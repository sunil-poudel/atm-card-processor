#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Exporting OpenAPI specification to docs/openapi.json..."
cd "${ROOT_DIR}"
./backend/mvnw -f backend test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true

echo "OpenAPI spec successfully exported to docs/openapi.json"
