package com.codm.gamehost.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val REQUEST_CODE = 9101

    @Volatile private var service: IShellService? = null
    @Volatile private var binding = false
    @Volatile private var initialized = false
    @Volatile private var attempts = 0

    @Volatile var lastEvent: String = "INIT"
        private set
    @Volatile var lastError: String = ""
        private set

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.codm.gamehost", JavaShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(true)
            .version(7)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { IShellService.Stub.asInterface(it) }
            binding = false
            lastEvent = "USER_SERVICE_CONNECTED"
            lastError = ""
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            lastEvent = "USER_SERVICE_DISCONNECTED"
        }
    }

    val binderAvailable: Boolean
        get() = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    val permissionGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

    val serverApi: Int
        get() = try { Shizuku.getVersion() } catch (_: Throwable) { -1 }

    private fun server(): IShizukuService? {
        val binder = try { Shizuku.getBinder() } catch (_: Throwable) { null } ?: return null
        return try { IShizukuService.Stub.asInterface(binder) } catch (_: Throwable) { null }
    }

    val remoteProcessAvailable: Boolean
        get() = binderAvailable && permissionGranted && serverApi in 11..13 && server() != null

    fun canExecute(): Boolean =
        binderAvailable && permissionGranted && (remoteProcessAvailable || service != null)

    fun transportName(): String = when {
        remoteProcessAvailable -> "REMOTE_PROCESS_AIDL"
        service != null -> "USER_SERVICE"
        else -> "NONE"
    }

    fun init() {
        if (initialized) return
        initialized = true

        Shizuku.addBinderReceivedListenerSticky {
            lastError = ""
            if (remoteProcessAvailable) {
                lastEvent = "REMOTE_PROCESS_READY"
            } else if (permissionGranted) {
                lastEvent = "BINDER_RECEIVED"
                connectUserService()
            }
        }

        Shizuku.addBinderDeadListener {
            service = null
            binding = false
            lastEvent = "BINDER_DEAD"
            lastError = "Shizuku binder died"
        }

        Shizuku.addRequestPermissionResultListener { code, grant ->
            if (code != REQUEST_CODE) return@addRequestPermissionResultListener

            if (grant == PackageManager.PERMISSION_GRANTED) {
                lastError = ""
                if (remoteProcessAvailable) {
                    lastEvent = "REMOTE_PROCESS_READY"
                } else {
                    lastEvent = "PERMISSION_GRANTED"
                    connectUserService()
                }
            } else {
                lastEvent = "PERMISSION_DENIED"
                lastError = "Shizuku permission denied: " + grant
            }
        }

        if (binderAvailable && permissionGranted) {
            if (remoteProcessAvailable) {
                lastEvent = "REMOTE_PROCESS_READY"
            } else {
                lastEvent = "BINDER_READY"
                connectUserService()
            }
        }
    }

    fun requestPermission() {
        if (!binderAvailable) {
            lastEvent = "NO_BINDER"
            lastError = "Shizuku binder unavailable"
            return
        }

        if (!permissionGranted) {
            lastEvent = "REQUEST_PERMISSION"
            Shizuku.requestPermission(REQUEST_CODE)
            return
        }

        if (remoteProcessAvailable) {
            lastEvent = "REMOTE_PROCESS_READY"
            lastError = ""
        } else {
            connectUserService()
        }
    }

    fun connectUserService() {
        if (!binderAvailable || !permissionGranted || remoteProcessAvailable) return
        if (service != null || binding) return

        attempts += 1
        binding = true
        lastEvent = "USER_SERVICE_BIND_ATTEMPT:" + attempts
        lastError = ""

        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            binding = false
            lastEvent = "USER_SERVICE_BIND_EXCEPTION"
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "bind failed")
        }
    }

    fun forceReconnect() {
        service = null
        binding = false
        lastError = ""
        lastEvent = "RECHECK_TRANSPORT"

        if (remoteProcessAvailable) {
            lastEvent = "REMOTE_PROCESS_READY"
            return
        }

        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (_: Throwable) {
        }
        connectUserService()
    }

    fun diagnostics(): String {
        val uid = try { Shizuku.getUid().toString() } catch (_: Throwable) { "?" }
        val err = if (lastError.isBlank()) "-" else lastError
        return "SHIZUKU binder=" + binderAvailable +
            " permission=" + permissionGranted +
            " ready=" + canExecute() + "\n" +
            "transport=" + transportName() +
            " service=" + (service != null) +
            " remoteProcess=" + remoteProcessAvailable + "\n" +
            "serverUid=" + uid +
            " api=" + serverApi +
            " attempts=" + attempts +
            " event=" + lastEvent + "\n" +
            "error=" + err
    }

    fun exec(command: String): String? {
        if (!binderAvailable || !permissionGranted) {
            lastEvent = "EXEC_BLOCKED"
            lastError = "Shizuku binder/permission unavailable"
            return null
        }

        if (remoteProcessAvailable) {
            return execRemoteProcess(command)
        }

        val s = service
        if (s != null) {
            return try {
                val result = s.exec(command)
                lastEvent = "EXEC_USER_SERVICE_OK"
                lastError = ""
                "TRANSPORT=USER_SERVICE\n" + result
            } catch (t: Throwable) {
                service = null
                binding = false
                lastEvent = "EXEC_USER_SERVICE_FAILED"
                lastError = t.javaClass.simpleName + ": " + (t.message ?: "failed")
                null
            }
        }

        connectUserService()
        lastEvent = "EXEC_NO_TRANSPORT"
        lastError = "No executable Shizuku transport"
        return null
    }

    private fun execRemoteProcess(command: String): String {
        val shizukuService = server()
            ?: return failRemote("Shizuku service binder unavailable")

        return try {
            val remote = shizukuService.newProcess(
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            )

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutThread = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                        .bufferedReader()
                        .useLines { lines ->
                            lines.forEach { stdout.append(it).append('\n') }
                        }
                } catch (t: Throwable) {
                    stderr.append("[stdout-reader] ")
                        .append(t.javaClass.simpleName)
                        .append(": ")
                        .append(t.message ?: "failed")
                        .append('\n')
                }
            }

            val stderrThread = Thread {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                        .bufferedReader()
                        .useLines { lines ->
                            lines.forEach { stderr.append(it).append('\n') }
                        }
                } catch (t: Throwable) {
                    stderr.append("[stderr-reader] ")
                        .append(t.javaClass.simpleName)
                        .append(": ")
                        .append(t.message ?: "failed")
                        .append('\n')
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val rc = remote.waitFor()

            stdoutThread.join()
            stderrThread.join()

            lastEvent = "EXEC_REMOTE_PROCESS_OK"
            lastError = ""

            buildString {
                append("TRANSPORT=REMOTE_PROCESS_AIDL\n")
                append("RC=").append(rc).append('\n')
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    append("\n--- STDERR ---\n")
                    append(stderr)
                }
            }
        } catch (t: Throwable) {
            failRemote(t.javaClass.simpleName + ": " + (t.message ?: "remote process failed"))
        }
    }

    private fun failRemote(message: String): String {
        lastEvent = "EXEC_REMOTE_PROCESS_FAILED"
        lastError = message
        return "TRANSPORT=REMOTE_PROCESS_AIDL\nRC=255\n" + message
    }
}
