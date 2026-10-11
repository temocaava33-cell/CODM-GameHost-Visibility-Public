package com.codm.gamehost.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val REQUEST_CODE = 9101
    @Volatile private var service: IShellService? = null
    @Volatile private var binding = false

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.codm.gamehost", ShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("gamehost_shell")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null) IShellService.Stub.asInterface(binder) else null
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
        Shizuku.addBinderReceivedListenerSticky {
            if (permissionGranted) connect()
        }
        Shizuku.addBinderDeadListener {
            service = null
            binding = false
        }
        Shizuku.addRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE && grant == PackageManager.PERMISSION_GRANTED) {
                connect()
            }
        }
        if (binderAvailable && permissionGranted) connect()
    }

    fun requestPermission() {
        if (!binderAvailable) return
        if (!permissionGranted) Shizuku.requestPermission(REQUEST_CODE) else connect()
    }

    fun connect() {
        if (!permissionGranted || service != null || binding) return
        binding = true
        try {
            Shizuku.bindUserService(args, connection)
        } catch (_: Throwable) {
            binding = false
        }
    }

    fun isReady(): Boolean = binderAvailable && permissionGranted && service != null

    fun exec(command: String): String? {
        val s = service
        if (s == null) {
            connect()
            return null
        }
        return try {
            s.exec(command)
        } catch (_: Throwable) {
            service = null
            binding = false
            null
        }
    }
}
