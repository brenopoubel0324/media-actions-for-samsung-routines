#!/usr/bin/env sh
# Read-only evidence collection. No notification/media dumps or device identifiers.
set -eu
adb get-state >/dev/null
printf 'Captured UTC: '
date -u '+%Y-%m-%dT%H:%M:%SZ'
for prop in ro.product.model ro.product.manufacturer ro.build.version.release ro.build.version.sdk ro.build.version.oneui ro.build.version.security_patch ro.build.display.id ro.build.fingerprint; do
    printf '%s: ' "$prop"
    adb shell getprop "$prop" | tr -d '\r'
done
for package in com.samsung.android.app.routines com.samsung.android.app.routineplus com.samsung.systemui.lockstar com.spotify.music dev.local.mediaactions; do
    printf '\nPackage: %s\n' "$package"
    # Only version fields; deliberately exclude other dumpsys package output.
    adb shell dumpsys package "$package" | sed -n '/versionCode=/p; /versionName=/p' | head -n 4
done
printf '\nRecord manually: physical button/event, Routines+/LockStar installed/available, widget size/placement, secure-lock setup, AOD, playback route.\n'
