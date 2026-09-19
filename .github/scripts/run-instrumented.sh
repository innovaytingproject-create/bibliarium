#!/usr/bin/env bash
# Запускается внутри android-emulator-runner, когда эмулятор уже загружен.
#
# Прогонов два, и это не прихоть: система убивает процесс приложения, когда
# меняется appop MANAGE_EXTERNAL_STORAGE. Переключить разрешение внутри теста
# нельзя — вместе с процессом умирает и сам прогон. Поэтому состояние
# выставляется снаружи, до запуска каждой пачки тестов.

API="${1:?нужен уровень API}"
OUT="artifacts/api-${API}"
PKG="com.bibliarium.app"
mkdir -p "$OUT"

adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

adb logcat -c || true
adb logcat -v time > "$OUT/logcat.txt" &
LOGCAT_PID=$!

collect() {
  local name="$1"
  mkdir -p "$OUT/$name"
  adb pull "/sdcard/Android/data/$PKG/files/test-artifacts" "$OUT/$name/screenshots" 2>/dev/null || true
  adb shell rm -rf "/sdcard/Android/data/$PKG/files/test-artifacts" 2>/dev/null || true
  cp -r app/build/reports/androidTests/connected "$OUT/$name/report" 2>/dev/null || true
  cp -r app/build/outputs/androidTest-results/connected "$OUT/$name/results" 2>/dev/null || true
}

# --- Прогон 1: разрешения нет (свежая установка — состояние по умолчанию) ---
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.bibliarium.app.noaccess \
  --stacktrace
STATUS_NO_ACCESS=$?
collect "no-access"

# --- Доказательство: выдача разрешения убивает процесс приложения ---
# Это и есть причина, по которой приложение «ничего не делает» после того,
# как пользователь дал доступ в системных настройках.
adb shell am start -n "$PKG/$PKG.ui.MainActivity" > /dev/null 2>&1 || true
sleep 5
PID_BEFORE=$(adb shell pidof "$PKG" | tr -d '\r\n')
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow
sleep 5
PID_AFTER=$(adb shell pidof "$PKG" | tr -d '\r\n')
{
  echo "Выдача MANAGE_EXTERNAL_STORAGE через appops:"
  echo "  pid до выдачи:    ${PID_BEFORE:-<нет процесса>}"
  echo "  pid после выдачи: ${PID_AFTER:-<нет процесса>}"
  echo
  if [ -n "$PID_BEFORE" ] && [ "$PID_BEFORE" != "$PID_AFTER" ]; then
    echo "ВЫВОД: процесс приложения убит системой при изменении разрешения."
  else
    echo "ВЫВОД: процесс пережил изменение разрешения."
  fi
} > "$OUT/process-kill-evidence.txt"
cat "$OUT/process-kill-evidence.txt"

# --- Прогон 2: разрешение выдано ---
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.bibliarium.app.fullaccess \
  --stacktrace
STATUS_FULL_ACCESS=$?
collect "full-access"

adb shell appops get "$PKG" MANAGE_EXTERNAL_STORAGE > "$OUT/appops.txt" 2>&1 || true

sleep 2
kill "$LOGCAT_PID" 2>/dev/null || true

echo "=== содержимое $OUT"
find "$OUT" -type f | head -50

echo "=== статусы: без доступа=$STATUS_NO_ACCESS, с полным доступом=$STATUS_FULL_ACCESS"
if [ "$STATUS_NO_ACCESS" -ne 0 ]; then exit "$STATUS_NO_ACCESS"; fi
exit "$STATUS_FULL_ACCESS"
