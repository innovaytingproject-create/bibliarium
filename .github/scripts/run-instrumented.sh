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
  # Память эмулятора до и после пачки: на API 34 эмулятор пропадал целиком,
  # без единой строчки в логе, и понять, чем он кончился, было не по чему.
  adb shell cat /proc/meminfo > "$OUT/$name-mem-before.txt" 2>&1 || true
  adb shell am instrument -w -e package "$package" "$RUNNER" > "$log" 2>&1
  adb shell cat /proc/meminfo > "$OUT/$name-mem-after.txt" 2>&1 || true

  # Артефакты лежат во внутреннем каталоге приложения и достаются через run-as:
  # на Android 11 shell не читает /sdcard/Android/data, и на API 30 выгрузка
  # через внешнюю память молча не срабатывала.
  local shots="$OUT/$name/screenshots"
  mkdir -p "$shots"
  if adb exec-out run-as "$PKG" test -d files/test-artifacts 2>/dev/null; then
    for f in $(adb exec-out run-as "$PKG" ls files/test-artifacts 2>/dev/null); do
      # Если ls всё-таки что-то сказал вместо имён, такие строки пропускаем:
      # иначе в артефакты уезжает файл с именем вроде "ls:" и выгрузка падает.
      case "$f" in *:* | *" "* | "") continue ;; esac
      adb exec-out run-as "$PKG" cat "files/test-artifacts/$f" > "$shots/$f" 2>/dev/null || true
    done
  fi
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

# --- Проба: какая операция рисования убивает эмулятор API 34. Идёт самой
# первой, потому что полка эмулятор роняет, а забрать с погибшей машины
# нечего — переживает только logcat.
run_pass "probe" "com.bibliarium.app.probe"
STATUS_PROBE=$?

# --- Прогон 0: полка. Стоит первой намеренно: на API 34 эмулятор исчезал
# именно на ней, и надо отделить «виновата полка» от «эмулятор к этому
# моменту уже на исходе».
run_pass "shelf" "com.bibliarium.app.shelftests"
STATUS_SHELF=$?

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

# --- Прогон 3: конвертер FB2. Никакого UI и никаких разрешений — чистая
# проверка разбора и сборки книги.
run_pass "fb2" "com.bibliarium.app.fb2tests"
STATUS_FB2=$?

# --- Прогон 4: чтение. Разрешения на файлы тут ни при чём: книги приезжают
# из androidTest/assets и импортируются штатным путём во внутреннюю память.
run_pass "reader" "com.bibliarium.app.readertests"
STATUS_READER=$?

adb shell appops get "$PKG" MANAGE_EXTERNAL_STORAGE > "$OUT/appops-after.txt" 2>&1 || true

sleep 2
kill "$LOGCAT_PID" 2>/dev/null || true

echo "=== содержимое $OUT"
find "$OUT" -type f | head -50
echo "=== статусы: проба=$STATUS_PROBE, без доступа=$STATUS_NO_ACCESS, полный доступ=$STATUS_FULL_ACCESS, fb2=$STATUS_FB2, чтение=$STATUS_READER, полка=$STATUS_SHELF"

if [ "$STATUS_NO_ACCESS" -ne 0 ]; then exit "$STATUS_NO_ACCESS"; fi
if [ "$STATUS_FULL_ACCESS" -ne 0 ]; then exit "$STATUS_FULL_ACCESS"; fi
if [ "$STATUS_FB2" -ne 0 ]; then exit "$STATUS_FB2"; fi
if [ "$STATUS_READER" -ne 0 ]; then exit "$STATUS_READER"; fi
exit "$STATUS_SHELF"
