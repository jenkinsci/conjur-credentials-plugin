#!/bin/bash
set -ex
source "$(git rev-parse --show-toplevel)/scripts/util.sh"

declare -x DOCKER_NETWORK='default'

echo "---- removing dev environment----"
cd "$(dev_dir)"

docker compose down -v || true
docker compose -f docker-compose.disco.yml down -v || true

if [[ -n "$(cli_cid)" ]]; then
  docker rm -f "$(cli_cid)" 2>/dev/null
fi

if [ -d "$(project_dir)/conjur-intro" ] && [ "$(ls -A $(project_dir)/conjur-intro)" ]; then
  pushd $(project_dir)/conjur-intro > /dev/null
    ./bin/dap --stop
  popd > /dev/null
fi


clean_submodules

rm -rf conjur.pem tmp access_token