#!/usr/bin/env bash
# Запускается внутри android-emulator-runner, когда эмулятор уже загружен.
# Падение тестов не должно съедать артефакты, поэтому статус запоминается
# и возвращается в самом конце.

API="${1:?нужен уровень API}"
OUT="artifacts/api-${API}"
mkdir -p "$OUT"

adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

adb logcat -c || true
adb logcat -v time > "$OUT/logcat.txt" &
LOGCAT_PID=$!

./gradlew :app:connectedFullDebugAndroidTest --stacktrace
STATUS=$?

sleep 2
kill "$LOGCAT_PID" 2>/dev/null || true

# Скриншоты и заметки тестов лежат в каталоге приложения на внешней памяти.
adb pull /sdcard/Android/data/com.bibliarium.app/files/test-artifacts "$OUT/screenshots" 2>/dev/null || true

adb shell appops get com.bibliarium.app MANAGE_EXTERNAL_STORAGE > "$OUT/appops.txt" 2>&1 || true

echo "=== содержимое $OUT"
ls -R "$OUT" || true

exit "$STATUS"
