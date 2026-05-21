#!/usr/bin/env bash
# Fallback when WSLg GPU fails: software OpenGL (may open a blank window on some setups).
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${1:-127.0.0.1}"
PORT="${2:-9000}"

export DISPLAY="${DISPLAY:-:0}"
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe

./gradlew :lwjgl3:runWsl -PgameArgs="$HOST $PORT"
