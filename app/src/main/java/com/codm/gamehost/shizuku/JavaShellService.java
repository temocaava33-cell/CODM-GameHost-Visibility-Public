package com.codm.gamehost.shizuku;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class JavaShellService extends IShellService.Stub {
    private static final String TAG = "CODMGameHostShell";

    public JavaShellService() {
        Log.i(TAG, "public no-arg constructor uid=" + android.os.Process.myUid());
    }

    public JavaShellService(Context context) {
        Log.i(TAG, "public Context constructor uid=" + android.os.Process.myUid() + " context=" + context);
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy");
        System.exit(0);
    }

    @Override
    public String exec(String command) throws RemoteException {
        try {
            Process p = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
            int rc = p.waitFor();
            return "RC=" + rc + "\n" + out;
        } catch (Throwable t) {
            return "RC=255\n" + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        }
    }
}
