#!/bin/bash

function dev_dir {
  repo="$(git rev-parse --show-superproject-working-tree)"
  if [[ "$repo" == "" ]]; then
    repo="$(git rev-parse --show-toplevel)"
  fi

  echo "$repo/scripts"
}

function project_dir {
  repo="$(git rev-parse --show-superproject-working-tree)"
  if [[ "$repo" == "" ]]; then
    repo="$(git rev-parse --show-toplevel)"
  fi
  echo "$repo"
}

function compose_major_version {
  docker compose version --short | cut -d "." -f 1
}

function set_cli_cid {
  echo "$1" > "$(dev_dir)/tmp/cli_cid"
}

function cli_cid {
  cat "$(dev_dir)/tmp/cli_cid"
}

function set_conjur_cid {
  echo "$1" > "$(dev_dir)/tmp/conjur_cid"
}

function conjur_cid {
  cat "$(dev_dir)/tmp/conjur_cid"
}

function wait_for_conjur {
  docker exec "$(conjur_cid)" conjurctl wait -p 3000
}

# Waits for Conjur Enterprise to be ready by polling the health endpoint
# Reference: https://docs.cyberark.com/secrets-manager-sh/13.5/en/content/developer/conjur_api_health_check.htm
function wait_for_conjur_enterprise_health {
  local max_attempts=5
  local attempt=1
  local url="$1"

  echo "Waiting for Conjur to be ready at ${url}..."

  while [[ $attempt -le $max_attempts ]]; do
    # Check Conjur health endpoint
    if curl -sk "${url}/health" | grep -q "ok"; then
      echo "Conjur is ready!"
      return 0
    fi
    echo "Attempt $attempt/$max_attempts: Conjur not ready yet, waiting..."
    sleep 5
    attempt=$((attempt + 1))
  done

  echo "ERROR: Conjur did not become ready in time"
  return 1
}

function fetch_conjur_cert {
  local cid="$1"
  local cert_path="$2"

  (docker exec "$cid" cat "$cert_path") > "$(dev_dir)/conjur.pem"
}

function user_api_key {
  local account="$1"
  local id="$2"
  docker exec "$(conjur_cid)" conjurctl role retrieve-key "$account:user:$id"
}

function refresh_access_token {
  local id="$1"
  local api_key="$2"
  docker exec "$(cli_cid)" /bin/sh -c "
    export CONJUR_AUTHN_LOGIN=$id
    export CONJUR_AUTHN_API_KEY=$api_key
    conjur authenticate
  " > "$(dev_dir)/access_token"
}

function rotate_api_key {
  docker exec "$(cli_cid)" conjur user rotate-api-key
}

function host_api_key {
  local id="$1"
  docker exec "$(cli_cid)" conjur host rotate-api-key -i "$id"
}


function ensure_submodules {
  if [ -d "$(project_dir)/conjur-intro" ]; then
    git submodule init -- "$(project_dir)/conjur-intro"
    git submodule update --remote -- "$(project_dir)/conjur-intro"
  fi
}

function clean_submodules {
  if [ -d "$(project_dir)/conjur-intro" ]; then
    pushd "$(project_dir)/conjur-intro"
      git clean -df
    popd
  fi
}

function ensure_tool(){
  cmd="$1"
  package="$2"
  if command -v "${cmd}" >/dev/null; then
    return
  fi
  if command -v apt-get; then
    sudo apt-get update; sudo apt-get -y install ${package}
  else
    echo "Unable to install ${cmd}: apt-get not detected. If you're on a Mac, use 'brew install ${package}'"
    return 1
  fi
}

function ensure_mvn(){
  # skip if already configured
  command -v mvn && grep -q conjur_jenkins ~/.m2/settings.xml 2>/dev/null && return

  # Retrieve the latest mvn release version
  mvn_latest=$(curl -s \
    https://repo1.maven.org/maven2/org/apache/maven/maven/maven-metadata.xml | \
    grep '<version>[0-9]\+\.[0-9]\+\.[0-9]\+</version>' | \
    tail -1 | \
    cut -d ">" -f2 | \
    cut -d "<" -f1 | \
    tr -d '\r\n')
  echo "Latest detected maven version: ${mvn_latest}"
  # Install mvn cli
  mvn_version="${1:-${mvn_latest}}"
  echo "Installing maven version: ${mvn_version}"
  curl "https://dlcdn.apache.org/maven/maven-3/${mvn_version}/binaries/apache-maven-${mvn_version}-bin.tar.gz" > maven.tgz
  tar xzf maven.tgz
  export PATH="${PATH}:${PWD}/apache-maven-${mvn_version}/bin"

  # Get mvn creds from conjurops
  mkdir -p ~/.m2/
  if [[ -f ~/.m2/settings.xml ]]; then
    echo "Warning ~/.m2/settings.xml already exists and will be overwritten"
  fi
  /usr/local/lib/summon/summon-conjur ci/upstream-jenkins/maven-config |base64 -d > ~/.m2/settings.xml

}

function ensure_xmlstarlet(){
  ensure_tool xmlstarlet xmlstarlet
}