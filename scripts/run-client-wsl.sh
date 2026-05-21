#!/usr/bin/env bash
# WSLg client launch (GPU via D3D12). Use run-client-wsl-software.sh if this shows a blank window.
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${1:-127.0.0.1}"
PORT="${2:-9000}"

export DISPLAY="${DISPLAY:-:0}"
# Required for WSLg GPU passthrough (Windows 11).
export LD_LIBRARY_PATH="/usr/lib/wsl/lib:${LD_LIBRARY_PATH:-}"
unset LIBGL_ALWAYS_SOFTWARE
unset GALLIUM_DRIVER

./gradlew :lwjgl3:run --args="$HOST $PORT"
