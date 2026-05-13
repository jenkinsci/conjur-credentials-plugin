#!/bin/bash
set -ex

declare -x DOCKER_NETWORK=''

declare -x ENTERPRISE='false'
declare -x CLOUD='false'
declare -x EDGE='false'
declare -x JENKINS_API_KEY=''
declare -x ADMIN_API_KEY=''
declare -x DEFAULT_OPTION='--create'

source "$(git rev-parse --show-toplevel)/scripts/util.sh"

function help {
  cat <<EOF
Conjur Credentials :: Dev Environment

$0 [options]

-e            Deploy Conjur Enterprise. (Default: Conjur Open Source)
-c            Deploy Conjur Cloud. (Only for CI/CD pipelines, not for local development)
-ed           Deploy Conjur Edge. (Only for CI/CD pipelines, not for local development)
-h, --help    Print usage information.
EOF
}

while true ; do
  case "$1" in
    -e ) ENTERPRISE="true" ; shift ;;
    -c )  
      if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "Cannot setup a local environment using Conjur Cloud - this option is intended for CI/CD pipelines only"
        exit 1
      fi
      CLOUD="true"
      shift ;;
    -ed )  
      if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "Cannot setup a local environment using Conjur Cloud - this option is intended for CI/CD pipelines only"
        exit 1
      fi
      EDGE="true"
      shift ;;
    -h | --help ) help && exit 0 ;;
    * )
      if [[ -z "$1" ]]; then
        break
      else
        echo "$1 is not a valid option"
        help
        exit 1
      fi ;;
  esac
done

function clean {
  cd "$(dev_dir)"
  ./stop.sh
}
trap clean ERR

function setup_conjur_resources {
  echo "---- setting up Conjur resources ----"

  policy_path="."
  if [[ "$ENTERPRISE" == "false" ]]; then
    policy_path="/policy"
  fi

  docker exec "$(cli_cid)" /bin/sh -c "
    conjur policy load -f $policy_path/root.yml -b root
    conjur variable set -i jenkins/db/password -v password
    conjur variable set -i jenkins/db/dbuserName -v db_username
    conjur variable set -i jenkins/db/dbpassWord -v db_password
    conjur variable set -i 'jenkins/db/key' -v db_key
    
    #JWT configuration
    conjur policy load -f $policy_path/authn-jwt-jenkins.yml -b root
    conjur variable set -i conjur/authn-jwt/jenkins/token-app-property -v 'jenkins_name'
    conjur variable set -i  conjur/authn-jwt/jenkins/audience -v 'cyberark-conjur'
    conjur variable set -i conjur/authn-jwt/jenkins/identity-path -v 'jenkins/projects'
    conjur variable set -i  conjur/authn-jwt/jenkins/issuer -v 'http://localhost:8080'
    conjur variable set -i conjur/authn-jwt/jenkins/jwks-uri -v 'http://jenkins:8080/jwtauth/conjur-jwk-set'
    conjur policy load -f $policy_path/authn-jwt-jenkins-host.yml -b root
    conjur policy load -f $policy_path/authn-jwt-jenkins-secrets.yml -b root
    conjur variable set -i freestyle-job-credential1  -v 'job_1'
    conjur variable set -i freestyle-job-credential2  -v 'job_2'
    conjur variable set -i pipeline-job-credential1  -v 'pipeline1'
    conjur variable set -i pipeline-job-credential2  -v 'pipeline2'
    conjur variable set -i folder-job-credential1  -v 'folder1'
    conjur variable set -i folder-job-credential2  -v 'folder2'
    conjur variable set -i multibranch-job-credential1  -v 'multibranch1'
    conjur variable set -i multibranch-job-credential2  -v 'multibranch2'

    CONJUR_AUTHENTICATORS="authn, authn-jwt/jenkins"
  "
}

function deploy_conjur_open_source() {
  echo "---- deploying Conjur Open Source ----"
  export CONJUR_DATA_KEY=$(openssl rand -base64 32)

  # start conjur server
  docker compose up -d --build conjur conjur-proxy-nginx
  set_conjur_cid "$(docker compose ps -q conjur)"
  wait_for_conjur

  # get admin credentials
  fetch_conjur_cert "$(docker compose ps -q conjur-proxy-nginx)" "cert.crt"
  ADMIN_API_KEY="$(user_api_key "$CONJUR_ACCOUNT" admin)"

  # start conjur cli and configure conjur
  docker compose up --no-deps -d conjur_cli
  set_cli_cid "$(docker compose ps -q conjur_cli)"
  setup_conjur_resources
}

function deploy_conjur_enterprise {
  echo "---- deploying Conjur Enterprise ----"

  ensure_submodules

  pushd $(project_dir)/conjur-intro
    # start conjur leader and follower
    ./bin/dap --provision-master
    ./bin/dap --provision-follower

    docker compose exec -T conjur-master-1.mycompany.local bash -c "evoke variable set CONJUR_AUTHENTICATORS authn-jwt/jenkins,authn"

    set_conjur_cid "$(docker compose ps -q conjur-master.mycompany.local)"

    # Wait for Conjur master to be ready before proceeding
    wait_for_conjur_enterprise_health "https://localhost:443"

    fetch_conjur_cert "$(conjur_cid)" "/etc/ssl/certs/ca.pem"

    # Run 'sleep infinity' in the CLI container so it stays alive
    set_cli_cid "$(docker compose run --no-deps -d -w /src/cli --entrypoint sleep client infinity)"
    # Authenticate the CLI container
    docker exec "$(cli_cid)" /bin/sh -c "
      if [ ! -e /home/cli/conjur-server.pem ]; then
        echo y | conjur init -u ${CONJUR_APPLIANCE_URL} -a ${CONJUR_ACCOUNT} --force --self-signed
      fi
      conjur login -i admin -p MySecretP@ss1
    "
    # configure conjur
    cp ../scripts/policy/* . && setup_conjur_resources
  popd
}

# deploy conjur cloud
function url_encode() {
  printf '%s' "$1" | jq -sRr @uri
}

function set_conjur_cloud_variable() {
  local variable_name="$1"
  local data="$2"
  local encoded_variable_name
  encoded_variable_name=$(url_encode "$variable_name")
  curl -w "%{http_code}" -H "Authorization: Token token=\"$INFRAPOOL_CONJUR_AUTHN_TOKEN\"" \
       -X POST --data-urlencode "${data}" "${CONJUR_APPLIANCE_URL}/secrets/conjur/variable/${encoded_variable_name}"
}

function deploy_conjur_cloud() {
  curl -w "%{http_code}" -H "Authorization: Token token=\"$INFRAPOOL_CONJUR_AUTHN_TOKEN\"" \
       -X POST -d "$(cat ./policy/root.yml)" "${CONJUR_APPLIANCE_URL}/policies/conjur/policy/data"
  
  set_conjur_cloud_variable "data/jenkins/db/password" "password"
  set_conjur_cloud_variable "data/jenkins/db/dbuserName" "db_username"
  set_conjur_cloud_variable "data/jenkins/db/dbpassWord" "db_password"
  set_conjur_cloud_variable "data/jenkins/db/key" "db_key"

}

function deploy_xml() {
  sed -e "s|{{USERNAME}}|$CONJUR_AUTHN_LOGIN|g" \
    -e "s|{{API_KEY}}|$CONJUR_API_KEY|g" \
    templates/credential.xml > tmp/credential.xml
  
  sed -e "s|{{URL}}|$CONJUR_APPLIANCE_URL|g" \
    -e "s|{{ACCOUNT}}|$CONJUR_ACCOUNT|g" \
    -e "s|{{SECRET_PATH}}|$CONJUR_SECRET_PATH|g" \
    templates/secret.xml > tmp/secret.xml
  
   sed -e "s|{{URL}}|$CONJUR_APPLIANCE_URL|g" \
      -e "s|{{ACCOUNT}}|$CONJUR_ACCOUNT|g" \
      templates/globalconjurconfiguration-apikey.xml > tmp/globalconjurconfiguration-apikey.xml

    sed -e "s|{{URL}}|$CONJUR_APPLIANCE_URL|g" \
      -e "s|{{ACCOUNT}}|$CONJUR_ACCOUNT|g" \
      templates/globalconjurconfiguration-jwt.xml > tmp/globalconjurconfiguration-jwt.xml

    # Use API Key by default
    cp tmp/globalconjurconfiguration-apikey.xml tmp/org.conjur.jenkins.configuration.GlobalConjurConfiguration.xml
}

function deploy_jobs() {
  find templates/jobs -type f -name config.xml | while IFS= read -r config; do
    # Extract job directory (parent of config.xml)
    job_dir=$(dirname "$config")

    # Get relative path from templates/jobs
    rel_path="${job_dir#templates/jobs/}"

    # Target output directory
    target_dir="tmp/jobs/$rel_path"
    mkdir -p "$target_dir"

    # Replace placeholders and save to target location
    sed -e "s|{{URL}}|$CONJUR_APPLIANCE_URL|g" \
        -e "s|{{ACCOUNT}}|$CONJUR_ACCOUNT|g" \
        "$config" > "$target_dir/config.xml"
  done
}

function wait_for_jenkins() {
  echo "[INFO] Waiting for Jenkins container to complete initialization..."
    timeout=300
    counter=0
    jenkins_container="jenkins"
    while [[ $counter -lt $timeout ]]; do
      # Get current logs and print them
      current_logs=$(docker logs $jenkins_container 2>&1 | tail -5)
      echo "[DEBUG] Recent logs from $jenkins_container:"
      echo "$current_logs"
      echo "---"

      if echo "$current_logs" | grep -q "Jenkins is fully up and running"; then
        echo "[INFO] Jenkins initialization completed successfully"
        break
      fi
      echo "[INFO] Waiting for initialization... ($counter/$timeout seconds)"
      sleep 5
      counter=$((counter + 5))
    done

    if [[ $counter -ge $timeout ]]; then
      echo "[ERROR] Timeout waiting for Jenkins initialization to complete"
      exit 1
    fi
}

function rotate_host_api_key() {
  URL=$1
  JENKINS_API_KEY=$(curl -k --request PUT --data "" \
     -H "Authorization: Token token=\"$INFRAPOOL_CONJUR_AUTHN_TOKEN\"" \
     ${URL}/authn/conjur/api_key?role=host:data%2Fjenkins%2Fjenkins-connector)
}

function main() {
  # remove previous environment
  clean
  mkdir -p tmp

  # Copy the hpi file
  test -f "$(project_dir)"/target/conjur-credentials.hpi && cp -f "$(project_dir)"/target/conjur-credentials.hpi ./tmp

  if [[ "$ENTERPRISE" == "true" ]]; then
    export CONJUR_APPLIANCE_URL='https://conjur-master.mycompany.local'
    export CONJUR_ACCOUNT='demo'
    export CONJUR_AUTHN_LOGIN='host/jenkins/jenkins-connector'
    DOCKER_NETWORK='dap_net'
    # start conjur enterprise leader and follower
    deploy_conjur_enterprise
    # rotate api key
    JENKINS_API_KEY="$(host_api_key 'jenkins/jenkins-connector')"
    export CONJUR_API_KEY="$JENKINS_API_KEY"
    export CONJUR_SECRET_PATH="jenkins/db/dbpassWord"
  elif [[ "$CLOUD" == "true" ]]; then
    #disable the debugging
    set +x
    export CONJUR_APPLIANCE_URL="$INFRAPOOL_CONJUR_APPLIANCE_URL/api"
    export CONJUR_ACCOUNT=conjur
    export CONJUR_AUTHN_LOGIN='host/data/jenkins/jenkins-connector'
    export CONJUR_SECRET_PATH="data/jenkins/db/dbpassWord"
    test -f "$(dev_dir)/cloud_ca.pem" && cp "$(dev_dir)/cloud_ca.pem" "$(dev_dir)/conjur.pem"
    DOCKER_NETWORK='default'
    #upload the policy into cloud tenant pool
    deploy_conjur_cloud
    #Enable the debugging
    set -x
    rotate_host_api_key "$CONJUR_APPLIANCE_URL"
    export CONJUR_API_KEY="$JENKINS_API_KEY"
    DEFAULT_OPTION='--test-api-key-jobs'
  elif [[ "$EDGE" == "true" ]]; then
    export CONJUR_APPLIANCE_URL="https://edge-test:8443/api"
    export CONJUR_ACCOUNT=conjur
    export CONJUR_AUTHN_LOGIN='host/data/jenkins/jenkins-connector'
    export CONJUR_SECRET_PATH="data/jenkins/db/dbpassWord"
    DOCKER_NETWORK='default'
    #disable the debugging
    set +x
    rotate_host_api_key "https://localhost:443/api"
    export CONJUR_API_KEY="$JENKINS_API_KEY"
    #Enable the debugging
    set -x
    # Download the edge certificate
    openssl s_client -connect localhost:443 -showcerts </dev/null 2>/dev/null | openssl x509 -outform PEM > "$(dev_dir)/conjur.pem"
    DEFAULT_OPTION='--test-api-key-jobs'
  else
    export CONJUR_APPLIANCE_URL='https://conjur-proxy-nginx'
    export CONJUR_ACCOUNT='cucumber'
    export CONJUR_AUTHN_LOGIN='host/jenkins/jenkins-connector'
    DOCKER_NETWORK='default'
    # start conjur server and proxy
    deploy_conjur_open_source
    # rotate api key
    JENKINS_API_KEY="$(host_api_key 'jenkins/jenkins-connector')"
    export CONJUR_API_KEY="$JENKINS_API_KEY"
    export CONJUR_SECRET_PATH="jenkins/db/dbpassWord"

  fi
 
  #deploy the Conjur configuration
  deploy_xml
  # deploy_jobs
  deploy_jobs
  #start jenkins control node
  docker compose up -d --build jenkins
  docker compose up -d bitbucket
  [[ "$EDGE" == "true" ]] && docker network connect scripts_default edge-test
  wait_for_jenkins
  docker exec jenkins bash -c "./test_job.sh --import-certs"

  echo "Restarting Jenkins to apply certificate changes..."
  docker restart jenkins
  wait_for_jenkins

  # Install plugins
  docker exec jenkins bash -c "./test_job.sh --install-plugins"

  # Restart Jenkins to load the new plugins
  echo "Restarting Jenkins to load plugins..."
  docker restart jenkins
  wait_for_jenkins

  # Phase 3: Create credentials and run tests
  docker exec jenkins bash -c "./test_job.sh $DEFAULT_OPTION"
}

main