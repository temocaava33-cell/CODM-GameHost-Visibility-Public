package com.codm.gamehost.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

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

    private var cachedNewProcess: Method? = null
    private var newProcessResolved = false

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.codm.gamehost", JavaShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(true)
            .version(6)
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

    private fun resolveNewProcess(): Method? {
        if (newProcessResolved) return cachedNewProcess
        synchronized(this) {
            if (newProcessResolved) return cachedNewProcess
            newProcessResolved = true
            cachedNewProcess = try {
                val arrayClass = arrayOf<String>().javaClass
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    arrayClass,
                    arrayClass,
                    String::class.java
                ).apply { isAccessible = true }
            } catch (t: Throwable) {
                lastError = "newProcess reflection: " + t.javaClass.simpleName + ": " + (t.message ?: "missing")
                null
            }
            return cachedNewProcess
        }
    }

    val remoteProcessAvailable: Boolean
        get() = binderAvailable && permissionGranted && serverApi in 11..13 && resolveNewProcess() != null

    fun canExecute(): Boolean = binderAvailable && permissionGranted && (remoteProcessAvailable || service != null)

    fun transportName(): String = when {
        remoteProcessAvailable -> "REMOTE_PROCESS"
        service != null -> "USER_SERVICE"
        else -> "NONE"
    }

    fun init() {
        if (initialized) return
        initialized = true

        Shizuku.addBinderReceivedListenerSticky {
            lastEvent = if (remoteProcessAvailable) "REMOTE_PROCESS_READY" else "BINDER_RECEIVED"
            lastError = ""
            if (!remoteProcessAvailable && permissionGranted) connectUserService()
        }

        Shizuku.addBinderDeadListener {
            service = null
            binding = false
            cachedNewProcess = null
            newProcessResolved = false
            lastEvent = "BINDER_DEAD"
            lastError = "Shizuku binder died"
        }

        Shizuku.addRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE) {
                if (grant == PackageManager.PERMISSION_GRANTED) {
                    lastEvent = if (remoteProcessAvailable) "REMOTE_PROCESS_READY" else "PERMISSION_GRANTED"
                    lastError = ""
                    if (!remoteProcessAvailable) connectUserService()
                } else {
                    lastEvent = "PERMISSION_DENIED"
                    lastError = "Shizuku permission denied: " + grant
                }
            }
        }

        if (binderAvailable && permissionGranted) {
            lastEvent = if (remoteProcessAvailable) "REMOTE_PROCESS_READY" else "BINDER_READY"
            if (!remoteProcessAvailable) connectUserService()
        }
    }

    fun requestPermission() {
        if (!binderAvailable) {
            lastError = "Shizuku binder unavailable"
            lastEvent = "NO_BINDER"
            return
        }
        if (!permissionGranted) {
            lastEvent = "REQUEST_PERMISSION"
            Shizuku.requestPermission(REQUEST_CODE)
        } else if (remoteProcessAvailable) {
            lastEvent = "REMOTE_PROCESS_READY"
            lastError = ""
        } else {
            connectUserService()
        }
    }

    fun connectUserService() {
        if (!binderAvailable || !permissionGranted || remoteProcessAvailable) return
        if (service != null || binding) return

        binding = true
        attempts += 1
        lastEvent = "USER_SERVICE_BIND_ATTEMPT:" + attempts
        lastError = ""

        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            binding = false
            lastEvent = "USER_SERVICE_BIND_EXCEPTION"
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "bind failed")
        }
    }

    fun forceReconnect() {
        service = null
        binding = false
        cachedNewProcess = null
        newProcessResolved = false
        lastError = ""
        lastEvent = "RECHECK_TRANSPORT"

        if (remoteProcessAvailable) {
            lastEvent = "REMOTE_PROCESS_READY"
            return
        }

        try {
            Shizuku.unbindUserService(args, connection, true)
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
            lastError = "Shizuku binder/permission unavailable"
            lastEvent = "EXEC_BLOCKED"
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
        lastError = "No executable Shizuku transport"
        lastEvent = "EXEC_NO_TRANSPORT"
        return null
    }

    private fun execRemoteProcess(command: String): String {
        val method = resolveNewProcess()
            ?: return "TRANSPORT=REMOTE_PROCESS\nRC=255\nnewProcess method unavailable"

        return try {
            val cmd = arrayOf("/system/bin/sh", "-c", command)
            val process = method.invoke(null, cmd, null, null) as Process

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { stdout.append(it).append('\n') }
                    }
                } catch (t: Throwable) {
                    stderr.append("[stdout-reader] ").append(t.message ?: t.javaClass.simpleName).append('\n')
                }
            }

            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { stderr.append(it).append('\n') }
                    }
                } catch (t: Throwable) {
                    stderr.append("[stderr-reader] ").append(t.message ?: t.javaClass.simpleName).append('\n')
                }
            }

            outThread.start()
            errThread.start()
            val rc = process.waitFor()
            outThread.join()
            errThread.join()

            lastEvent = "EXEC_REMOTE_PROCESS_OK"
            lastError = ""

            buildString {
                append("TRANSPORT=REMOTE_PROCESS\n")
                append("RC=").append(rc).append('\n')
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    append("\n--- STDERR ---\n")
                    append(stderr)
                }
            }
        } catch (t: Throwable) {
            val root = if (t is InvocationTargetException && t.targetException != null) {
                t.targetException
            } else {
                t
            }
            lastEvent = "EXEC_REMOTE_PROCESS_FAILED"
            lastError = root.javaClass.simpleName + ": " + (root.message ?: "failed")
            "TRANSPORT=REMOTE_PROCESS\nRC=255\n" + lastError
        }
    }
}
