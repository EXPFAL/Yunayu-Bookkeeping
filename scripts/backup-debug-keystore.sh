#!/usr/bin/env bash
# 将本机默认 debug.keystore 复制到仓库 Backups/（该目录已被 .gitignore），并打印 SHA-256。
set -euo pipefail
SRC="${HOME}/.android/debug.keystore"
if [[ ! -f "$SRC" ]]; then
  echo "找不到 debug.keystore：$SRC" >&2
  exit 1
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKUPS="${ROOT}/Backups"
mkdir -p "$BACKUPS"
STAMP="$(date +%Y%m%d)"
DEST="${BACKUPS}/yunayu-debug-keystore-${STAMP}.keystore"
cp -f "$SRC" "$DEST"
echo "已备份到：${DEST}"
if command -v keytool >/dev/null 2>&1; then
  keytool -list -v -keystore "$DEST" -storepass android -alias androiddebugkey
else
  echo "未找到 keytool，跳过指纹打印。" >&2
fi
