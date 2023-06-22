#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/utils.sh"

ensure_mvn
set -x

git config --global user.email "conj_ops@cyberark.com"
git config --global user.name "CyberArk Conjur Jenkins"

echo "==== Preparing Release ===="
mvn --batch-mode release:prepare

echo "==== Release Properties: ===="
cat release.properties

echo "==== Executing Release ===="
mvn --batch-mode release:perform
