#!/usr/bin/env bash
# 一键构建智学网手表版 APK
# 依赖：JDK 17+、Android SDK（platform 34 / build-tools 34+）
# SDK 位置按以下顺序自动探测：$ANDROID_HOME -> $ANDROID_SDK_ROOT -> local.properties 里的 sdk.dir
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [ -f "$ROOT/local.properties" ]; then
    export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -1)"
  else
    echo "错误：找不到 Android SDK。请设置 ANDROID_HOME，或创建 local.properties 写入 sdk.dir" >&2
    exit 2
  fi
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"

cd "$ROOT"
exec ./gradlew "$@"
