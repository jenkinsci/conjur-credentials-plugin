#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/utils.sh"

ensure_mvn
set -x

mvn --batch-mode hpi:hpi
