#!/usr/bin/env bash
set -euo pipefail

task_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose="${task_dir}/scripts/compose.sh"

for _ in {1..30}; do
  if curl --silent --output /dev/null http://127.0.0.1:8088/; then
    break
  fi
  sleep 2
done

"${compose}" run --rm \
  -v "${task_dir}/scripts/init-platform.py:/scripts/init-platform.py:ro" \
  check python3 /scripts/init-platform.py

"${compose}" exec -T ldap sh -c \
  "printf '%s\\n' 'dn: uid=tmaelstro,ou=users,dc=georchestra,dc=org' 'changetype: modify' 'replace: userPassword' 'userPassword: tmaelstro' '' | ldapmodify -H ldap://localhost:389 -D cn=admin,dc=georchestra,dc=org -w secret"

"${compose}" run --rm \
  -v "${task_dir}/scripts/create-maelstro-gs-resources.py:/scripts/create-maelstro-gs-resources.py:ro" \
  check python3 /scripts/create-maelstro-gs-resources.py

if ! "${compose}" exec -T postgis psql -U georchestra -d datafeeder -Atc \
  "select to_regclass('psc.antennes') is not null" | grep -qx t; then
  "${compose}" exec -T postgis psql -v ON_ERROR_STOP=0 -U georchestra -d datafeeder \
    < "${task_dir}/fixtures/psc_antenne.sql"
else
  echo "The psc.antennes table already exists; skipping import."
fi

"${compose}" exec -T ldap ldapwhoami -x -H ldap://localhost:389 \
  -D uid=tmaelstro,ou=users,dc=georchestra,dc=org -w tmaelstro
"${compose}" exec -T postgis psql -U georchestra -d datafeeder -Atc \
  'select count(*) from psc.antennes;'

echo "Local bootstrap completed."
