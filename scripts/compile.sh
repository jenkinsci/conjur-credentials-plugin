#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_tool jdk openjdk-21-jdk
ensure_mvn

select_mvn_settings

set -x

mvn --batch-mode --settings "${MVN_SETTINGS}" compile
