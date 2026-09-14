#!/bin/bash
# End-to-end test for ConjurCertAuthenticator.
# Starts Docker services, runs the Java e2e test, then tears down.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Step 1: Generate certificates ==="
bash "$SCRIPT_DIR/generate-certs.sh"

echo ""
echo "=== Step 2: Start Docker services ==="
cd "$SCRIPT_DIR"
docker compose down -v 2>/dev/null || true
docker compose up -d

echo "Waiting for services to become healthy..."
for i in $(seq 1 30); do
  if docker compose ps | grep -q "(healthy)" && ! docker compose ps | grep -q "(health: starting)"; then
    echo "All services healthy."
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: Services did not become healthy in time."
    docker compose logs
    docker compose down -v
    exit 1
  fi
  sleep 1
done

echo ""
echo "=== Step 3: Verify mTLS works with curl ==="
# Test with valid client cert
HTTP_CODE=$(curl -sk \
  --cert "$SCRIPT_DIR/certs/client.pem" \
  --key "$SCRIPT_DIR/certs/client.key" \
  --cacert "$SCRIPT_DIR/certs/ca.pem" \
  -X POST \
  -w "%{http_code}" \
  -o /dev/null \
  "https://localhost:9443/authn-cert/jenkins-cert/myaccount/host%2Fjenkins%2Fcert-node/authenticate")

if [ "$HTTP_CODE" = "200" ]; then
  echo "  curl mTLS test: PASSED (HTTP $HTTP_CODE)"
else
  echo "  curl mTLS test: FAILED (HTTP $HTTP_CODE)"
  docker compose logs
  docker compose down -v
  exit 1
fi

# Test without client cert (should fail with 400 from nginx)
HTTP_CODE_NO_CERT=$(curl -sk \
  --cacert "$SCRIPT_DIR/certs/ca.pem" \
  -X POST \
  -w "%{http_code}" \
  -o /dev/null \
  "https://localhost:9443/authn-cert/jenkins-cert/myaccount/host%2Fjenkins%2Fcert-node/authenticate" 2>/dev/null || echo "000")

if [ "$HTTP_CODE_NO_CERT" != "200" ]; then
  echo "  no-cert rejection test: PASSED (HTTP $HTTP_CODE_NO_CERT)"
else
  echo "  no-cert rejection test: FAILED — should have been rejected"
  docker compose down -v
  exit 1
fi

echo ""
echo "=== Step 4: Run Java e2e test ==="
cd "$PROJECT_DIR"
mvn test -pl . -Dtest=ConjurCertAuthenticatorE2ETest \
  -DforkCount=0 \
  -De2e.cert.truststore="$SCRIPT_DIR/certs/truststore.jks" \
  -De2e.cert.keystore="$SCRIPT_DIR/certs/client.p12" \
  -De2e.cert.password=changeit \
  -De2e.conjur.url="https://localhost:9443" \
  -De2e.cert.serviceId="jenkins-cert" \
  -De2e.cert.account="myaccount" \
  -De2e.cert.hostId="host/jenkins/cert-node"

TEST_EXIT=$?

echo ""
echo "=== Step 5: Cleanup ==="
cd "$SCRIPT_DIR"
docker compose down -v

if [ $TEST_EXIT -eq 0 ]; then
  echo ""
  echo "=== ALL E2E TESTS PASSED ==="
else
  echo ""
  echo "=== E2E TESTS FAILED ==="
  exit 1
fi
