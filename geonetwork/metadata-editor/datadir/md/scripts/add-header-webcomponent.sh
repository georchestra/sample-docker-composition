#!/bin/sh

# To be properl executed by datahub initialization need the +x flag 
# so you need to add it with chmod and commit/push it

DATAHUB_DIR=${1:-/usr/share/nginx/html/metadata-editor}
SNIPPET="<style>body {display: flex;flex-direction: column} body md-editor-root, .h-screen {height: calc(100vh - 90px);}</style><script src='https://cdn.jsdelivr.net/gh/georchestra/header@dist/header.js'></script><geor-header active-app='metadata-editor' style='height:90px'></geor-header>"

if grep -q "${SNIPPET}" "${DATAHUB_DIR}/index.html"; then
  echo "[INFO] geOrchestra: header already present."
  exit 0
fi

echo "[INFO] geOrchestra: adding header in the main page..."
sed -i "s#<body>#<body>${SNIPPET}#" ${DATAHUB_DIR}/index.html
