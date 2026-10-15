package com.codm.gamehost.offline;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String GAME = "com.activision.callofduty.shooter";
    private static final int REQUEST = 2001;

    private TextView state, log;
    private Button apply, verify, permission, launch;
    private volatile ICommandService shell;
    private boolean bound = false;

    private final Shizuku.OnBinderReceivedListener onReceived = this::connectIfAllowed;
    private final Shizuku.OnBinderDeadListener onDead = () -> {
        shell = null;
        runOnUiThread(() -> updateState("Shizuku disconnected"));
    };
    private final Shizuku.OnRequestPermissionResultListener onPermission = (code, result) -> {
        if (code == REQUEST) {
            if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed();
            else updateState("Shizuku permission denied");
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            shell = ICommandService.Stub.asInterface(binder);
            updateState("Shizuku connected · shell service ready");
            append("Ready. No root, VIP, account, network or ads required.");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null;
            updateState("Shell service disconnected");
        }
    };

    private final String applyScript =
        "PKG='" + GAME + "'\n" +
        "echo '=== CODM OFFLINE PROFILE ==='\n" +
        "id\n" +
        "echo '[1] Display 90 Hz'\n" +
        "settings put system peak_refresh_rate 90.0\n" +
        "settings put system min_refresh_rate 90.0\n" +
        "settings put system user_refresh_rate 90.0\n" +
        "echo '[2] GameManager Performance / 90 fps / 0.7'\n" +
        "cmd game set --mode 2 --downscale 0.7 --fps 90 \"$PKG\"\n" +
        "cmd game mode performance \"$PKG\"\n" +
        "echo '[3] Keep game eligible for foreground execution'\n" +
        "cmd appops set \"$PKG\" RUN_IN_BACKGROUND allow\n" +
        "cmd appops set \"$PKG\" RUN_ANY_IN_BACKGROUND allow\n" +
        "cmd appops set \"$PKG\" WAKE_LOCK allow\n" +
        "am set-inactive \"$PKG\" false\n" +
        "am set-standby-bucket \"$PKG\" active\n" +
        "cmd deviceidle whitelist +\"$PKG\"\n" +
        "echo '[4] Safe quality flags'\n" +
        "setprop debug.egl.force_rgba_4444 0\n" +
        "setprop debug.egl.force_msaa 0\n" +
        "setprop debug.graphics.max_texture_size 0\n" +
        "echo '[5] Power saver'\n" +
        "settings put global low_power 0\n" +
        "cmd power set-adaptive-power-saver-enabled false\n" +
        "echo '[6] Verify'\n" +
        "cmd game mode \"$PKG\"\n" +
        "cmd game list-configs \"$PKG\"\n" +
        "settings get system peak_refresh_rate\n" +
        "device_config get game_overlay \"$PKG\"\n" +
        "echo 'Profile command sequence complete; actual FPS must be measured in game.'\n";

    private final String statusScript =
        "PKG='" + GAME + "'\n" +
        "echo '=== CODM PROFILE STATUS ==='\n" +
        "id\n" +
        "echo 'Game mode:'\ncmd game mode \"$PKG\"\n" +
        "echo 'Game configs:'\ncmd game list-configs \"$PKG\"\n" +
        "echo 'Device config:'\ndevice_config get game_overlay \"$PKG\"\n" +
        "echo 'Refresh rate:'\n" +
        "settings get system peak_refresh_rate\n" +
        "settings get system min_refresh_rate\n" +
        "settings get system user_refresh_rate\n" +
        "echo 'Game process:'\npidof \"$PKG\" || true\n";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 30, 28, 20);
        root.setBackgroundColor(Color.rgb(12, 18, 29));

        TextView heading = new TextView(this);
        heading.setText("CODM OFFLINE 90");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        root.addView(heading);

        state = new TextView(this);
        state.setTextColor(Color.CYAN);
        state.setPadding(0, 14, 0, 14);
        state.setText("Shizuku: connecting...");
        root.addView(state);

        permission = button("1 · Подключить Shizuku", root);
        apply = button("2 · Применить 90 FPS / 0.7 / Performance", root);
        verify = button("3 · Проверить настройки", root);
        launch = button("4 · Запустить CODM", root);

        TextView note = new TextView(this);
        note.setText("Независимое приложение без VIP, рекламы и сервера.\n" +
          "GameManager override не гарантирует реальные 90 FPS.");
        note.setTextColor(Color.LTGRAY);
        note.setPadding(0, 14, 0, 12);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Color.rgb(175, 232, 184));
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        permission.setOnClickListener(v -> requestAccess());
        apply.setOnClickListener(v -> runCommand(applyScript));
        verify.setOnClickListener(v -> runCommand(statusScript));
        launch.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(GAME);
            if (intent != null) startActivity(intent);
            else append("CODM is not installed or the launcher is unavailable.");
        });

        updateState("Waiting for Shizuku");
        Shizuku.addBinderReceivedListener(onReceived);
        Shizuku.addBinderDeadListener(onDead);
        Shizuku.addRequestPermissionResultListener(onPermission);
        try { if (Shizuku.pingBinder()) connectIfAllowed(); }
        catch (Throwable ignored) { updateState("Start Shizuku, then tap connect."); }
    }

    private Button button(String title, LinearLayout root) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return b;
    }

    private void updateState(String value) {
        runOnUiThread(() -> {
            state.setText(value);
            boolean active = shell != null;
            apply.setEnabled(active);
            verify.setEnabled(active);
        });
    }

    private void append(String value) {
        runOnUiThread(() -> log.append(value + "\n"));
    }

    private void requestAccess() {
        if (!Shizuku.pingBinder()) {
            append("Shizuku is not running. Launch Shizuku first.");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST);
        } else {
            connectIfAllowed();
        }
    }

    private void connectIfAllowed() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                updateState("Grant Shizuku permission");
                return;
            }
            if (bound) return;
            bound = true;
            Shizuku.UserServiceArgs args =
                new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), ShellService.class.getName()))
                    .daemon(false).processNameSuffix("service").version(1);
            Shizuku.bindUserService(args, connection);
            updateState("Connecting to shell service...");
        } catch (Throwable t) {
            bound = false;
            updateState("Shizuku binding failed: " + t.getMessage());
        }
    }

    private void runCommand(String script) {
        ICommandService connected = shell;
        if (connected == null) { append("Connect Shizuku first."); return; }
        apply.setEnabled(false);
        verify.setEnabled(false);
        append("\n>>> Executing...\n");
        new Thread(() -> {
            String answer;
            try { answer = connected.execute(script); }
            catch (RemoteException e) { answer = "Remote error: " + e; }
            final String out = answer;
            runOnUiThread(() -> {
                append(out);
                if (shell != null) {
                    apply.setEnabled(true);
                    verify.setEnabled(true);
                }
            });
        }, "CODM-Shell").start();
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(onReceived);
        Shizuku.removeBinderDeadListener(onDead);
        Shizuku.removeRequestPermissionResultListener(onPermission);
        super.onDestroy();
    }
}
