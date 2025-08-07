#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/util.sh"

ensure_mvn
set -x

mvn --batch-mode test
