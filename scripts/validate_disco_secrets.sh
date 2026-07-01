#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(git rev-parse --show-toplevel)"
cd "${repo_dir}"

jenkins_container="${JENKINS_CONTAINER_NAME:-jenkins}"
export DISCO_DOCKER_NETWORK="${DISCO_DOCKER_NETWORK:-container:${jenkins_container}}"
export DISCO_E2E_JENKINS_URL="${DISCO_E2E_JENKINS_URL:-http://localhost:8080}"
export JENKINS_URL="${DISCO_E2E_JENKINS_URL}"
export JENKINS_ADMIN_ID="${JENKINS_ADMIN_ID:-admin}"
export JENKINS_ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-}"

if [[ -z "${JENKINS_ADMIN_PASSWORD}" ]]; then
  echo "[ERROR] JENKINS_ADMIN_PASSWORD is required for DisCo E2E validation. Provide it via Summon or the environment."
  exit 1
fi

if [[ -n "${DISCO_E2E_JENKINS_CLI_AUTH:-}" ]]; then
  export JENKINS_CLI_AUTH="${DISCO_E2E_JENKINS_CLI_AUTH}"
  echo "[INFO] Using Jenkins CLI auth from DISCO_E2E_JENKINS_CLI_AUTH"
else
  export JENKINS_CLI_AUTH="${JENKINS_ADMIN_ID}:${JENKINS_ADMIN_PASSWORD}"
  echo "[INFO] Using DisCo E2E admin user '${JENKINS_ADMIN_ID}' for Jenkins CLI auth"
fi

docker_args=()
if [[ -n "${DISCO_GRAPHQL_DOCKER_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  docker_args=(${DISCO_GRAPHQL_DOCKER_ARGS})
fi
if [[ " ${docker_args[*]} " != *" --network "* ]]; then
  docker_args+=(--network "${DISCO_DOCKER_NETWORK}")
fi

echo "[INFO] Jenkins URL for E2E export trigger: ${JENKINS_URL}"
echo "[INFO] Override with DISCO_E2E_JENKINS_URL if the DisCo Jenkins endpoint is not http://localhost:8080 inside the test container"
echo "[INFO] Docker network mode for E2E test container: ${DISCO_DOCKER_NETWORK}"
if [[ -n "${JENKINS_CLI_AUTH:-}" ]]; then
  echo "[INFO] Jenkins CLI authentication is configured"
else
  echo "[INFO] Jenkins CLI authentication is not configured; this only works when Jenkins permits anonymous CLI access"
fi

mkdir -p "${HOME}/.m2/repository"

docker run --rm \
  "${docker_args[@]}" \
  -v "${repo_dir}:/workspace" \
  -v "${HOME}/.m2/repository:/root/.m2/repository" \
  -w /workspace \
  -e DISCO_E2E_JENKINS_URL \
  -e JENKINS_URL \
  -e JENKINS_CLI_AUTH \
  -e DISCO_TENANT_ID \
  -e DISCO_SUBDOMAIN \
  -e DISCO_IDENTITY_URL \
  -e DISCO_GRAPHQL_URL \
  -e DISCO_USERNAME \
  -e DISCO_PASSWORD \
  -e DISCO_E2E_RUN_ID \
  -e DISCO_GRAPHQL_WAIT_TIMEOUT_SECONDS \
  -e DISCO_GRAPHQL_WAIT_INTERVAL_SECONDS \
  -e JENKINS_CLI_TIMEOUT_SECONDS \
  "${DISCO_GRAPHQL_TEST_IMAGE:-maven:3.9.9-eclipse-temurin-21}" \
  mvn test \
    -DDISCO_GRAPHQL_RUN=true \
    -Dtest=org.conjur.jenkins.disco.e2e.tests.Disco* \
    -DfailIfNoTests=false