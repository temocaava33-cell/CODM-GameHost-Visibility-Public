#!/system/bin/sh

PKG="com.activision.callofduty.shooter"
FPS="90"
SCALE="0.7"
BASE="/storage/emulated/0/GameBoost"
LOGDIR="$BASE/logs"
LOCK="$BASE/codm_master_service_90.lock"
LOG="$LOGDIR/codm_master_90_$(date +%Y%m%d_%H%M%S).txt"
WL="in.hridayan.ashell,moe.shizuku.privileged.api,com.activision.callofduty.shooter,com.android.shell,inc.trilokia.pubgfxtool,inc.trilokia.gfxtool"

mkdir -p "$LOGDIR"

say(){ echo "$1"; echo "$1" >> "$LOG"; }

cmd_run(){
    say "+ $*"
    OUT="$($@ 2>&1)"
    RC="$?"
    [ -n "$OUT" ] && { echo "$OUT"; echo "$OUT" >> "$LOG"; }
    case "$OUT" in
        *"Failed transaction"*) TXN_FAIL=$((TXN_FAIL+1)); sleep 3 ;;
        *"Permission Denial"*) DENY=$((DENY+1)); sleep 1 ;;
    esac
    sleep 1
    return "$RC"
}

lock_start(){
    if [ -f "$LOCK" ]; then
        OLD="$(cat "$LOCK" 2>/dev/null)"
        if [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; then
            echo "BUSY CODM_MASTER PID=$OLD"
            exit 9
        fi
        rm -f "$LOCK"
    fi
    echo "$$" > "$LOCK"
    trap 'rm -f "$LOCK"' EXIT INT TERM
}

TXN_FAIL=0
DENY=0
lock_start

say "=== CODM MASTER SERVICE 90 FINAL START ==="
say "TIME=$(date)"
say "LOG=$LOG"
say "ENV=$(whoami 2>/dev/null) $(id 2>/dev/null)"
say "PKG=$PKG FPS=$FPS SCALE=$SCALE"

CHECK="$(settings get system peak_refresh_rate 2>&1)"
say "SERVICE_CHECK_peak_refresh_rate=$CHECK"
case "$CHECK" in
    *"Failed transaction"*)
        say "STOP_SYSTEM_SERVER_TRANSACTION_FAIL_REBOOT_OR_RESTART_SHIZUKU"
        exit 2
        ;;
    *"Permission Denial"*)
        say "STOP_NOT_SHELL_USE_SHIZUKU_OR_ADB_SHELL"
        exit 3
        ;;
esac

say "--- 0 STOP OLD CODM PROCESS BEFORE UNITY SURFACE ---"
cmd_run am force-stop "$PKG"
sleep 3

say "--- 1 CLEAN QUALITY RESTORE ---"
cmd_run wm size reset
cmd_run wm density reset
cmd_run settings put global low_ram false
cmd_run setprop debug.egl.force_rgba_4444 0
cmd_run setprop debug.egl.force_msaa 0
cmd_run setprop debug.graphics.max_texture_size 0
cmd_run setprop debug.hwui.capture_skiabitmap false
cmd_run setprop debug.renderthread.reduceopstasksplitting 0

say "--- 2 DISPLAY 90HZ + XIAOMI KEYS ---"
cmd_run settings put system peak_refresh_rate 90.0
cmd_run settings put system min_refresh_rate 90.0
cmd_run settings put system user_refresh_rate 90.0
cmd_run settings put system miui_refresh_rate 90
cmd_run settings put secure user_refresh_rate 90
cmd_run settings put global user_refresh_rate 90

say "--- 3 MIUI / HYPEROS GAME ENGINE ---"
cmd_run settings put global game_engine_enable 1
cmd_run settings put secure game_engine_boost 1
cmd_run settings put global game_engine_boost 1
cmd_run settings put global game_engine_whitelist "$WL"
cmd_run settings put secure game_engine_whitelist "$WL"
cmd_run settings put global cloud_game_booster_whitelist "$WL"
cmd_run settings put secure cloud_game_booster_whitelist "$WL"
cmd_run settings put global mtk_perf_whitelist "$WL"
cmd_run settings put secure mtk_perf_whitelist "$WL"
cmd_run settings put global smart_power_whitelist "$WL"
cmd_run settings put secure smart_power_whitelist "$WL"
cmd_run settings put global miui_game_mode 1
cmd_run settings put secure game_mode 1
cmd_run settings put secure game_render_quality 1.0
cmd_run settings put global game_turbo_visual_enhancement 0
cmd_run settings put system game_turbo_visual_enhancement 0
cmd_run settings put system game_turbo_hdr_enabled 0
cmd_run settings put system game_turbo_shadow_enhancement 0

say "--- 4 GAME MANAGER / OVERLAY ---"
cmd_run settings put global game_driver_all_apps 0
cmd_run settings put global updatable_driver_all_apps 0
cmd_run settings put global game_driver_opt_in_apps "$PKG"
cmd_run setprop debug.graphics.game_default_frame_rate.disabled true
cmd_run device_config put game_overlay "$PKG" mode=2,downscaleFactor=$SCALE,fps=$FPS
cmd_run cmd game set --mode 2 --downscale "$SCALE" --fps "$FPS" "$PKG"
cmd_run cmd game mode performance "$PKG"
cmd_run cmd game mode 2 "$PKG"

say "--- 5 FOREGROUND / DOZE PROTECTION ---"
cmd_run cmd appops set "$PKG" RUN_IN_BACKGROUND allow
cmd_run cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow
cmd_run cmd appops set "$PKG" WAKE_LOCK allow
cmd_run cmd appops set "$PKG" START_FOREGROUND allow
cmd_run am set-inactive "$PKG" false
cmd_run am set-standby-bucket "$PKG" active
cmd_run cmd deviceidle whitelist +"$PKG"
cmd_run cmd deviceidle tempwhitelist -d 3600000 "$PKG"

say "--- 6 POWER / COMPILE ---"
cmd_run settings put global low_power 0
cmd_run cmd power set-adaptive-power-saver-enabled false
cmd_run cmd power set-fixed-performance-mode-enabled true
cmd_run cmd package compile -m speed-profile -f "$PKG"

say "--- 7 SAFE RENDERER PATH FOR MALI ---"
cmd_run setprop debug.hwui.renderer skiagl
cmd_run setprop debug.hwui.use_buffer_age true
cmd_run setprop debug.hwui.render_dirty_regions true
cmd_run setprop debug.hwui.skip_empty_damage true
cmd_run setprop debug.hwui.use_hint_manager true
cmd_run setprop debug.hwui.disable_vsync false
cmd_run setprop debug.hwui.disable_draw_defer false
cmd_run setprop debug.hwui.disable_draw_reorder false
cmd_run setprop debug.sf.disable_backpressure 0
cmd_run setprop debug.sf.enable_gl_backpressure 1
cmd_run setprop debug.sf.enable_hwc_vds 1

say "--- 8 INPUT LAG SAFE UI ---"
cmd_run settings put global window_animation_scale 0.0
cmd_run settings put global transition_animation_scale 0.0
cmd_run settings put global animator_duration_scale 0.0
cmd_run settings put secure swipe_bottom_to_switch_apps_enabled 0

say "--- 9 START CODM AFTER PROFILE ---"
am start -n "$PKG/com.tencent.tmgp.cod.CODMPrivatePermissionActivity" >> "$LOG" 2>&1 || \
am start -n "$PKG/com.tencent.tmgp.cod.CODMainActivity" >> "$LOG" 2>&1 || \
monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >> "$LOG" 2>&1
sleep 5

say "--- 10 PRE-SURFACE REAPPLY LOOP 24S ---"
i=1
while [ "$i" -le 12 ]; do
    say "PULSE=$i"
    cmd_run settings put system peak_refresh_rate 90.0
    cmd_run settings put system min_refresh_rate 90.0
    cmd_run settings put system user_refresh_rate 90.0
    cmd_run setprop debug.graphics.game_default_frame_rate.disabled true
    cmd_run device_config put game_overlay "$PKG" mode=2,downscaleFactor=$SCALE,fps=$FPS
    cmd_run cmd game set --mode 2 --downscale "$SCALE" --fps "$FPS" "$PKG"
    cmd_run cmd game mode 2 "$PKG"
    cmd_run am set-inactive "$PKG" false
    cmd_run am set-standby-bucket "$PKG" active
    cmd_run cmd deviceidle tempwhitelist -d 3600000 "$PKG"
    if [ "$i" = "1" ] || [ "$i" = "6" ] || [ "$i" = "12" ]; then
        cmd_run cmd appops set "$PKG" RUN_IN_BACKGROUND allow
        cmd_run cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow
        cmd_run cmd appops set "$PKG" WAKE_LOCK allow
        cmd_run cmd appops set "$PKG" START_FOREGROUND allow
    fi
    i=$((i+1))
    sleep 2
done

say "--- FINAL CHECK ---"
say "peak=$(settings get system peak_refresh_rate 2>&1)"
say "min=$(settings get system min_refresh_rate 2>&1)"
say "user=$(settings get system user_refresh_rate 2>&1)"
say "overlay=$(device_config get game_overlay "$PKG" 2>&1)"
say "game_mode=$(cmd game mode "$PKG" 2>&1)"
say "codm_pid=$(pidof "$PKG" 2>/dev/null)"
say "TXN_FAIL=$TXN_FAIL DENY=$DENY"
say "=== CODM MASTER SERVICE 90 FINAL DONE ==="
echo "CODM_MASTER_90_DONE"
echo "$LOG"
