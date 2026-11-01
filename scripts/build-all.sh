#!/usr/bin/env bash
# 按"跨版本区间"构建 jar（组内字节码一致，因此一个包可覆盖整段）。
# 用法：./scripts/build-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"

MOD_VERSION=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)
OUT="$PWD/build/versions"
mkdir -p "$OUT"
rm -f build/libs/*.jar "$OUT"/*.jar

# 格式：编译用版本|yarn|fabric-api|ModMenu|java|minecraft依赖范围|包名区间标签
VERSIONS=(
  "1.20.1|1.20.1+build.10|0.92.12+1.20.1|7.2.2|17|>=1.20.1 <1.20.5|1.20.1-1.20.4"
  "1.20.6|1.20.6+build.3|0.100.8+1.20.6|10.0.0|21|1.20.6|1.20.6"
  "1.21.1|1.21.1+build.3|0.116.17+1.21.1|11.0.5|21|>=1.21.1 <1.21.6|1.21.1-1.21.5"
  "1.21.6|1.21.6+build.1|0.128.2+1.21.6|15.0.2|21|>=1.21.6 <1.21.9|1.21.6-1.21.8"
)

for entry in "${VERSIONS[@]}"; do
  IFS='|' read -r MC YARN API MODMENU JAVA MCDEP LABEL <<< "$entry"
  echo "=== Building MC $LABEL (compile against $MC, Java $JAVA) ==="
  ./gradlew build --console=plain --no-daemon --max-workers=2 \
    -Pminecraft_version="$MC" \
    -Pyarn_mappings="$YARN" \
    -Pfabric_api_version="$API" \
    -Pmodmenu_version="$MODMENU" \
    -Pjava_release="$JAVA" \
    -Pminecraft_dependency="$MCDEP"
  JAR="build/libs/eyelocator-${MOD_VERSION}.jar"
  DEST="$OUT/eyelocator-${MOD_VERSION}+mc${LABEL}.jar"
  cp "$JAR" "$DEST"
  echo "  -> $DEST"
done

echo "全部完成，产物在: $OUT"
