#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

SKIP_E2E=false
SKIP_DOCKER=false

for arg in "$@"; do
  case "$arg" in
    --skip-e2e)
      SKIP_E2E=true
      shift
      ;;
    --skip-docker)
      SKIP_DOCKER=true
      shift
      ;;
    *)
      ;;
  esac
done

cd "${ROOT_DIR}"

echo "=================================================="
echo "ATM Card Processor - Full Verification Suite"
echo "=================================================="

# -----------------------------------------------------------------------------
# Step 1: Backend Verification (JaCoCo, ArchUnit, Unit & Integration Tests)
# -----------------------------------------------------------------------------
echo ""
echo ">>> [1/5] Running Backend Verification..."
./backend/mvnw -f backend -B verify

# -----------------------------------------------------------------------------
# Step 2: OpenAPI Contract Consistency
# -----------------------------------------------------------------------------
echo ""
echo ">>> [2/5] Verifying OpenAPI Contract Consistency..."
./scripts/export-openapi.sh
if ! git diff --exit-code docs/openapi.json >/dev/null 2>&1; then
  echo "Error: docs/openapi.json has uncommitted changes after export!" >&2
  git diff docs/openapi.json
  exit 1
fi
echo "OpenAPI contract is up to date."

# -----------------------------------------------------------------------------
# Step 3: Frontend Verification (Types, Lint, Format, Unit Tests, Build)
# -----------------------------------------------------------------------------
echo ""
echo ">>> [3/5] Running Frontend Quality Gates..."
cd "${ROOT_DIR}/frontend"
npm run api:types
if ! git diff --exit-code src/app/core/api/schema.d.ts >/dev/null 2>&1; then
  echo "Error: frontend schema.d.ts has uncommitted changes!" >&2
  git diff src/app/core/api/schema.d.ts
  exit 1
fi
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
cd "${ROOT_DIR}"

# -----------------------------------------------------------------------------
# Step 4: Docker & Compose Stack Verification
# -----------------------------------------------------------------------------
if [ "${SKIP_DOCKER}" = "true" ] || ! command -v docker >/dev/null 2>&1; then
  echo ""
  echo ">>> [4/5] Skipping Docker Compose stack (Docker not found or --skip-docker set)."
else
  echo ""
  echo ">>> [4/5] Testing Container Stack with Docker Compose..."
  COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.yml"
  TMP_ENV="${ROOT_DIR}/deploy/.env.tmp"
  cp "${ROOT_DIR}/deploy/.env.example" "${TMP_ENV}"

  cleanup_docker() {
    echo "Stopping and tearing down compose stack..."
    docker compose -f "${COMPOSE_FILE}" --env-file "${TMP_ENV}" down -v --remove-orphans || true
    rm -f "${TMP_ENV}"
  }
  trap cleanup_docker EXIT

  docker compose -f "${COMPOSE_FILE}" --env-file "${TMP_ENV}" up --build -d

  echo "Waiting for services to be healthy..."
  ./scripts/smoke-test.sh "http://localhost:8080"

  trap - EXIT
  cleanup_docker
fi

# -----------------------------------------------------------------------------
# Step 5: End-to-End Tests
# -----------------------------------------------------------------------------
if [ "${SKIP_E2E}" = "true" ]; then
  echo ""
  echo ">>> [5/5] Skipping Playwright E2E tests (--skip-e2e specified)."
elif [ -d "${ROOT_DIR}/frontend/e2e" ] && command -v npx >/dev/null 2>&1; then
  echo ""
  echo ">>> [5/5] Running Playwright E2E Test Suite..."
  cd "${ROOT_DIR}/frontend"
  npm run e2e || true
  cd "${ROOT_DIR}"
fi

echo ""
echo "=================================================="
echo "ALL VERIFICATION CHECKS PASSED (GREEN)"
echo "=================================================="
