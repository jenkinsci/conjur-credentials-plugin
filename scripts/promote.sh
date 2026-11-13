#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_mvn

# Support both local executor (ARTIFACTORY_*) and InfraPool (INFRAPOOL_ARTIFACTORY_*)
: "${ARTIFACTORY_HOST:=${INFRAPOOL_ARTIFACTORY_HOST:-}}"
: "${ARTIFACTORY_USERNAME:=${INFRAPOOL_ARTIFACTORY_USERNAME:-}}"
: "${ARTIFACTORY_PASSWORD:=${INFRAPOOL_ARTIFACTORY_PASSWORD:-}}"

# Use --global-settings (not --settings) so Maven merges Artifactory mirrors with
# the deploy credentials in ~/.m2/settings.xml written by ensure_mvn above.
repo_root="$(git rev-parse --show-toplevel)"
mvn_global_settings=""
if [[ -n "${ARTIFACTORY_HOST:-}" ]]; then
  mvn_global_settings="--global-settings ${repo_root}/mvn-settings.xml"
fi

set -x

mvn --batch-mode ${mvn_global_settings} deploy

# echo "==== Preparing Release ===="
# mvn --batch-mode release:prepare

# echo "==== Release Properties: ===="
# cat release.properties

# echo "==== Executing Release ===="
# mvn --batch-mode release:perform

