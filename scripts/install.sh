#!/usr/bin/env bash
# Build-free install of the release APK onto the TV, kept on internal storage.
#
# The TV has "Force allow apps on external" on (developer options), which makes Android
# ignore our installLocation="internalOnly" and put every install/update on the adopted
# USB volume. That volume mounts late at boot, so home would show FallbackHome until it
# does. So: install, then move the package back to internal, and check.
set -euo pipefail
TV="${TV:?set TV to the TV's adb address, e.g. TV=192.168.1.50:5555}"
PKG=io.github.shhrrtnvr.bravialauncher
APK="${1:-$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk}"

adb -s "$TV" install -r "$APK"
path=$(adb -s "$TV" shell pm path "$PKG" | tr -d '\r')
# Usually lands on USB; sometimes already internal (moving then fails with -6).
case "$path" in
  *package:/data/app/*) ;;
  *) adb -s "$TV" shell pm move-package "$PKG" internal
     path=$(adb -s "$TV" shell pm path "$PKG" | tr -d '\r') ;;
esac
echo "$path"
case "$path" in
  *package:/data/app/*) ;;
  *) echo "Still not on internal storage (another Android user locked? see docs/plan.md)" >&2; exit 1 ;;
esac
# The TV kernel-panics on adb reboot and sometimes on its own: flush to disk now.
adb -s "$TV" shell sync
