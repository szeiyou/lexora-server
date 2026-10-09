#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------
# pack.sh — 打包 dicserver 部署包 (tar.gz)
#
# 用法：
#   ./pack.sh                  # 输出到当前目录
#   ./pack.sh /tmp             # 输出到 /tmp
#
# 服务器解压后：
#   cd dicserver && docker compose up -d
# --------------------------------------------------

OUT_DIR="${1:-.}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSION="$(git describe --tags --always --dirty 2>/dev/null || date +%Y%m%d%H%M%S)"
TAR_NAME="dicserver-${VERSION}.tar.gz"
STAGING="dicserver"

echo "==> Cleaning old artifacts..."
rm -rf "$STAGING" "$OUT_DIR/$TAR_NAME"

echo "==> Staging files..."
mkdir -p "$STAGING"

# Core build files
cp pom.xml        "$STAGING/"
cp mvnw           "$STAGING/"
cp mvnw.cmd       "$STAGING/"
cp -r .mvn        "$STAGING/"

# Source code
cp -r src         "$STAGING/"

# Docker / deploy
cp Dockerfile          "$STAGING/"
cp docker-compose.yml  "$STAGING/"
cp .dockerignore       "$STAGING/" 2>/dev/null || true
cp .env.example        "$STAGING/" 2>/dev/null || true

# Docs (optional but useful)
cp README.md       "$STAGING/"

# Remove anything that should NOT ship
rm -rf "$STAGING/target"
rm -rf "$STAGING/.git"
rm -rf "$STAGING/.claude"
rm -rf "$STAGING/.github"
rm -rf "$STAGING/docs"
rm -rf "$STAGING/.vscode"
rm -rf "$STAGING/audio-data"

echo "==> Creating $TAR_NAME ..."
tar -czf "$OUT_DIR/$TAR_NAME" "$STAGING"
rm -rf "$STAGING"

SIZE="$(du -h "$OUT_DIR/$TAR_NAME" | cut -f1)"
echo "==> Done: $OUT_DIR/$TAR_NAME ($SIZE)"
echo ""
echo "  在服务器上："
echo "    tar -xzf $TAR_NAME"
echo "    cd dicserver"
echo "    cp .env.example .env   # 按需修改"
echo "    docker compose up -d"
