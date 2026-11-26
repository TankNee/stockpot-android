#!/usr/bin/env bash
# 在模拟器里按文字点击、截图。用法：
#   ./scripts/emu.sh tap "股票"
#   ./scripts/emu.sh shot 名字
set -uo pipefail
# Git Bash 会把 /sdcard/... 改写成 Windows 路径，关掉
export MSYS_NO_PATHCONV=1
ADB="/c/Users/tanknee/android-tools/sdk/platform-tools/adb.exe"
OUT_DIR="/c/Users/tanknee/WorkBuddy/spaces/2026_11_23_09-48-32"
XML_WIN='C:\Users\tanknee\WorkBuddy\spaces\2026_11_23_09-48-32\ui.xml'
# adb 是 Windows 程序，目标路径要 C:/... 写法
XML_ADB='C:/Users/tanknee/WorkBuddy/spaces/2026_11_23_09-48-32/ui.xml'

tap_text() {
  local needle="$1"
  local ok=0
  for _ in 1 2 3; do
    "$ADB" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    if "$ADB" pull /sdcard/ui.xml "$XML_ADB" > /dev/null 2>&1; then ok=1; break; fi
    sleep 1
  done
  [ "$ok" = 1 ] || { echo "dump 失败"; return 1; }
  local bounds
  bounds=$(python - "$needle" "$XML_WIN" <<'PY'
import re, sys
needle, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node[^>]*>', xml):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    d = re.search(r'content-desc="([^"]*)"', tag)
    label = (t.group(1) if t else '') + '|' + (d.group(1) if d else '')
    if needle in label:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            break
PY
)
  if [ -z "$bounds" ]; then echo "没找到: $needle"; return 1; fi
  echo "点 $needle @ $bounds"
  "$ADB" shell input tap $bounds
  sleep 1.4
}

shot() {
  "$ADB" exec-out screencap -p > "$OUT_DIR/emu-$1.png"
  echo "截图 emu-$1.png"
}

case "${1:-}" in
  tap) shift; tap_text "$@" ;;
  shot) shift; shot "$@" ;;
  *) echo "用法: $0 tap <文字> | shot <名字>" ;;
esac
