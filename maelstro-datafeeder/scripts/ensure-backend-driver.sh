#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="${task_dir}/scripts/compose.sh"

wait_for_backend() {
  for _ in {1..30}; do
    if "${compose}" exec -T maelstro-backend health_check >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# Maelstro PR #202 explicitly selects the installed psycopg2 driver. Keep this
# fallback for older published images whose unqualified postgresql:// URL makes
# SQLAlchemy 2.1 try to load psycopg v3.
if wait_for_backend; then
  echo "Maelstro backend is healthy; no database driver workaround is required."
  exit 0
fi

if ! "${compose}" logs --no-color maelstro-backend 2>&1 \
  | grep -qE "No module named ['\"]psycopg['\"]"; then
  echo "Maelstro backend is unhealthy for a reason unrelated to the legacy psycopg issue." >&2
  exit 1
fi

if ! "${compose}" exec -T maelstro-backend python -c 'import psycopg' >/dev/null 2>&1; then
  echo "Installing the psycopg v3 fallback for an older Maelstro image..."
  "${compose}" exec -T -u root maelstro-backend \
    pip install 'psycopg[binary]>=3.2,<4'
  "${compose}" restart maelstro-backend
fi

for _ in {1..30}; do
  if "${compose}" exec -T maelstro-backend health_check >/dev/null 2>&1; then
    echo "Maelstro backend is healthy after applying the database driver fallback."
    exit 0
  fi
  sleep 2
done

echo "Maelstro backend did not become healthy." >&2
exit 1
