#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

HOST="${1:-127.0.0.1}"
PORT="${2:-9000}"

export DISPLAY="${DISPLAY:-:0}"

./gradlew :lwjgl3:run --args="$HOST $PORT"
