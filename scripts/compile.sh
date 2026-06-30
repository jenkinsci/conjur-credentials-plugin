#!/usr/bin/env bash

set -xeuo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_tool jdk openjdk-21-jdk
ensure_mvn
set -x

mvn --batch-mode compile
