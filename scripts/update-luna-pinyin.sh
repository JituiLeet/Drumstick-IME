#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/src/main/assets/rime"
curl -L https://raw.githubusercontent.com/rime/rime-luna-pinyin/master/luna_pinyin.dict.yaml -o "$ROOT/app/src/main/assets/rime/luna_pinyin.dict.yaml"
curl -L https://raw.githubusercontent.com/rime/rime-luna-pinyin/master/luna_pinyin.schema.yaml -o "$ROOT/app/src/main/assets/rime/luna_pinyin.schema.yaml"
echo "luna_pinyin updated"
