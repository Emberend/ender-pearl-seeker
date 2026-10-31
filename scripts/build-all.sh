#!/usr/bin/env bash
# 为多个 Minecraft 1.21.x 版本分别构建 jar。
# 用法：./scripts/build-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"

MOD_VERSION=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2)
OUT="$PWD/build/versions"
mkdir -p "$OUT"

# 格式：minecraft|yarn映射|fabric-api
VERSIONS=(
  "1.21.1|1.21.1+build.3|0.116.17+1.21.1"
  "1.21.2|1.21.2+build.1|0.106.1+1.21.2"
  "1.21.3|1.21.3+build.2|0.114.1+1.21.3"
  "1.21.4|1.21.4+build.8|0.119.4+1.21.4"
  "1.21.5|1.21.5+build.1|0.128.2+1.21.5"
  "1.21.6|1.21.6+build.1|0.128.2+1.21.6"
  "1.21.7|1.21.7+build.8|0.129.0+1.21.7"
  "1.21.8|1.21.8+build.1|0.136.1+1.21.8"
)

for entry in "${VERSIONS[@]}"; do
  IFS='|' read -r MC YARN API <<< "$entry"
  echo "=== Building for Minecraft $MC ==="
  ./gradlew build --console=plain --no-daemon --max-workers=2 \
    -Pminecraft_version="$MC" \
    -Pyarn_mappings="$YARN" \
    -Pfabric_api_version="$API"
  JAR=$(ls build/libs/*.jar | grep -v -- '-sources.jar' | head -n 1)
  DEST="$OUT/eyelocator-${MOD_VERSION}+mc${MC}.jar"
  cp "$JAR" "$DEST"
  echo "  -> $DEST"
done

echo "全部完成，产物在: $OUT"
