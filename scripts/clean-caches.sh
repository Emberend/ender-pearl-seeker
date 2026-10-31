#!/usr/bin/env bash
# 清理 Gradle / Loom 构建缓存，避免项目目录膨胀。
#
# 默认只保留 build-all.sh 实际使用的 4 个编译版本：
#   1.20.1（覆盖 1.20.1-1.20.4）、1.20.6、1.21.1（覆盖 1.21.1-1.21.5）、1.21.6（覆盖 1.21.6-1.21.8）
# 其它版本（1.19.x、1.21.2-1.21.5、1.21.7-1.21.11 等）的缓存会被删除。
#
# 用法：./scripts/clean-caches.sh
set -euo pipefail
cd "$(dirname "$0")/.."

KEEP_VERSIONS="1.20.1 1.20.6 1.21.1 1.21.6"

before=$(du -sm . 2>/dev/null | cut -f1)
echo "清理前: ${before} MB"

should_keep() {
    local version="$1"
    for keep in $KEEP_VERSIONS; do
        if [ "$version" = "$keep" ]; then
            return 0
        fi
    done
    return 1
}

# 1) Loom 按 Minecraft 版本建立的工作目录
for root in ".gradle-home/caches/fabric-loom" ".gradle/loom-cache"; do
    [ -d "$root" ] || continue
    for dir in "$root"/*/; do
        name=$(basename "$dir")
        case "$name" in
            1.*)
                if ! should_keep "$name"; then
                    rm -rf "$dir"
                fi
                ;;
        esac
    done
done

# 2) 两个 minecraftMaven 里按版本存放的 remap 产物
#    路径形如 <maven>/net/minecraft/minecraft-merged-<hash>/<version>-net.fabricmc.yarn...-v2
for maven in ".gradle/loom-cache/minecraftMaven" ".gradle-home/caches/fabric-loom/minecraftMaven"; do
    [ -d "$maven" ] || continue
    find "$maven" -mindepth 4 -maxdepth 4 -type d -name "1.*-net.fabricmc*" -print0 |
        while IFS= read -r -d '' dir; do
            version=$(basename "$dir" | sed 's/-net\.fabricmc.*//')
            if ! should_keep "$version"; then
                rm -rf "$dir"
            fi
        done
    # 清掉被删空的父目录（例如只剩空 hash 目录）
    find "$maven" -mindepth 1 -type d -empty -delete 2>/dev/null || true
done

# 3) 重新 remap 得到的 mod 依赖（删掉后下次构建会重建）
rm -rf .gradle/loom-cache/remapped_mods

# 4) 构建中间产物，保留 build/versions 里的成品 jar
if [ -d build ]; then
    find build -mindepth 1 -maxdepth 1 ! -name versions -exec rm -rf {} +
fi

after=$(du -sm . 2>/dev/null | cut -f1)
echo "清理后: ${after} MB（释放 $((before - after)) MB）"
echo "提示：以后单独构建未保留的版本时会重新下载依赖，属正常现象。"