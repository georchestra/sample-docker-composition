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

# Initialize the field used by GeoNetwork's default sort so an empty catalog
# can load without displaying a search error before its first record is added.
"${compose}" exec -T elasticsearch sh -c '
  curl --silent --show-error --fail \
    --request PUT http://localhost:9200/gn-records \
    --header "Content-Type: application/json" \
    -u "elastic:$ELASTIC_PASSWORD" -v \
    --data '\''{"mappings":{"properties":{"createDate":{"type":"date"}}}}'\''
 ' >/dev/null

"${compose}" exec -T elasticsearch sh -c '
    curl --silent --show-error --fail \
   --request PUT http://localhost:9200/gn-records/_mapping \
   --header "Content-Type: application/json" \
   -u "elastic:$ELASTIC_PASSWORD" -v \
   --data '\''{"properties":{"createDate":{"type":"date"}}}'\''
 '   >/dev/null


if ! "${compose}" exec -T datadb psql -U postgres -d postgres -Atc \
  "select to_regclass('psc.antennes') is not null" | grep -qx t; then
  "${compose}" exec -T datadb psql -v ON_ERROR_STOP=0 -U postgres -d postgres \
    < "${task_dir}/fixtures/psc_antenne.sql"
else
  echo "The psc.antennes table already exists; skipping import."
fi

"${compose}" exec -T ldap ldapwhoami -x -H ldap://localhost:389 \
  -D uid=tmaelstro,ou=users,dc=georchestra,dc=org -w tmaelstro
"${compose}" exec -T datadb psql -U postgres -d postgres -Atc \
  'select count(*) from psc.antennes;'

echo "Local bootstrap completed."
