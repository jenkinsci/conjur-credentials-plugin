#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_mvn

select_mvn_settings

set -x

mvn --batch-mode --global-settings "${MVN_SETTINGS}" deploy

# echo "==== Preparing Release ===="
# mvn --batch-mode release:prepare

# echo "==== Release Properties: ===="
# cat release.properties

# echo "==== Executing Release ===="
# mvn --batch-mode release:perform

