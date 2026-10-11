#!/system/bin/sh
PKG="com.activision.callofduty.shooter"

echo "======================================"
echo " CODM POST-MASTER CANDY / CLEAN DISPLAY"
echo "======================================"

echo
echo "[1] POWER PERFORMANCE"
cmd power set-mode 0
cmd power set-adaptive-power-saver-enabled false
cmd power set-fixed-performance-mode-enabled true
echo "Fixed Performance EXIT=$?"

echo
echo "[2] CODM GAME MODE PERFORMANCE"
cmd game mode performance "$PKG"

echo
echo "[3] REMOVE WINDOW BLUR"
wm disable-blur 1

echo
echo "[4] REMOVE COLOR INVERSION"
settings put secure accessibility_display_inversion_enabled 0

echo
echo "[5] REMOVE REDUCE BRIGHT COLORS"
settings put secure reduce_bright_colors_activated 0

echo
echo "[6] REMOVE NIGHT DISPLAY"
settings put secure night_display_activated 0

echo
echo "[7] DISPLAY MANAGER 90 HZ"

SIZE="$(wm size | sed -n 's/^Physical size: //p' | head -n 1)"
W="${SIZE%x*}"
H="${SIZE#*x}"

echo "Physical=${W}x${H}"

if [ -n "$W" ] && [ -n "$H" ]; then
    cmd display set-user-preferred-display-mode "$W" "$H" 90 0
else
    echo "DISPLAY_SIZE_DETECT_FAILED"
fi

sleep 1

echo
echo "========== VERIFY =========="

echo
echo "GAME MODE:"
cmd game mode "$PKG"

echo
echo "DISPLAY:"
cmd display get-user-preferred-display-mode 0

echo
echo "BLUR:"
wm disable-blur

echo
echo "COLOR INVERSION:"
settings get secure accessibility_display_inversion_enabled

echo
echo "REDUCE BRIGHT COLORS:"
settings get secure reduce_bright_colors_activated

echo
echo "NIGHT DISPLAY:"
settings get secure night_display_activated

echo
echo "DALTONIZER:"
settings get secure accessibility_display_daltonizer_enabled

echo
echo "EVEN DIMMER:"
settings get secure even_dimmer_activated

echo
echo "EGL/HWUI:"
echo -n "MSAA="
getprop debug.egl.force_msaa
echo -n "RGBA4444="
getprop debug.egl.force_rgba_4444
echo -n "RENDERER="
getprop debug.hwui.renderer
echo -n "VSYNC_DISABLED="
getprop debug.hwui.disable_vsync

echo
echo "CPU:"
for c in /sys/devices/system/cpu/cpu[0-9]*; do
    CUR="$(cat "$c/cpufreq/scaling_cur_freq" 2>/dev/null)"
    MAX="$(cat "$c/cpufreq/cpuinfo_max_freq" 2>/dev/null)"
    [ -n "$CUR" ] && echo "$(basename "$c"): CUR=$CUR / MAX=$MAX"
done

echo
echo "======================================"
echo " POST-MASTER CANDY DONE"
echo "======================================"
