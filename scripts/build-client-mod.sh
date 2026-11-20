#!/usr/bin/env bash
# Собирает мод corelix-client (миксины на классы Minecraft) против настоящего клиента в именах Mojang.
# Нужна сеть (Mojang) и предварительно: gradle build && gradle :mixin-support:bundle
# Результат: client/build/corelix-client.jar. Использование: bash scripts/build-client-mod.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MC="$(grep '^minecraftVersion=' "$ROOT/gradle.properties" | cut -d= -f2)"
VERSION="$(grep '^corelixVersion=' "$ROOT/gradle.properties" | cut -d= -f2)"
LOADER="$ROOT/loader/build/libs/corelix-loader.jar"
REMAP="$ROOT/remap/build/libs/corelix-remap.jar"
BUNDLE="$ROOT/mixin-support/build/bundle"

W="${CORELIX_VERIFY_DIR:-$(mktemp -d)}"
bash "$ROOT/scripts/fetch-client.sh" "$MC" "$W"
[ -f "$W/client-mojmap.jar" ] || java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli remap "$W/client.jar" "$W/client.txt" "$W/client-mojmap.jar"

MIXIN_JAR="$(ls "$BUNDLE"/sponge-mixin-*.jar | head -1)"
MCLIBS="$(ls "$W"/libs/*.jar | tr '\n' ':')"
B="$ROOT/client/build/classes"
rm -rf "$ROOT/client/build"; mkdir -p "$B"
javac -proc:none -nowarn -cp "$W/client-mojmap.jar:$MIXIN_JAR:$LOADER:$ROOT/core/build/libs/corelix-core.jar:$MCLIBS" -d "$B" $(find "$ROOT/client/src" -name '*.java')
for f in "$ROOT"/client/resources/*; do sed "s/\${version}/$VERSION/" "$f" > "$B/$(basename "$f")"; done
jar cf "$ROOT/client/build/corelix-client.jar" -C "$B" .
echo "собрано: client/build/corelix-client.jar ($VERSION, Minecraft $MC)"
