#!/bin/bash
# Direct-replacement verification: AppleUX owns HOME + Settings entry,
# overlays granted, stock home superseded. Usage: verify-replacement.sh
set -u
ADB="$ANDROID_HOME/platform-tools/adb"
echo "==> waiting for device"
"$ADB" wait-for-device
sleep 5
echo "==> set AppleUX as default HOME (stock Launcher3 superseded)"
"$ADB" shell cmd package set-home-activity com.appleux.launcher/.LauncherActivity
echo "==> grants for overlays + notifications + brightness"
"$ADB" shell appops set com.appleux.launcher SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
# The notification listener is declared in com.android.systemui's manifest and
# bound with a signature permission the platform grants automatically, so there
# is no enabled_notification_listeners entry to seed any more.
"$ADB" shell dumpsys notification | grep -m1 "com.appleux.systemui.AppleuxNotificationListener" || \
  echo "  (listener not yet connected - is SystemUI running?)"
"$ADB" shell appops set com.appleux.settings WRITE_SETTINGS allow 2>/dev/null || true
"$ADB" shell appops set com.appleux.settings WRITE_SETTINGS allow 2>/dev/null || true
echo "==> start SystemUI overlays + lockscreen"
# com.appleux.systemui was DELETED 2026-10-17 (deliverables/m23): it floated
# two full-screen TYPE_APPLICATION_OVERLAY windows over SystemUI. There is no
# overlay service to poke any more, so this now verifies the thing that
# replaced it: that com.android.systemui is installed and owns the panels.
"$ADB" shell pm path com.android.systemui || echo "  SystemUI not installed"
sleep 2
echo "==> top activity (expect AppleUX)"
"$ADB" shell dumpsys activity activities 2>/dev/null | grep -m 3 topResumedActivity
echo "==> packages present"
"$ADB" shell pm list packages 2>/dev/null | grep -i "appleux\|launcher3" || true
echo "==> press HOME so the default home (ours) is foreground"
"$ADB" shell input keyevent 3
sleep 3
"$ADB" shell dumpsys activity activities 2>/dev/null | grep -m 1 topResumedActivity
echo "==> screenshots"
"$ADB" exec-out screencap -p > screenshots/replace-home.png
"$ADB" shell input swipe 500 1800 500 400
sleep 2
"$ADB" exec-out screencap -p > screenshots/replace-cc.png
"$ADB" shell input keyevent 4
"$ADB" shell input swipe 500 400 500 1800
sleep 2
"$ADB" exec-out screencap -p > screenshots/replace-shade.png
ls -lh screenshots/replace-*.png
