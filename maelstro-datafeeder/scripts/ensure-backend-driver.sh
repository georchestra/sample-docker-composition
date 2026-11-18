#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="${task_dir}/scripts/compose.sh"

# Temporary workaround for the current maelstro-backend image: it ships with
# SQLAlchemy 2.1 and psycopg2, while its unqualified postgresql:// URL makes
# SQLAlchemy load psycopg v3. Remove this once the image either includes
# psycopg v3 or explicitly selects the psycopg2 driver.
if ! "${compose}" exec -T maelstro-backend python -c 'import psycopg' >/dev/null 2>&1; then
  echo "Installing the local psycopg v3 workaround in maelstro-backend..."
  "${compose}" exec -T -u root maelstro-backend \
    pip install 'psycopg[binary]>=3.2,<4'
  "${compose}" restart maelstro-backend
fi

for _ in {1..30}; do
  if "${compose}" exec -T maelstro-backend health_check >/dev/null 2>&1; then
    echo "Maelstro backend is healthy."
    exit 0
  fi
  sleep 2
done

echo "Maelstro backend did not become healthy." >&2
exit 1
