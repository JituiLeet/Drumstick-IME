#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/rime"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Fetching open-source Rime prelude and luna-pinyin data..."
git clone --depth 1 https://github.com/rime/rime-prelude.git "$TMP/prelude"
git clone --depth 1 https://github.com/rime/rime-luna-pinyin.git "$TMP/luna"
cp -f "$TMP/prelude"/*.yaml "$OUT/" 2>/dev/null || true
cp -f "$TMP/luna"/*.yaml "$OUT/" 2>/dev/null || true
cp -f "$TMP/luna"/*.txt "$OUT/" 2>/dev/null || true
echo "Rime data copied to $OUT"
