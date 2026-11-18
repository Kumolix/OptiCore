#!/usr/bin/env bash
# Дымовой тест загрузчика на «поддельной игре» (без настоящего Minecraft).
# Проверяет: поиск модов, whitelist, порядок по зависимостям, child-first загрузку,
# трансформацию байткода игровых классов, доступ игры к классам ядра, ошибку при нехватке зависимости.
# Запуск после сборки: gradle build && bash scripts/smoke.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOADER="$ROOT/loader/build/libs/corelix-loader.jar"
CORE="$ROOT/core/build/libs/corelix-core.jar"
TESTMOD="$ROOT/testmod/build/libs/corelix-testmod.jar"
REMAP="$ROOT/remap/build/libs/corelix-remap.jar"

for f in "$LOADER" "$CORE" "$TESTMOD" "$REMAP"; do
  [ -f "$f" ] || { echo "нет файла $f, сначала выполните: gradle build"; exit 2; }
done

W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

# --- поддельная игра ---------------------------------------------------------
mkdir -p "$W/src/net/minecraft/client/main" "$W/classes"
cat > "$W/src/net/minecraft/client/main/Main.java" <<'EOF'
package net.minecraft.client.main;

import dev.corelix.core.CorelixCore;
import dev.corelix.core.CorelixExecutor;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("GAME_MAIN says: HELLO_CORELIX");
        System.out.println("GAME_MAIN loader: " + Main.class.getClassLoader().getClass().getSimpleName());
        var future = CorelixCore.executor().submit(CorelixExecutor.Priority.HIGH, () -> 40 + 2);
        System.out.println("GAME_MAIN executor result: " + future.get());
    }
}
EOF
javac -nowarn -cp "$CORE:$LOADER" -d "$W/classes" "$W/src/net/minecraft/client/main/Main.java" 2>&1 | grep -v 'Picked up' || true
jar cf "$W/fake-game.jar" -C "$W/classes" .

run_game() { # $1 = папка игры, остальное = JVM-опции
  local game="$1"; shift
  java "$@" -cp "$LOADER:$W/fake-game.jar" dev.corelix.loader.CorelixMain --gameDir "$game" 2>&1 | grep -v 'Picked up'
}

check() { # описание, регулярка
  if grep -qE -- "$2" <<<"$OUT"; then
    echo "  ok: $1"
  else
    echo "  FAIL: $1 (ожидалось: $2)"
    echo "---- вывод ----"; echo "$OUT"; echo "---------------"
    exit 1
  fi
}

# --- сценарий 1: нормальный запуск, платформа desktop ----------------------------
echo "[1] нормальный запуск"
mkdir -p "$W/game1/mods"
cp "$CORE" "$TESTMOD" "$W/game1/mods/"
echo "not a corelix mod" > "$W/note.txt"
jar cf "$W/game1/mods/junk.jar" -C "$W" note.txt

OUT="$(run_game "$W/game1" -Dcorelix.platform=desktop)"
check "обычный jar без манифеста пропущен (whitelist)" 'skipped \(not a Corelix mod\): junk\.jar'
check "ядро запущено с профилем DESKTOP" 'profile=DESKTOP, worker threads=[0-9]+'
check "testmod запущен" '\[testmod\] testmod started'
check "байткод игрового класса изменён трансформером" 'GAME_MAIN says: WORLD_CORELIX'
check "игровой класс загружен нашим загрузчиком" 'GAME_MAIN loader: CorelixClassLoader'
check "игра видит класс ядра и пул задач работает" 'GAME_MAIN executor result: 42'
core_line="$(grep -n 'mod corelix-core' <<<"$OUT" | head -1 | cut -d: -f1)"
test_line="$(grep -n 'mod testmod' <<<"$OUT" | head -1 | cut -d: -f1)"
if [ -n "$core_line" ] && [ -n "$test_line" ] && [ "$core_line" -lt "$test_line" ]; then
  echo "  ok: зависимость (core) загружена раньше зависимого (testmod)"
else
  echo "  FAIL: порядок загрузки"; echo "$OUT"; exit 1
fi
[ -f "$W/game1/config/corelix.properties" ] && echo "  ok: конфиг создан" || { echo "  FAIL: конфиг не создан"; exit 1; }

# --- сценарий 2: профиль телефона ----------------------------------------------------
echo "[2] профиль MOBILE (и алиас pojav)"
OUT="$(run_game "$W/game1" -Dcorelix.platform=mobile)"
check "ядро запущено с профилем MOBILE" 'profile=MOBILE, worker threads=[0-9]+'
OUT="$(run_game "$W/game1" -Dcorelix.platform=pojav)"
check "значение pojav понимается как MOBILE" 'profile=MOBILE, worker threads=[0-9]+'

# --- сценарий 3: не хватает зависимости -------------------------------------------
echo "[3] отсутствует зависимость"
mkdir -p "$W/game3/mods"
cp "$TESTMOD" "$W/game3/mods/"
set +e
OUT="$(run_game "$W/game3")"
code=${PIPESTATUS[0]}
set -e
if [ "$code" -ne 1 ]; then echo "  FAIL: ожидался код выхода 1, получен $code"; echo "$OUT"; exit 1; fi
echo "  ok: код выхода 1"
check "понятная ошибка про отсутствующий мод" "requires missing mod 'corelix-core'"

# --- сценарий 4: ремап клиента (обфусцированные имена -> Mojang) ----------------------
echo "[4] ремап клиента через загрузчик"
OBF="$ROOT/scripts/fixtures/obf"
mkdir -p "$W/obf-classes" "$W/libs4" "$W/game4/mods"
javac -nowarn -d "$W/obf-classes" "$OBF"/o/*.java "$OBF/Main.java" 2>&1 | grep -v 'Picked up' || true
jar cf "$W/obf-client.jar" -C "$W/obf-classes" .
cp "$REMAP" "$W/libs4/"

run_obf() {
  java -Dcorelix.libs="$W/libs4" -Dcorelix.mappings="$OBF/client.txt" -Dcorelix.cache="$W/cache4" \
    -cp "$LOADER:$W/obf-client.jar" dev.corelix.loader.CorelixMain --gameDir "$W/game4" 2>&1 | grep -v 'Picked up'
}
OUT="$(run_obf)"
check "клиент перегнан в имена Mojang" 'remap: [0-9]+ classes in [0-9]+ ms'
check "игра работает на ремапнутых классах (унаследованные методы, поля, массивы, лямбды)" \
  'OBF_GAME g=20 h=1 i=static5 class=corelix\.test\.Impl'
OUT="$(run_obf)"
check "второй запуск берёт результат из кэша" 'remap: using cached'
check "и игра по-прежнему работает" 'OBF_GAME g=20 h=1 i=static5 class=corelix\.test\.Impl'

# оригинальный jar без ремапа остаётся обфусцированным (контроль, что тест различает)
OUT="$(java -cp "$W/obf-client.jar" net.minecraft.client.main.Main 2>&1 | grep -v 'Picked up')"
check "без загрузчика классы остаются обфусцированными" 'class=o\.b'

echo "Дымовой тест пройден"
