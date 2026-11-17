#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="${task_dir}/scripts/compose.sh"

if ! "${compose}" exec -T maelstro-back python -c 'import psycopg' >/dev/null 2>&1; then
  echo "Installing the local psycopg v3 workaround in maelstro-back..."
  "${compose}" exec -T -u root maelstro-back \
    pip install 'psycopg[binary]>=3.2,<4'
  "${compose}" restart maelstro-back
fi

for _ in {1..30}; do
  if "${compose}" exec -T maelstro-back health_check >/dev/null 2>&1; then
    echo "Maelstro backend is healthy."
    exit 0
  fi
  sleep 2
done

echo "Maelstro backend did not become healthy." >&2
exit 1
