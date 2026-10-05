#!/usr/bin/env bash
# Builds the jar if needed and starts the app.
#   --port=NNNN   listen on this port (default 8020, or $TIMESHEETS_PORT)
#   --data=path   use a different timesheet file
#   --rebuild     force a rebuild of the jar
set -euo pipefail
cd "$(dirname "$0")"

PORT="${TIMESHEETS_PORT:-8020}"
DATA_ARG=""
for arg in "$@"; do
  case "$arg" in
    --port=*) PORT="${arg#--port=}" ;;
    --data=*) DATA_ARG="--timesheets.data-file=${arg#--data=}" ;;
    --rebuild) rm -f target/timesheets.jar ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [ ! -f target/timesheets.jar ]; then
  echo "Building (first run downloads Node and dependencies, takes a couple of minutes)…"
  mvn -q package -DskipTests
fi

exec java -jar target/timesheets.jar --server.port="$PORT" $DATA_ARG
