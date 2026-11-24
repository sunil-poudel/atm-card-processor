#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ADMIN_EMAIL="${2:-admin@minicard.local}"
ADMIN_PASSWORD="${3:-AdminPassword123!}"

echo "=================================================="
echo "ATM Card Processor - Smoke Test Suite"
echo "Target: ${BASE_URL}"
echo "=================================================="

# Helper: check dependencies
if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required for smoke-test.sh" >&2
  exit 1
fi

echo "[1/8] Waiting for service readiness..."
MAX_ATTEMPTS=30
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health/readiness" || true)
  if [ "$STATUS" = "200" ]; then
    echo "Service is UP and ready."
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep 1
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
  echo "Timeout waiting for service readiness at ${BASE_URL}/actuator/health/readiness" >&2
  exit 1
fi

echo "[2/8] Authenticating bootstrap admin (${ADMIN_EMAIL})..."
LOGIN_RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")

ACCESS_TOKEN=$(echo "${LOGIN_RESP}" | jq -r '.accessToken')
if [ -z "${ACCESS_TOKEN}" ] || [ "${ACCESS_TOKEN}" = "null" ]; then
  echo "Authentication failed. Response: ${LOGIN_RESP}" >&2
  exit 1
fi
echo "Authenticated successfully."

AUTH_HEADER="Authorization: Bearer ${ACCESS_TOKEN}"

echo "[3/8] Issuing a new card..."
ISSUE_RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/cards" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d '{
    "holderName": "Smoke Test Cardholder",
    "currency": "NPR",
    "validityYears": 3,
    "perTransactionLimitMinor": 500000,
    "dailyLimitMinor": 2000000,
    "initialBalanceMinor": 1000000,
    "blockedMccs": ["7995"]
  }')

CARD_ID=$(echo "${ISSUE_RESP}" | jq -r '.id')
CARD_PAN=$(echo "${ISSUE_RESP}" | jq -r '.pan')

if [ -z "${CARD_ID}" ] || [ "${CARD_ID}" = "null" ] || [ -z "${CARD_PAN}" ] || [ "${CARD_PAN}" = "null" ]; then
  echo "Card issuance failed or full PAN was not exposed. Response: ${ISSUE_RESP}" >&2
  exit 1
fi
echo "Card issued: ID=${CARD_ID}, PAN=${CARD_PAN:0:6}******${CARD_PAN: -4}"

echo "[4/8] Activating card..."
curl -s -f -X POST "${BASE_URL}/api/v1/cards/${CARD_ID}/activate" \
  -H "${AUTH_HEADER}" >/dev/null
echo "Card activated."

echo "[5/8] Authorizing approved payment (NPR 1,500.00)..."
TX_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || openssl rand -hex 16)
AUTH_HTTP_RESP=$(curl -s -i -X POST "${BASE_URL}/api/v1/authorize" \
  -H "${AUTH_HEADER}" \
  -H "Idempotency-Key: ${TX_KEY}" \
  -H "Content-Type: application/json" \
  -d "{
    \"cardId\": \"${CARD_ID}\",
    \"amountMinor\": 150000,
    \"currency\": \"NPR\",
    \"merchantName\": \"Smoke Test Supermarket\",
    \"mcc\": \"5411\",
    \"reference\": \"SMK-REF-001\"
  }")

AUTH_BODY=$(echo "${AUTH_HTTP_RESP}" | sed -n '/^\r\{0,1\}$/,$p')
AUTH_STATUS=$(echo "${AUTH_BODY}" | jq -r '.status')
AUTH_CODE=$(echo "${AUTH_BODY}" | jq -r '.responseCode')
TX_ID=$(echo "${AUTH_BODY}" | jq -r '.transactionId')

if [ "${AUTH_STATUS}" != "APPROVED" ] || [ "${AUTH_CODE}" != "00" ]; then
  echo "Authorization did not approve as expected! Body: ${AUTH_BODY}" >&2
  exit 1
fi
echo "Payment approved: TX_ID=${TX_ID}, Status=${AUTH_STATUS}, Code=${AUTH_CODE}"

echo "[6/8] Testing idempotent replay with identical key..."
REPLAY_RESP=$(curl -s -i -X POST "${BASE_URL}/api/v1/authorize" \
  -H "${AUTH_HEADER}" \
  -H "Idempotency-Key: ${TX_KEY}" \
  -H "Content-Type: application/json" \
  -d "{
    \"cardId\": \"${CARD_ID}\",
    \"amountMinor\": 150000,
    \"currency\": \"NPR\",
    \"merchantName\": \"Smoke Test Supermarket\",
    \"mcc\": \"5411\",
    \"reference\": \"SMK-REF-001\"
  }")

REPLAY_BODY=$(echo "${REPLAY_RESP}" | sed -n '/^\r\{0,1\}$/,$p')
REPLAY_TX_ID=$(echo "${REPLAY_BODY}" | jq -r '.transactionId')
REPLAY_HEADER=$(echo "${REPLAY_RESP}" | grep -i "idempotent-replayed" || true)

if [ "${REPLAY_TX_ID}" != "${TX_ID}" ]; then
  echo "Replay returned different transaction ID: ${REPLAY_TX_ID} != ${TX_ID}" >&2
  exit 1
fi
if [ -z "${REPLAY_HEADER}" ]; then
  echo "Missing Idempotent-Replayed header in replay response!" >&2
  exit 1
fi
echo "Idempotent replay verified: returned original transaction ${TX_ID} with Idempotent-Replayed header."

echo "[7/8] Testing over-limit decline (NPR 6,000.00 > Limit 5,000.00)..."
DECLINE_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || openssl rand -hex 16)
DECLINE_RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/authorize" \
  -H "${AUTH_HEADER}" \
  -H "Idempotency-Key: ${DECLINE_KEY}" \
  -H "Content-Type: application/json" \
  -d "{
    \"cardId\": \"${CARD_ID}\",
    \"amountMinor\": 600000,
    \"currency\": \"NPR\",
    \"merchantName\": \"Smoke Test Luxury Goods\",
    \"mcc\": \"5311\",
    \"reference\": \"SMK-DECL-001\"
  }")

DECLINE_STATUS=$(echo "${DECLINE_RESP}" | jq -r '.status')
DECLINE_CODE=$(echo "${DECLINE_RESP}" | jq -r '.responseCode')

if [ "${DECLINE_STATUS}" != "DECLINED" ] || [ "${DECLINE_CODE}" != "61" ]; then
  echo "Over-limit authorization did not decline with code 61! Response: ${DECLINE_RESP}" >&2
  exit 1
fi
echo "Over-limit correctly declined: Status=${DECLINE_STATUS}, Code=${DECLINE_CODE}"

echo "[8/8] Fetching operational report and audit events..."
TODAY=$(date -u +"%Y-%m-%d")
REPORT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "${AUTH_HEADER}" "${BASE_URL}/api/v1/reports/daily?date=${TODAY}")
if [ "${REPORT_STATUS}" != "200" ]; then
  echo "Failed to fetch daily report (HTTP ${REPORT_STATUS})" >&2
  exit 1
fi

AUDIT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "${AUTH_HEADER}" "${BASE_URL}/api/v1/audit-events?size=5")
if [ "${AUDIT_STATUS}" != "200" ]; then
  echo "Failed to query audit events (HTTP ${AUDIT_STATUS})" >&2
  exit 1
fi

echo "=================================================="
echo "SMOKE TEST RESULT: ALL CHECKS PASSED (GREEN)"
echo "=================================================="
