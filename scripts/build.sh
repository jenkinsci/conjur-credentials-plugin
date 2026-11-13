#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_mvn

# Support both local executor (ARTIFACTORY_*) and InfraPool (INFRAPOOL_ARTIFACTORY_*)
: "${ARTIFACTORY_HOST:=${INFRAPOOL_ARTIFACTORY_HOST:-}}"
: "${ARTIFACTORY_USERNAME:=${INFRAPOOL_ARTIFACTORY_USERNAME:-}}"
: "${ARTIFACTORY_PASSWORD:=${INFRAPOOL_ARTIFACTORY_PASSWORD:-}}"

repo_root="$(git rev-parse --show-toplevel)"
mvn_settings="${repo_root}/mvn-settings.xml"
if [[ -z "${ARTIFACTORY_HOST:-}" ]]; then
  mvn_settings="${repo_root}/mvn-settings.xml.dist"
fi

set -x

mvn --batch-mode --settings "${mvn_settings}" hpi:hpi