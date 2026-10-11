package com.codm.gamehost.shizuku

import android.os.RemoteException
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellService : IShellService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    @Throws(RemoteException::class)
    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            val rc = p.waitFor()
            "RC=" + rc + "\n" + out
        } catch (t: Throwable) {
            "RC=255\n" + t.javaClass.simpleName + ": " + (t.message ?: "unknown")
        }
    }
}
