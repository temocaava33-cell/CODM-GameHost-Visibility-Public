package com.codm.gamehost.offline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String GAME = "com.activision.callofduty.shooter";
    private static final int PERMISSION_REQUEST = 4319;
    private static final int BG = Color.rgb(7, 12, 24);
    private static final int PANEL = Color.rgb(16, 25, 42);
    private static final int TEXT = Color.rgb(240, 246, 255);
    private static final int MUTED = Color.rgb(156, 177, 205);
    private static final int CYAN = Color.rgb(45, 215, 240);
    private static final int GREEN = Color.rgb(115, 238, 180);
    private static final int AMBER = Color.rgb(255, 194, 94);
    private static final int BORDER = Color.rgb(37, 58, 82);

    private volatile ICommandService shell;
    private boolean binding;
    private boolean busy;
    private boolean showLog;
    private boolean activeGamePresent = true;
    private int fps = 90;
    private String scale = "0.7";

    private TextView connectionValue, connectionHint, statusTitle, statusValue, logView, activityTag;
    private Button connectButton, applyButton, verifyButton, launchButton, fps60, fps90, scale70, scale80, scale100, logToggle;
    private Switch hzToggle, backgroundToggle, qualityToggle;
    private ProgressBar spinner;

    private final Shizuku.OnBinderReceivedListener received = this::connectIfAllowed;
    private final Shizuku.OnBinderDeadListener died = () -> {
        shell = null;
        binding = false;
        runOnUiThread(() -> {
            updateConnection("SHIZUKU OFFLINE", "Служба остановлена. Запусти Shizuku.", AMBER);
            status("ОЖИДАНИЕ", "Подключение к Shizuku потеряно.", AMBER);
            refreshButtons();
        });
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (request, result) -> {
        if (request != PERMISSION_REQUEST) return;
        if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed();
        else runOnUiThread(() -> updateConnection("НЕТ ДОСТУПА", "Доступ Shizuku отклонён. Разреши в Shizuku.", AMBER));
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            shell = ICommandService.Stub.asInterface(service);
            binding = false;
            runOnUiThread(() -> {
                updateConnection("SHELL 2000 · CONNECTED", "Команды исполняются через Shizuku UserService.", GREEN);
                appendLog("Shizuku UserService connected");
                refreshButtons();
                runScript("ПЕРВИЧНАЯ ПРОВЕРКА", buildStatus(), false);
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null;
            binding = false;
            runOnUiThread(() -> {
                updateConnection("ОТКЛЮЧЕНО", "UserService отключился.", AMBER);
                refreshButtons();
            });
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        render();
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(died);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        try {
            if (!Shizuku.pingBinder()) {
                updateConnection("SHIZUKU OFFLINE", "Запусти Shizuku и нажми «Подключить».", AMBER);
            } else {
                connectIfAllowed();
            }
        } catch (Throwable problem) {
            updateConnection("SHIZUKU OFFLINE", "Запусти Shizuku и нажми «Подключить».", AMBER);
        }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        scroll.addView(root);

        LinearLayout top = row();
        TextView logo = text("◈  N O V A  /  C O D M", 12, CYAN, true);
        top.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        TextView offline = text("●  OFFLINE  /  V2", 10, GREEN, true);
        offline.setPadding(dp(10), dp(8), dp(10), dp(8));
        offline.setBackground(solid(Color.rgb(12, 41, 43), 12, Color.rgb(32, 99, 90)));
        top.addView(offline);
        root.addView(top);
        space(root, 20);

        LinearLayout hero = column();
        hero.setPadding(dp(22), dp(22), dp(22), dp(24));
        hero.setBackground(gradient("#102D49", "#111629", 22, "#275C78"));
        TextView eyebrow = text("MOBILE PERFORMANCE CONTROL", 11, CYAN, true);
        hero.addView(eyebrow);
        space(hero, 10);
        hero.addView(text("CODM  /  90", 34, TEXT, true));
        space(hero, 6);
        hero.addView(text("Независимый профиль GameManager\nБез рекламы, VIP и аккаунтов.", 13, MUTED, false));
        space(hero, 18);
        LinearLayout metrics = row();
        metrics.addView(metric("90", "FPS TARGET"), new LinearLayout.LayoutParams(0, -2, 1));
        spaceHorizontal(metrics, 7);
        metrics.addView(metric("0.70", "DOWNSCALE"), new LinearLayout.LayoutParams(0, -2, 1));
        spaceHorizontal(metrics, 7);
        metrics.addView(metric("P2", "PERFORMANCE"), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(metrics);
        root.addView(hero);
        space(root, 14);

        LinearLayout shizuku = panel();
        shizuku.addView(label("01  ·  ДОСТУП К СИСТЕМЕ"));
        space(shizuku, 10);
        connectionValue = text("ПРОВЕРКА SHIZUKU", 16, AMBER, true);
        shizuku.addView(connectionValue);
        space(shizuku, 5);
        connectionHint = text("Ожидание системного Binder...", 12, MUTED, false);
        shizuku.addView(connectionHint);
        space(shizuku, 12);
        connectButton = button("ПОДКЛЮЧИТЬ SHIZUKU", false);
        connectButton.setOnClickListener(v -> requestAccess());
        shizuku.addView(connectButton);
        root.addView(shizuku);
        space(root, 14);

        LinearLayout game = panel();
        game.addView(label("02  ·  КОНФИГУРАЦИЯ CODM"));
        space(game, 14);
        game.addView(text("Лимит кадров GameManager", 15, TEXT, true));
        space(game, 8);
        LinearLayout fpsRow = row();
        fps60 = option("60 FPS");
        fps90 = option("90 FPS");
        fpsRow.addView(fps60, new LinearLayout.LayoutParams(0, dp(44), 1));
        spaceHorizontal(fpsRow, 8);
        fpsRow.addView(fps90, new LinearLayout.LayoutParams(0, dp(44), 1));
        game.addView(fpsRow);
        fps60.setOnClickListener(v -> { fps=60; refreshChoice(); });
        fps90.setOnClickListener(v -> { fps=90; refreshChoice(); });
        space(game, 16);
        game.addView(text("Масштаб графики", 15, TEXT, true));
        space(game, 3);
        game.addView(text("0.70 = 70% внутреннего разрешения. Не FPS-замер.", 11, MUTED, false));
        space(game, 8);
        LinearLayout scaleRow = row();
        scale70 = option("0.70");
        scale80 = option("0.80");
        scale100 = option("1.00");
        scaleRow.addView(scale70, new LinearLayout.LayoutParams(0, dp(44), 1));
        spaceHorizontal(scaleRow, 7);
        scaleRow.addView(scale80, new LinearLayout.LayoutParams(0, dp(44), 1));
        spaceHorizontal(scaleRow, 7);
        scaleRow.addView(scale100, new LinearLayout.LayoutParams(0, dp(44), 1));
        game.addView(scaleRow);
        scale70.setOnClickListener(v -> { scale="0.7"; refreshChoice(); });
        scale80.setOnClickListener(v -> { scale="0.8"; refreshChoice(); });
        scale100.setOnClickListener(v -> { scale="1.0"; refreshChoice(); });
        space(game, 16);
        TextView perf = text("◆  GAME MODE  /  PERFORMANCE (2)", 12, CYAN, true);
        perf.setPadding(dp(11), dp(12), dp(11), dp(12));
        perf.setBackground(solid(Color.rgb(14, 42, 58), 12, Color.rgb(40, 94, 115)));
        game.addView(perf);
        root.addView(game);
        space(root, 14);

        LinearLayout advanced = panel();
        advanced.addView(label("03  ·  ДОПОЛНИТЕЛЬНЫЕ НАСТРОЙКИ"));
        space(advanced, 10);
        hzToggle = setting(advanced, "Экран 90 Hz", "Системный запрос 90 Гц (если дисплей поддерживает)", true);
        backgroundToggle = setting(advanced, "Background / Doze", "AppOps + разрешение фоновой работы CODM", true);
        qualityToggle = setting(advanced, "Безопасные EGL-флаги", "Не форсировать MSAA или RGBA4444", true);
        root.addView(advanced);
        space(root, 14);

        LinearLayout actions = panel();
        actions.addView(label("04  ·  ЗАПУСК И ПРОВЕРКА"));
        space(actions, 12);
        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        actions.addView(spinner, new LinearLayout.LayoutParams(-1, dp(26)));
        applyButton = button("⚡  ПРИМЕНИТЬ ПРОФИЛЬ", true);
        applyButton.setOnClickListener(v -> runScript("ПРИМЕНЕНИЕ", buildApply(), true));
        actions.addView(applyButton);
        space(actions, 8);
        verifyButton = button("◎  ПРОВЕРИТЬ СИСТЕМУ", false);
        verifyButton.setOnClickListener(v -> runScript("ДИАГНОСТИКА", buildStatus(), false));
        actions.addView(verifyButton);
        space(actions, 8);
        launchButton = button("▶  ЗАПУСТИТЬ CALL OF DUTY", false);
        launchButton.setOnClickListener(v -> launchCodm());
        actions.addView(launchButton);
        space(actions, 14);
        statusTitle = text("НЕ ПРОВЕРЕНО", 14, AMBER, true);
        statusValue = text("Применение настроек ещё не подтверждено.", 12, MUTED, false);
        actions.addView(statusTitle);
        space(actions, 3);
        actions.addView(statusValue);
        root.addView(actions);
        space(root, 14);

        LinearLayout logs = panel();
        logs.addView(label("05  ·  СИСТЕМНЫЙ ЖУРНАЛ"));
        space(logs, 10);
        logToggle = button("ПОКАЗАТЬ ДЕТАЛИ  ▾", false);
        logToggle.setOnClickListener(v -> {
            showLog = !showLog;
            logView.setVisibility(showLog ? View.VISIBLE : View.GONE);
            logToggle.setText(showLog ? "СКРЫТЬ ДЕТАЛИ  ▴" : "ПОКАЗАТЬ ДЕТАЛИ  ▾");
        });
        logs.addView(logToggle);
        space(logs, 9);
        logView = text("", 11, GREEN, false);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setBackground(solid(Color.rgb(4, 10, 20), 12, BORDER));
        logView.setVisibility(View.GONE);
        logs.addView(logView);
        root.addView(logs);

        space(root, 16);
        activityTag = text("ENGINE / JAVA + SHIZUKU · LOCAL ONLY\nЦелевые 90 FPS ≠ измеренные 90 FPS.", 10, MUTED, false);
        activityTag.setGravity(Gravity.CENTER);
        root.addView(activityTag);
        refreshChoice();
        setContentView(scroll);
        refreshButtons();
    }

    private void updateConnection(String title, String detail, int color) {
        runOnUiThread(() -> {
            connectionValue.setText("●  " + title);
            connectionValue.setTextColor(color);
            connectionHint.setText(detail);
            refreshButtons();
        });
    }

    private void status(String title, String detail, int color) {
        statusTitle.setText(title);
        statusTitle.setTextColor(color);
        statusValue.setText(detail);
    }

    private void refreshChoice() {
        setSelected(fps60, fps==60);
        setSelected(fps90, fps==90);
        setSelected(scale70, scale.equals("0.7"));
        setSelected(scale80, scale.equals("0.8"));
        setSelected(scale100, scale.equals("1.0"));
    }

    private void setSelected(Button view, boolean selected) {
        view.setTextColor(selected ? Color.rgb(4, 17, 30) : MUTED);
        view.setBackground(selected
                ? gradient("#40E6F4", "#18B4F1", 11, "#79EDFB")
                : solid(Color.rgb(21, 32, 50), 11, BORDER));
    }

    private void refreshButtons() {
        boolean ready = shell != null && !busy;
        if (applyButton == null) return;
        applyButton.setEnabled(ready);
        verifyButton.setEnabled(ready);
        connectButton.setEnabled(!busy);
        launchButton.setEnabled(!busy);
    }

    private void connectIfAllowed() {
        try {
            if (!Shizuku.pingBinder()) {
                updateConnection("SHIZUKU OFFLINE", "Запусти службу Shizuku.", AMBER);
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                updateConnection("ТРЕБУЕТСЯ ДОСТУП", "Одно разрешение Shizuku — это нормально.", AMBER);
                return;
            }
            if (shell != null || binding) return;
            binding = true;
            updateConnection("ПОДКЛЮЧЕНИЕ…", "Ожидание UserService с UID 2000.", CYAN);
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), ShellService.class.getName()))
                    .daemon(false).processNameSuffix("engine").version(2);
            Shizuku.bindUserService(args, connection);
        } catch (Throwable failure) {
            binding = false;
            updateConnection("ОШИБКА ПОДКЛЮЧЕНИЯ", failure.getMessage(), AMBER);
        }
    }

    private void requestAccess() {
        try {
            if (!Shizuku.pingBinder()) {
                updateConnection("SHIZUKU OFFLINE", "Открой Shizuku, запусти службу и вернись.", AMBER);
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST);
            } else {
                connectIfAllowed();
            }
        } catch (Throwable failure) {
            updateConnection("ОШИБКА SHIZUKU", failure.toString(), AMBER);
        }
    }

    private String buildApply() {
        String down = scale.equals("1.0") ? "disable" : scale;
        StringBuilder sb = new StringBuilder();
        sb.append("PKG='").append(GAME).append("'\n");
        sb.append("fail=0\n");
        sb.append("run(){ label=\"$1\"; shift; echo \">>> $label\"; \"$@\" 2>&1; rc=$?; ");
        sb.append("if [ \"$rc\" -eq 0 ]; then echo \"OK:$label\"; else echo \"FAIL:$label:$rc\"; fail=$((fail+1)); fi; }\n");
        sb.append("echo '=== CODM 90 ENGINE / APPLY ==='\n");
        sb.append("id\n");
        sb.append("if ! pm path \"$PKG\" >/dev/null 2>&1; then echo 'FAIL:CODM_NOT_INSTALLED'; exit 2; fi\n");
        if (hzToggle.isChecked()) {
            sb.append("run 'DISPLAY_PEAK' settings put system peak_refresh_rate 90.0\n");
            sb.append("run 'DISPLAY_MIN' settings put system min_refresh_rate 90.0\n");
            sb.append("run 'DISPLAY_USER' settings put system user_refresh_rate 90.0\n");
        }
        sb.append("run 'FPS_SCALE' cmd game set --mode 2 --downscale ").append(down)
          .append(" --fps ").append(fps).append(" \"$PKG\"\n");
        sb.append("run 'GAME_PERFORMANCE' cmd game mode performance \"$PKG\"\n");
        if (backgroundToggle.isChecked()) {
            sb.append("run 'BACKGROUND' cmd appops set \"$PKG\" RUN_IN_BACKGROUND allow\n");
            sb.append("run 'ANY_BACKGROUND' cmd appops set \"$PKG\" RUN_ANY_IN_BACKGROUND allow\n");
            sb.append("run 'WAKE_LOCK' cmd appops set \"$PKG\" WAKE_LOCK allow\n");
            sb.append("run 'ACTIVE_APP' am set-inactive \"$PKG\" false\n");
            sb.append("run 'STANDBY' am set-standby-bucket \"$PKG\" active\n");
            sb.append("run 'DOZE_EXEMPTION' cmd deviceidle whitelist \"+$PKG\"\n");
        }
        if (qualityToggle.isChecked()) {
            sb.append("run 'RGBA4444_OFF' setprop debug.egl.force_rgba_4444 0\n");
            sb.append("run 'MSAA_OFF' setprop debug.egl.force_msaa 0\n");
            sb.append("run 'NO_TEXTURE_LIMIT' setprop debug.graphics.max_texture_size 0\n");
        }
        sb.append("echo '=== VERIFICATION ==='\n");
        sb.append("cmd game mode \"$PKG\" 2>&1\n");
        sb.append("cmd game list-configs \"$PKG\" 2>&1\n");
        sb.append("echo \"DISPLAY_PEAK=$(settings get system peak_refresh_rate 2>&1)\"\n");
        sb.append("echo \"FAILED_COMMANDS=$fail\"\n");
        sb.append("exit 0\n");
        return sb.toString();
    }

    private String buildStatus() {
        return "PKG='" + GAME + "'\n"
                + "echo '=== SYSTEM STATUS / READ ONLY ==='\n"
                + "id\n"
                + "echo '--- GAME MODE ---'\n"
                + "cmd game mode \"$PKG\" 2>&1\n"
                + "echo '--- GAME CONFIGS ---'\n"
                + "cmd game list-configs \"$PKG\" 2>&1\n"
                + "echo '--- GAME OVERLAY ---'\n"
                + "device_config get game_overlay \"$PKG\" 2>&1\n"
                + "echo '--- DISPLAY REFRESH ---'\n"
                + "settings get system peak_refresh_rate 2>&1\n"
                + "settings get system min_refresh_rate 2>&1\n"
                + "settings get system user_refresh_rate 2>&1\n"
                + "echo '--- CODM PROCESS ---'\n"
                + "pidof \"$PKG\" 2>&1 || true\n";
    }

    private void runScript(String label, String commands, boolean applying) {
        ICommandService current = shell;
        if (current == null || busy) {
            updateConnection("НЕ ПОДКЛЮЧЕНО", "Сначала разреши Shizuku.", AMBER);
            return;
        }
        busy = true;
        spinner.setVisibility(View.VISIBLE);
        status("ВЫПОЛНЯЕТСЯ", label, CYAN);
        refreshButtons();
        appendLog("\n► " + label + "\n");
        new Thread(() -> {
            String output;
            try {
                output = current.execute(commands);
            } catch (RemoteException e) {
                output = "FAIL:REMOTE_EXCEPTION " + e;
            } catch (Throwable t) {
                output = "FAIL:UNEXPECTED " + t;
            }
            final String finalOutput = output;
            runOnUiThread(() -> {
                appendLog(finalOutput);
                busy = false;
                spinner.setVisibility(View.GONE);
                if (finalOutput.contains("FAIL:") || finalOutput.contains("FAILED_COMMANDS=1")
                        || finalOutput.matches("(?s).*FAILED_COMMANDS=[2-9].*")) {
                    status("НУЖНА ПРОВЕРКА", "Не все команды поддерживаются прошивкой. Открой журнал.", AMBER);
                    showLog = true;
                    logView.setVisibility(View.VISIBLE);
                    logToggle.setText("СКРЫТЬ ДЕТАЛИ  ▴");
                } else if (applying) {
                    status("КОМАНДЫ ОТПРАВЛЕНЫ", "Проверь системный профиль и фактическую частоту в игре.", GREEN);
                } else {
                    status("ДИАГНОСТИКА ГОТОВА", "Подробный вывод в системном журнале.", GREEN);
                }
                refreshButtons();
            });
        }, "CODM-profile-worker").start();
    }

    private void launchCodm() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(GAME);
            if (intent == null) {
                status("ИГРА НЕ НАЙДЕНА", GAME + " недоступен.", AMBER);
                return;
            }
            startActivity(intent);
        } catch (Throwable t) {
            status("НЕ УДАЛОСЬ ЗАПУСТИТЬ", t.toString(), AMBER);
        }
    }

    private void appendLog(String content) {
        if (logView == null) return;
        String previous = logView.getText().toString();
        if (previous.length() > 38000) previous = previous.substring(previous.length()-28000);
        logView.setText(previous + content + "\n");
    }

    private LinearLayout panel() {
        LinearLayout view = column();
        view.setPadding(dp(17), dp(17), dp(17), dp(18));
        view.setBackground(solid(PANEL, 18, BORDER));
        return view;
    }

    private LinearLayout metric(String number, String caption) {
        LinearLayout item = column();
        item.setPadding(dp(9), dp(11), dp(6), dp(10));
        item.setBackground(solid(Color.rgb(8, 28, 46), 10, Color.rgb(41, 88, 110)));
        item.addView(text(number, 18, TEXT, true));
        TextView label = text(caption, 8, CYAN, true);
        label.setLetterSpacing(0.04f);
        item.addView(label);
        return item;
    }

    private Switch setting(LinearLayout parent, String name, String details, boolean enabled) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text(name, 14, TEXT, true));
        TextView sub = text(details, 11, MUTED, false);
        sub.setPadding(0, dp(3), dp(2), 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        row.addView(toggle);
        row.setPadding(dp(0), dp(9), dp(0), dp(9));
        parent.addView(row);
        return toggle;
    }

    private Button button(String caption, boolean primary) {
        Button b = new Button(this);
        b.setText(caption);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.rgb(4, 18, 30) : TEXT);
        b.setBackground(primary
                ? gradient("#54EAF6", "#22B1F6", 15, "#6DFFFF")
                : solid(Color.rgb(24, 42, 64), 15, Color.rgb(55, 93, 119)));
        b.setMinHeight(dp(52));
        b.setStateListAnimator(null);
        return b;
    }

    private Button option(String caption) {
        Button b = button(caption, false);
        b.setTextSize(13);
        b.setMinHeight(dp(42));
        return b;
    }

    private TextView label(String value) {
        TextView v = text(value, 11, CYAN, true);
        v.setLetterSpacing(0.07f);
        return v;
    }

    private TextView text(String value, int size, int color, boolean strong) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(true);
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void space(LinearLayout parent, int px) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(px)));
    }

    private void spaceHorizontal(LinearLayout parent, int px) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(dp(px), 1));
    }

    private int dp(int pixels) {
        return (int) (getResources().getDisplayMetrics().density * pixels + 0.5f);
    }

    private GradientDrawable solid(int color, int radius, int border) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), border);
        return g;
    }

    private GradientDrawable gradient(String start, String end, int radius, String stroke) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(start), Color.parseColor(end)});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.parseColor(stroke));
        return g;
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(died);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }
}
