#!/usr/bin/env bash
# Проверка ремапа на НАСТОЯЩЕМ клиенте Minecraft. Нужна сеть (серверы Mojang), поэтому запускается в CI.
# Скачивает client.jar, client.txt (маппинги Mojang) и библиотеки версии, перегоняет клиент в имена Mojang,
# затем загружает и верифицирует каждый класс результата.
# Использование: bash scripts/verify-real.sh [версия, по умолчанию из gradle.properties]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MC="${1:-$(grep '^minecraftVersion=' "$ROOT/gradle.properties" | cut -d= -f2)}"
REMAP="$ROOT/remap/build/libs/corelix-remap.jar"
[ -f "$REMAP" ] || { echo "нет $REMAP, сначала выполните: gradle build"; exit 2; }

W="${CORELIX_VERIFY_DIR:-$(mktemp -d)}"
mkdir -p "$W/libs"
echo "Minecraft $MC, рабочая папка $W"

python3 - "$MC" "$W" <<'PY'
import hashlib, json, os, sys, urllib.request

mc, work = sys.argv[1], sys.argv[2]

def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()

def download(url, dest, sha1):
    if os.path.exists(dest) and hashlib.sha1(open(dest, "rb").read()).hexdigest() == sha1:
        return
    data = fetch(url)
    got = hashlib.sha1(data).hexdigest()
    if got != sha1:
        sys.exit(f"sha1 не совпал для {url}: ожидалось {sha1}, получено {got}")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data)

manifest = json.loads(fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
entry = next((v for v in manifest["versions"] if v["id"] == mc), None)
if entry is None:
    sys.exit(f"версия {mc} не найдена в манифесте Mojang")
ver = json.loads(fetch(entry["url"]))

dl = ver["downloads"]
download(dl["client"]["url"], f"{work}/client.jar", dl["client"]["sha1"])
if "client_mappings" not in dl:
    sys.exit(f"у версии {mc} нет client_mappings (официальных маппингов)")
download(dl["client_mappings"]["url"], f"{work}/client.txt", dl["client_mappings"]["sha1"])

def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    result = False
    for r in rules:
        os_rule = r.get("os")
        applies = os_rule is None or (os_rule.get("name") in (None, "linux") and "arch" not in os_rule)
        if applies:
            result = r["action"] == "allow"
    return result

count = 0
for lib in ver["libraries"]:
    art = lib.get("downloads", {}).get("artifact")
    if not art or not allowed(lib) or "natives" in lib["name"]:
        continue
    download(art["url"], f"{work}/libs/{os.path.basename(art['path'])}", art["sha1"])
    count += 1
print(f"скачано: client.jar, client.txt, библиотек: {count}")
PY

echo "--- ремап"
java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli remap "$W/client.jar" "$W/client.txt" "$W/client-mojmap.jar"

echo "--- верификация всех классов"
java -Xmx2g -cp "$REMAP" dev.corelix.remap.Cli verify "$W/client-mojmap.jar" "$W/libs"
echo "Проверка на настоящем клиенте $MC пройдена"
