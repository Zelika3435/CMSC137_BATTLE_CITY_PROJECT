#!/usr/bin/env bash
# WSL-friendly client launch (software OpenGL + DISPLAY=:0 if needed).
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${1:-127.0.0.1}"
PORT="${2:-9000}"

export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"
export DISPLAY="${DISPLAY:-:0}"

./gradlew :lwjgl3:runWsl -PgameArgs="$HOST $PORT"
