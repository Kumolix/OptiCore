#!/usr/bin/env bash
# Полная цепочка на НАСТОЯЩЕМ клиенте: загрузчик -> ремап в имена Mojang -> Mixin -> изменённый net.minecraft.client.Minecraft.
# Нужна сеть (Mojang), поэтому запускается в CI после: gradle build && gradle :mixin-support:bundle
# Использование: bash scripts/verify-mixin.sh [версия, по умолчанию из gradle.properties]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MC="${1:-$(grep '^minecraftVersion=' "$ROOT/gradle.properties" | cut -d= -f2)}"
LOADER="$ROOT/loader/build/libs/corelix-loader.jar"
REMAP="$ROOT/remap/build/libs/corelix-remap.jar"
BUNDLE="$ROOT/mixin-support/build/bundle"
FIX="$ROOT/scripts/fixtures/mixin"

for f in "$LOADER" "$REMAP" "$BUNDLE"; do
  [ -e "$f" ] || { echo "нет $f, сначала: gradle build && gradle :mixin-support:bundle"; exit 2; }
done

W="${CORELIX_VERIFY_DIR:-$(mktemp -d)}"
echo "Minecraft $MC, рабочая папка $W"
bash "$ROOT/scripts/fetch-client.sh" "$MC" "$W"

# Клиент в именах Mojang нужен только для компиляции тестового миксина; игра получит свой ремап через загрузчик.
[ -f "$W/client-mojmap.jar" ] || java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli remap "$W/client.jar" "$W/client.txt" "$W/client-mojmap.jar"

# Папка libs Corelix: ремаппер + сервис Mixin + Mixin + ASM
LIBS="$W/corelix-libs"
rm -rf "$LIBS"; mkdir -p "$LIBS"
cp "$BUNDLE"/*.jar "$REMAP" "$LIBS/"
echo "libs: $(ls "$LIBS" | tr '\n' ' ')"

MIXIN_JAR="$(ls "$BUNDLE"/sponge-mixin-*.jar | head -1)"
MCLIBS="$(ls "$W"/libs/*.jar | tr '\n' ':')"

# Тестовый мод с миксином
B="$W/mixintest-build"
rm -rf "$B"; mkdir -p "$B"
javac -proc:none -nowarn -cp "$W/client-mojmap.jar:$MIXIN_JAR:$LOADER:$MCLIBS" -d "$B" \
  $(find "$FIX/src" -name '*.java')
cp "$FIX"/resources/* "$B/"
rm -rf "$W/game"; mkdir -p "$W/game/mods"
jar cf "$W/game/mods/mixintest.jar" -C "$B" .

echo "--- запуск через загрузчик"
set +e
OUT="$(java -Xmx2g \
  -Dcorelix.platform=desktop \
  -Dcorelix.libs="$LIBS" \
  -Dcorelix.mappings="$W/client.txt" \
  -Dcorelix.cache="$W/cache" \
  -Dcorelix.mainClass=dev.corelix.mixintest.MixinCheckMain \
  -cp "$LOADER:$W/client.jar:$MCLIBS" \
  dev.corelix.loader.CorelixMain --gameDir "$W/game" 2>&1)"
code=$?
set -e
echo "$OUT" | tail -60
if [ "$code" -ne 0 ] || ! grep -q "MIXIN_REAL_OK" <<<"$OUT"; then
  echo "ПРОВЕРКА MIXIN НЕ ПРОЙДЕНА (код $code)"
  exit 1
fi
echo "Mixin на настоящем клиенте $MC работает"
