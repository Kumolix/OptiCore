#!/usr/bin/env bash
# Печатает javap классов клиента в именах Mojang. Использование: bash scripts/inspect.sh "<флаги javap>" <класс>...
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FLAGS="$1"; shift
MC="$(grep '^minecraftVersion=' "$ROOT/gradle.properties" | cut -d= -f2)"
W="${CORELIX_VERIFY_DIR:-$(mktemp -d)}"
bash "$ROOT/scripts/fetch-client.sh" "$MC" "$W" >/dev/null
REMAP="$(ls "$ROOT"/remap/build/libs/corelix-remap*.jar | head -1)"
[ -f "$W/client-mojmap.jar" ] || java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli remap "$W/client.jar" "$W/client.txt" "$W/client-mojmap.jar"
CP="$W/client-mojmap.jar:$(ls "$W"/libs/*.jar | tr '\n' ':')"
for c in "$@"; do
  echo "=================== $c"
  javap $FLAGS -cp "$CP" "$c" || echo "(не удалось)"
done
