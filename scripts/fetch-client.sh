#!/usr/bin/env bash
# Скачивает у Mojang client.jar, client.txt (маппинги) и библиотеки версии в папку. Повторный запуск ничего не качает заново
# (проверка sha1). Использование: bash scripts/fetch-client.sh <версия> <папка>
set -euo pipefail

MC="$1"
W="$2"
mkdir -p "$W/libs"

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
print(f"готово: client.jar, client.txt, библиотек: {count}")
PY
