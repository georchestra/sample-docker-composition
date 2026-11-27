#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
required=(
  compose.yaml
  config/default.properties
  config/gateway/gateway.yaml
  config/gateway/routes.yaml
  config/datafeeder/datafeeder.env
  config/database/datafeeder-init.sql
  config/maelstro/config.yaml
  config/maelstro/transformations/rewrite-geoserver-url.xsl
  envs/common.env
  envs/database-georchestra.env
  resources/caddy/etc/Caddyfile
  secrets/slapd_password.txt
  fixtures/psc_antenne.sql
)

for path in "${required[@]}"; do
  if [[ ! -f "${task_dir}/${path}" ]]; then
    echo "Required local file is missing: ${path}." >&2
    exit 1
  fi
done

if grep -R -nE 'MAELSTRO_REPO|\.\./\.\./maelstro|/repos/maelstro' "${task_dir}" \
  --exclude-dir=.git --exclude=prepare-maelstro.sh; then
  echo "Forbidden references to an external Maelstro checkout were found." >&2
  exit 1
fi

echo "Self-contained composition verified."
