#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

exec docker compose \
  --project-directory "${task_dir}" \
  -f "${task_dir}/compose.yaml" \
  -f "${task_dir}/compose.local.yaml" \
  "$@"
