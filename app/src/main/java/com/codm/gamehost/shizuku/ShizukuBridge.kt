package com.codm.gamehost.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val REQUEST_CODE = 9101
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var service: IShellService? = null
    @Volatile private var binding = false
    @Volatile private var initialized = false
    @Volatile private var attempts = 0
    @Volatile var lastEvent: String = "INIT"
        private set
    @Volatile var lastError: String = ""
        private set

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.codm.gamehost", JavaShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(true)
            .version(5)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            lastEvent = "CONNECTED:" + (name?.className ?: "?")
            lastError = ""
            service = binder?.let { IShellService.Stub.asInterface(it) }
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lastEvent = "DISCONNECTED:" + (name?.className ?: "?")
            service = null
            binding = false
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

    fun init() {
        if (initialized) return
        initialized = true

        Shizuku.addBinderReceivedListenerSticky {
            lastEvent = "BINDER_RECEIVED"
            if (permissionGranted) connect()
        }

        Shizuku.addBinderDeadListener {
            lastEvent = "BINDER_DEAD"
            lastError = "Shizuku binder died"
            service = null
            binding = false
        }

        Shizuku.addRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE) {
                lastEvent = "PERMISSION_RESULT:" + grant
                if (grant == PackageManager.PERMISSION_GRANTED) connect()
                else lastError = "Shizuku permission denied: " + grant
            }
        }

        if (binderAvailable && permissionGranted) connect()
    }

    fun requestPermission() {
        if (!binderAvailable) {
            lastError = "Shizuku binder unavailable"
            return
        }
        if (!permissionGranted) {
            Shizuku.requestPermission(REQUEST_CODE)
        } else {
            connect()
        }
    }

    fun connect() {
        if (!binderAvailable) {
            lastError = "binder=false"
            return
        }
        if (!permissionGranted) {
            lastError = "permission=false"
            return
        }
        if (service != null || binding) return

        binding = true
        attempts += 1
        lastEvent = "BIND_ATTEMPT:" + attempts
        lastError = ""

        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            binding = false
            lastEvent = "BIND_EXCEPTION"
            lastError = t.javaClass.simpleName + ": " + (t.message ?: "bind failed")
            return
        }

        mainHandler.postDelayed({
            if (service == null && binding) {
                binding = false
                lastEvent = "BIND_TIMEOUT"
                lastError = "No onServiceConnected callback after 10s"
            }
        }, 10000)
    }

    fun forceReconnect() {
        service = null
        binding = false
        lastEvent = "FORCE_RECONNECT"
        lastError = ""
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Throwable) {
        }
        mainHandler.postDelayed({ connect() }, 700)
    }

    fun isReady(): Boolean = binderAvailable && permissionGranted && service != null

    fun diagnostics(): String {
        val uid = try { Shizuku.getUid().toString() } catch (_: Throwable) { "?" }
        val api = try { Shizuku.getVersion().toString() } catch (_: Throwable) { "?" }
        val err = if (lastError.isBlank()) "-" else lastError
        return "SHIZUKU binder=" + binderAvailable +
            " permission=" + permissionGranted +
            " service=" + (service != null) +
            " binding=" + binding + "\n" +
            "serverUid=" + uid + " api=" + api +
            " attempts=" + attempts + " event=" + lastEvent + "\n" +
            "error=" + err
    }

    fun exec(command: String): String? {
        val s = service ?: run {
            lastError = "exec requested while service=null"
            connect()
            return null
        }
        return try {
            s.exec(command)
        } catch (t: Throwable) {
            lastError = "exec: " + t.javaClass.simpleName + ": " + (t.message ?: "failed")
            lastEvent = "EXEC_EXCEPTION"
            service = null
            binding = false
            null
        }
    }
}
