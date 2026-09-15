#!/usr/bin/env bash

# Coordinates development infrastructure while keeping the application process on the host.
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose=(docker compose --project-directory "$repo_root" --file "$repo_root/compose.yaml")
command="${1:-run}"

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "Docker is required. Install Docker Desktop or another Docker Compose provider." >&2
        exit 1
    fi
}

start_infrastructure() {
    "${compose[@]}" up --detach --wait
}

require_docker
case "$command" in
    run)
        start_infrastructure
        exec "$repo_root/launch.sh" "${@:2}"
        ;;
    up)
        start_infrastructure
        ;;
    down)
        "${compose[@]}" down
        ;;
    logs)
        "${compose[@]}" logs --follow "${@:2}"
        ;;
    reset-data)
        echo "This removes all local Llamination database data." >&2
        "${compose[@]}" down --volumes
        start_infrastructure
        ;;
    *)
        echo "Usage: $0 [run|up|down|logs|reset-data]" >&2
        exit 2
        ;;
esac
