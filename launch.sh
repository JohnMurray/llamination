#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
base_port="${SERVER_PORT:-${PORT:-8080}}"

if [[ ! "$base_port" =~ ^[0-9]+$ ]] || ((base_port < 1 || base_port > 65535)); then
    echo "Invalid base port: $base_port" >&2
    exit 1
fi

port="$base_port"
while lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; do
    if ((port == 65535)); then
        echo "No free port found at or above $base_port" >&2
        exit 1
    fi
    ((port += 1))
done

url="http://localhost:$port"

cleanup() {
    if [[ -n "${server_pid:-}" ]] && kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$repo_root/backend"
echo "Starting Llamination at $url"
SERVER_PORT="$port" ./gradlew bootRun "$@" &
server_pid=$!

until curl --noproxy '*' --silent --fail --output /dev/null "$url"; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
        wait "$server_pid"
        exit $?
    fi
    sleep 0.25
done

open "$url"
wait "$server_pid"
