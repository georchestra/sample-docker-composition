#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="${task_dir}/scripts/compose.sh"
base_url="https://georchestra-127-0-0-1.nip.io:${CADDY_HTTPS_PORT:-8443}"

"${compose}" ps --status running

check_status() {
  local path="$1"
  local expected="$2"
  local status
  status="$(curl --noproxy '*' --insecure --silent --output /dev/null \
    --write-out '%{http_code}' "${base_url}${path}")"
  if [[ ! " ${expected} " =~ " ${status} " ]]; then
    echo "${path}: HTTP ${status}; expected one of: ${expected}" >&2
    return 1
  fi
  echo "${path}: HTTP ${status}"
}

# Gateway requires authentication for all three applications. A redirect or an
# unauthorized response proves that Caddy and the Gateway route are reachable.
check_status "/maelstro/" "302 401"
check_status "/dataset/" "302 401"
check_status "/datafeeder-backend/utils/health-check/" "302 401"
check_status "/datahub/" "200"
check_status "/metadata-editor/" "302 401"

check_authenticated_status() {
  local path="$1"
  local expected="$2"
  local status
  status="$(curl --noproxy '*' --insecure --silent --output /dev/null \
    --user testadmin:testadmin --write-out '%{http_code}' "${base_url}${path}")"
  if [[ ! " ${expected} " =~ " ${status} " ]]; then
    echo "${path} authenticated: HTTP ${status}; expected: ${expected}" >&2
    return 1
  fi
  echo "${path} authenticated: HTTP ${status}"
}

check_authenticated_status "/maelstro/" "200"
check_authenticated_status "/dataset/" "200"
check_authenticated_status "/datafeeder-backend/utils/health-check/" "200"
check_authenticated_status "/datafeeder-backend/docs" "200"
check_authenticated_status "/datahub/" "200"
check_authenticated_status "/metadata-editor/" "200"
