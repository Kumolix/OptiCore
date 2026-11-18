#!/usr/bin/env bash
# Проверка ремапа на НАСТОЯЩЕМ клиенте Minecraft. Нужна сеть (серверы Mojang), поэтому запускается в CI.
# Перегоняет клиент в имена Mojang, затем загружает и верифицирует каждый класс результата.
# Использование: bash scripts/verify-real.sh [версия, по умолчанию из gradle.properties]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MC="${1:-$(grep '^minecraftVersion=' "$ROOT/gradle.properties" | cut -d= -f2)}"
REMAP="$ROOT/remap/build/libs/corelix-remap.jar"
[ -f "$REMAP" ] || { echo "нет $REMAP, сначала выполните: gradle build"; exit 2; }

W="${CORELIX_VERIFY_DIR:-$(mktemp -d)}"
echo "Minecraft $MC, рабочая папка $W"
bash "$ROOT/scripts/fetch-client.sh" "$MC" "$W"

echo "--- ремап"
java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli remap "$W/client.jar" "$W/client.txt" "$W/client-mojmap.jar"

echo "--- верификация всех классов"
java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli verify "$W/client-mojmap.jar" "$W/libs"
echo "Проверка на настоящем клиенте $MC пройдена"
