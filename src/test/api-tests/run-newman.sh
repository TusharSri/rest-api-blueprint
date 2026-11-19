#!/usr/bin/env bash
# Run a black-box API suite against a running application.
#   ./run-newman.sh smoke                 # against http://localhost:8080 (environments/local)
#   ./run-newman.sh regression ci         # environments/ci
#   BASE_URL=https://staging.example.com ./run-newman.sh smoke staging
# Needs Node.js (newman is fetched on demand through npx).
set -euo pipefail
cd "$(dirname "$0")"

SUITE="${1:?usage: $0 smoke|sanity|regression [environment]}"
ENVIRONMENT="${2:-local}"
EXTRA=()
[ "$SUITE" = "smoke" ] && EXTRA+=(--bail)                       # smoke fails fast
[ -n "${BASE_URL:-}" ] && EXTRA+=(--env-var "baseUrl=${BASE_URL}")

mkdir -p reports
npx --yes newman run "${SUITE}/${SUITE}.postman_collection.json" \
  -e "environments/${ENVIRONMENT}.postman_environment.json" \
  --timeout-request 15000 \
  --reporters cli,junit --reporter-junit-export "reports/${SUITE}-results.xml" \
  "${EXTRA[@]}"
