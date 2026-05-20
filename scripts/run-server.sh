#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${1:-9000}"
./gradlew :server:run --args="$PORT"
