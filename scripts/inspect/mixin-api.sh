#!/usr/bin/env bash
# Разведка: скачивает Mixin и исходники Fabric Loader и печатает нужные интерфейсы.
# Запускается в CI workflow inspect.yml при изменении файлов в scripts/inspect/.
set -uo pipefail

W="$(mktemp -d)"
M=https://maven.fabricmc.net

echo "===== METADATA"
curl -fsSL "$M/net/fabricmc/sponge-mixin/maven-metadata.xml" | grep -E "<(latest|release)>"
curl -fsSL "$M/net/fabricmc/fabric-loader/maven-metadata.xml" | grep -E "<(latest|release)>"

MIXIN_V="$(curl -fsSL "$M/net/fabricmc/sponge-mixin/maven-metadata.xml" | sed -n 's|.*<release>\(.*\)</release>.*|\1|p')"
LOADER_V="$(curl -fsSL "$M/net/fabricmc/fabric-loader/maven-metadata.xml" | sed -n 's|.*<release>\(.*\)</release>.*|\1|p')"
echo "mixin=$MIXIN_V loader=$LOADER_V"

curl -fsSL -o "$W/mixin.jar" "$M/net/fabricmc/sponge-mixin/$MIXIN_V/sponge-mixin-$MIXIN_V.jar"
curl -fsSL -o "$W/mixin.pom" "$M/net/fabricmc/sponge-mixin/$MIXIN_V/sponge-mixin-$MIXIN_V.pom"
curl -fsSL -o "$W/loader-src.jar" "$M/net/fabricmc/fabric-loader/$LOADER_V/fabric-loader-$LOADER_V-sources.jar" || echo "NO SOURCES JAR"
curl -fsSL -o "$W/loader.pom" "$M/net/fabricmc/fabric-loader/$LOADER_V/fabric-loader-$LOADER_V.pom" || true

echo "===== MIXIN POM DEPENDENCIES"
python3 - "$W/mixin.pom" <<'PY'
import re, sys
t = open(sys.argv[1]).read()
for d in re.findall(r"<dependency>(.*?)</dependency>", t, re.S):
    g = re.search(r"<groupId>(.*?)</groupId>", d); a = re.search(r"<artifactId>(.*?)</artifactId>", d)
    v = re.search(r"<version>(.*?)</version>", d); s = re.search(r"<scope>(.*?)</scope>", d)
    print(g and g.group(1), a and a.group(1), v and v.group(1), s and s.group(1))
PY

echo "===== MIXIN JAR META-INF/services"
unzip -l "$W/mixin.jar" | grep -E "META-INF/services" || echo "(none)"
unzip -l "$W/mixin.jar" | grep -E "META-INF/MANIFEST"
unzip -p "$W/mixin.jar" META-INF/MANIFEST.MF | head -20

echo "===== javap service API"
for c in \
  org.spongepowered.asm.service.IMixinService \
  org.spongepowered.asm.service.MixinServiceAbstract \
  org.spongepowered.asm.service.IClassProvider \
  org.spongepowered.asm.service.IClassBytecodeProvider \
  org.spongepowered.asm.service.ITransformerProvider \
  org.spongepowered.asm.service.IClassTracker \
  org.spongepowered.asm.service.IMixinAuditTrail \
  org.spongepowered.asm.service.IGlobalPropertyService \
  org.spongepowered.asm.service.IPropertyKey \
  org.spongepowered.asm.service.IMixinServiceBootstrap \
  org.spongepowered.asm.service.IMixinInternal \
  org.spongepowered.asm.service.ISyntheticClassInfo \
  org.spongepowered.asm.service.IMixinPlatformServiceAgent \
  org.spongepowered.asm.launch.platform.container.IContainerHandle \
  org.spongepowered.asm.launch.platform.container.ContainerHandleURI \
  org.spongepowered.asm.mixin.transformer.IMixinTransformer \
  org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory \
  org.spongepowered.asm.launch.MixinBootstrap \
  org.spongepowered.asm.mixin.Mixins \
  org.spongepowered.asm.logging.ILogger \
  org.spongepowered.asm.logging.LoggerAdapterAbstract \
  org.spongepowered.asm.logging.LoggerAdapterDefault \
  ; do
  echo "----- $c"
  javap -p -cp "$W/mixin.jar" "$c" 2>&1 | grep -v "^Compiled from"
done
echo "----- MixinEnvironment\$Side / Phase / CompatibilityLevel"
javap -p -cp "$W/mixin.jar" 'org.spongepowered.asm.mixin.MixinEnvironment$Side' 'org.spongepowered.asm.mixin.MixinEnvironment$Phase' 2>&1 | grep -E "static|class" | head -30
javap -p -cp "$W/mixin.jar" 'org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel' 2>&1 | grep -E "JAVA_" | head -30

echo "===== FABRIC LOADER SOURCES"
mkdir -p "$W/src" && (cd "$W/src" && unzip -q -o ../loader-src.jar 2>/dev/null)
for f in \
  net/fabricmc/loader/impl/launch/knot/MixinServiceKnot.java \
  net/fabricmc/loader/impl/launch/knot/MixinServiceKnotBootstrap.java \
  net/fabricmc/loader/impl/launch/FabricMixinBootstrap.java \
  net/fabricmc/loader/impl/launch/knot/MixinServiceKnot\$*.java \
  ; do
  for p in $W/src/$f; do
    [ -f "$p" ] || continue
    echo "----- FILE $p" | sed "s|$W/src/||"
    cat "$p"
  done
done
echo "----- services files"
find "$W/src" -path "*META-INF/services*" -type f | while read -r s; do echo "## $s" | sed "s|$W/src/||"; cat "$s"; done
echo "----- mixin-related parts of KnotClassDelegate"
grep -n -i -E "mixin|transformClassBytes|getPreMixinClassBytes" "$W/src/net/fabricmc/loader/impl/launch/knot/KnotClassDelegate.java" | head -60
echo "===== DONE"
