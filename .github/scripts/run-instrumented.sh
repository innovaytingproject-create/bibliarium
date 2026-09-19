#!/usr/bin/env bash
# Запускается внутри android-emulator-runner, когда эмулятор уже загружен.
#
# Почему тесты запускаются через am instrument, а не через gradle:
#
# 1. Менять appop MANAGE_EXTERNAL_STORAGE во время инструментального прогона
#    нельзя: соединение UiAutomation рвётся, и прогон падает целиком. Значит,
#    состояние разрешения надо выставлять снаружи, до запуска.
# 2. Задача connectedAndroidTest ставит APK и запускает тесты одним куском,
#    а переустановка сбрасывает appop обратно в default.
#
# Поэтому: один раз ставим APK, дальше сами управляем разрешением и запуском.

API="${1:?нужен уровень API}"
OUT="artifacts/api-${API}"
PKG="com.bibliarium.app"
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
mkdir -p "$OUT"

adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

adb logcat -c || true
adb logcat -v time > "$OUT/logcat.txt" &
LOGCAT_PID=$!

./gradlew :app:installFullDebug :app:installFullDebugAndroidTest --stacktrace
INSTALL_STATUS=$?
if [ "$INSTALL_STATUS" -ne 0 ]; then
  kill "$LOGCAT_PID" 2>/dev/null || true
  echo "Установка не удалась"
  exit "$INSTALL_STATUS"
fi

# Прогон одной пачки тестов. Возвращает 1, если тесты упали или процесс умер.
run_pass() {
  local name="$1"
  local package="$2"
  local log="$OUT/$name.txt"

  adb shell am force-stop "$PKG" || true
  adb shell am instrument -w -e package "$package" "$RUNNER" > "$log" 2>&1

  # Артефакты лежат во внутреннем каталоге приложения и достаются через run-as:
  # на Android 11 shell не читает /sdcard/Android/data, и на API 30 выгрузка
  # через внешнюю память молча не срабатывала.
  local shots="$OUT/$name/screenshots"
  mkdir -p "$shots"
  for f in $(adb exec-out run-as "$PKG" ls files/test-artifacts 2>/dev/null); do
    adb exec-out run-as "$PKG" cat "files/test-artifacts/$f" > "$shots/$f" 2>/dev/null || true
  done
  adb exec-out run-as "$PKG" rm -rf files/test-artifacts > /dev/null 2>&1 || true

  echo "--- $name ---"
  tail -40 "$log"

  if grep -qE "FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|shortMsg=" "$log"; then
    return 1
  fi
  if ! grep -q "OK (" "$log"; then
    return 1
  fi
  return 0
}

# --- Прогон 1: разрешения нет ---
adb shell am force-stop "$PKG" || true
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE default || true
run_pass "no-access" "com.bibliarium.app.noaccess"
STATUS_NO_ACCESS=$?

# --- Проверка: переживает ли процесс приложения выдачу разрешения ---
adb shell am force-stop "$PKG" || true
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE default || true
adb shell am start -n "$PKG/$PKG.ui.MainActivity" > /dev/null 2>&1 || true
PID_BEFORE=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  PID_BEFORE=$(adb shell pidof "$PKG" | tr -d '\r\n')
  [ -n "$PID_BEFORE" ] && break
  sleep 1
done
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow
sleep 5
PID_AFTER=$(adb shell pidof "$PKG" | tr -d '\r\n')
{
  echo "Выдача MANAGE_EXTERNAL_STORAGE при работающем приложении:"
  echo "  pid до выдачи:    ${PID_BEFORE:-<процесс не запустился>}"
  echo "  pid после выдачи: ${PID_AFTER:-<процесса нет>}"
  echo
  if [ -z "$PID_BEFORE" ]; then
    echo "ВЫВОД: проверить не удалось — приложение не запустилось."
  elif [ "$PID_BEFORE" != "$PID_AFTER" ]; then
    echo "ВЫВОД: процесс приложения убит системой при выдаче разрешения."
  else
    echo "ВЫВОД: процесс пережил выдачу разрешения."
  fi
} > "$OUT/process-kill-evidence.txt"
cat "$OUT/process-kill-evidence.txt"

# --- Прогон 2: разрешение уже выдано, приложение стартует с ним с нуля ---
adb shell am force-stop "$PKG" || true
adb shell appops get "$PKG" MANAGE_EXTERNAL_STORAGE > "$OUT/appops-before-full-access.txt" 2>&1 || true
run_pass "full-access" "com.bibliarium.app.fullaccess"
STATUS_FULL_ACCESS=$?

adb shell appops get "$PKG" MANAGE_EXTERNAL_STORAGE > "$OUT/appops-after.txt" 2>&1 || true

sleep 2
kill "$LOGCAT_PID" 2>/dev/null || true

echo "=== содержимое $OUT"
find "$OUT" -type f | head -50
echo "=== статусы: без доступа=$STATUS_NO_ACCESS, с полным доступом=$STATUS_FULL_ACCESS"

if [ "$STATUS_NO_ACCESS" -ne 0 ]; then exit "$STATUS_NO_ACCESS"; fi
exit "$STATUS_FULL_ACCESS"
