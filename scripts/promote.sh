#!/usr/bin/env bash

set -euo pipefail

. "$(git rev-parse --show-toplevel)/scripts/utils.sh"

ensure_mvn
set -x

mvn --batch-mode deploy

# echo "==== Preparing Release ===="
# mvn --batch-mode release:prepare

# echo "==== Release Properties: ===="
# cat release.properties

# echo "==== Executing Release ===="
# mvn --batch-mode release:perform

