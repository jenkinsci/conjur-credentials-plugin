#!/bin/bash

declare -x TOTAL_SUCCESS=0
declare -x TOTAL_FAILURE=0

JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
if [[ -z "${JENKINS_CLI_AUTH:-}" && -n "${JENKINS_ADMIN_ID:-}" && -n "${JENKINS_ADMIN_PASSWORD:-}" ]]; then
    JENKINS_CLI_AUTH="${JENKINS_ADMIN_ID}:${JENKINS_ADMIN_PASSWORD}"
fi

jenkins_cli() {
    local command=(java -jar /tmp/jenkins-cli.jar -s "${JENKINS_URL}")
    if [[ -n "${JENKINS_CLI_AUTH:-}" ]]; then
        command+=(-auth "${JENKINS_CLI_AUTH}")
    fi
    "${command[@]}" "$@"
}

# Function to check if the command was successful
check_command_success() {
    if [ $? -ne 0 ]; then
        echo "Error: $1 failed."
        exit 1
    else
        echo "$1 completed successfully."
    fi
}

# Function to download Jenkins CLI
download_jenkins_cli() {
    echo "Downloading Jenkins CLI..."
    wget http://localhost:8080/jnlpJars/jenkins-cli.jar -O /tmp/jenkins-cli.jar
    check_command_success "Downloading Jenkins CLI"

    # Verify if the file was downloaded successfully
    if [ ! -f /tmp/jenkins-cli.jar ]; then
        echo "Error: Jenkins CLI jar does not exist after download."
        exit 1
    fi
}

# Function to install Jenkins plugins
install_jenkins_plugins() {
    echo "Installing required Jenkins plugins..."
    jenkins_cli install-plugin $(cat /tmp/plugins) -deploy
    check_command_success "Installing required Jenkins plugins"
    if [ ! -f /tmp/conjur-credentials.hpi ]; then
        echo "Error: local Conjur plugin artifact /tmp/conjur-credentials.hpi was not copied into the Jenkins container."
        exit 1
    fi
    echo "Installing local Conjur plugin artifact from /tmp/conjur-credentials.hpi"
    jenkins_cli install-plugin file:///tmp/conjur-credentials.hpi
    check_command_success "Installing local Conjur plugin artifact"
}

# Function to create global credentials
create_credentials() {
    echo "Create the global credentials..."
    jenkins_cli create-credentials-by-xml system::system::jenkins _ < /tmp/credential.xml
    check_command_success "Creating credentials"
    jenkins_cli create-credentials-by-xml system::system::jenkins _ < /tmp/secret.xml
    check_command_success "Creating secrets"
    jenkins_cli create-credentials-by-xml system::system::jenkins _ < /tmp/add-bitbucket-token.xml
    check_command_success "Adding bitbucket personal access token"
    jenkins_cli create-credentials-by-xml system::system::jenkins _ < /tmp/bitbucket-cred.xml
    check_command_success "Adding bitbucket username credentials"
    jenkins_cli groovy = < /tmp/bitbucket-config.groovy
    check_command_success "Adding bitbucket server instance"

}

# Function to create disco credentials
create_disco_credentials() {
    echo "Creating CyberArk DisCo Discovery credentials..."
    jenkins_cli help create-credentials-by-xml >/dev/null
    check_command_success "Verifying credentials CLI command"
    jenkins_cli create-credentials-by-xml system::system::jenkins _ < /tmp/disco-credential.xml
    check_command_success "Adding CyberArk DisCo Discovery credential"
}

# Function to import SSL certificate into Java keystore
import_ssl_certificate() {
    echo "Importing the SSL certificate into the Java keystore..."
    openssl s_client -showcerts -connect ftp.halifax.rwth-aachen.de:443 </dev/null 2>/dev/null | openssl x509 -outform PEM > /var/jenkins_home/halifax.crt
    keytool -import -noprompt -alias halifax -file /var/jenkins_home/halifax.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
    openssl s_client -showcerts -connect updates.jenkins.io:443 </dev/null 2>/dev/null | openssl x509 -outform PEM > /var/jenkins_home/updates-jenkins.crt
    keytool -import -noprompt -alias updates-jenkins -file /var/jenkins_home/updates-jenkins.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
    if [ -f /var/jenkins_home/conjur.pem ]; then
        keytool -import -noprompt -file /var/jenkins_home/conjur.pem -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
        check_command_success "Importing Conjur certificate"
    else
        echo "Skipping Conjur certificate import: /var/jenkins_home/conjur.pem not found"
    fi
    check_command_success "Importing SSL certificate"
}

# Function to trigger Jenkins build and get the build status
trigger_jenkins_build() {
      local job_name=$1

    echo "Triggering the Jenkins build..."
     # 1 = pipeline, 2 = freestyle, 3 = multibranch
     if [ "$2" -eq 3 ]; then
            # "Multibranch job detected. Reloading job config"
            # There is no direct build for multibranch
            jenkins_cli reload-job "$1"
            check_command_success "Reloading multibranch job"

            echo "Waiting 10 seconds for branches to be discovered.."
            sleep 10
            branch_jobs=$(echo "import jenkins.model.*; Jenkins.instance.getItem('$1')?.getItems()?.each { println it.name } " | jenkins_cli groovy = )
            for branch_job in $branch_jobs; do
               full_job_name="$1/$branch_job"
               echo "Triggering branch build: $full_job_name"
               BUILD_STATUS=$(jenkins_cli build "$full_job_name" -f )
                # Validate the build trigger
               if [ -z "$BUILD_STATUS" ]; then
                       echo "Error: Build trigger failed."
                       exit 1
               else
                       echo "Build triggered successfully."
               fi
               check_build_status
            done
     else
            BUILD_STATUS=$(jenkins_cli build "$1" -f)
             # Validate the build trigger
             if [ -z "$BUILD_STATUS" ]; then
                    echo "Error: Build trigger failed."
                    exit 1
             else
                    echo "Build triggered successfully."
             fi
             check_build_status
     fi

      if [[ "$BUILD_STATUS" == *"SUCCESS"* ]]; then
          return 0
      else
          return 1
      fi

}

check_build_status() {
    if [[ "$BUILD_STATUS" == *"SUCCESS"* ]]; then
        echo "Build succeeded: $BUILD_STATUS"
    else
        echo "Build failed with status: $BUILD_STATUS"
        #exit 1
    fi
}

configure_auth_method() {
    local job_name=$1
    echo "Configuring authentication method for job: $1"

    if [[ $job_name == *"jwt"* ]]; then
        cp /tmp/globalconjurconfiguration-jwt.xml /var/jenkins_home/org.conjur.jenkins.configuration.GlobalConjurConfiguration.xml
    else
        cp /tmp/globalconjurconfiguration-apikey.xml /var/jenkins_home/org.conjur.jenkins.configuration.GlobalConjurConfiguration.xml
    fi

    echo "Reloading Jenkins configuration for job: $job_name"
cat << 'EOF' | jenkins_cli groovy =
import jenkins.model.*;
import org.conjur.jenkins.configuration.*;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;

def instance = Jenkins.getInstance()
def conjurConfig = instance.getDescriptor(GlobalConjurConfiguration.class)
conjurConfig.load()
EOF
}

run_disco_discovery() {
    echo "Launching CyberArk DisCo Discovery and waiting for completion..."
    jenkins_cli groovy = < /tmp/run-disco-discovery.groovy
    check_command_success "CyberArk DisCo Discovery export"
}

wait_for_conjur_refresh() {
      echo "Refreshing Conjur authentication by accessing credentials page..."
      sleep 120
      curl -s "http://localhost:8080/manage/credentials/" > /dev/null
}

process_jobs() {
    local job_pattern=$1
    echo "Starting job trigger loop..."

    for config in $(find /var/jenkins_home/jobs -name config.xml); do
        local flag=0
        local job_dir=$(dirname "$config")
        local relative_path="${job_dir#/var/jenkins_home/jobs/}"
       # Replace intermediate "jobs/" with "/" to match Jenkins folder structure
        local job_name="${relative_path//\/jobs\//\/}"
        local specific_job=$(basename "$job_name")

        if grep -q "<definition" "$config"; then
            echo "Standard pipeline job: $config"
            flag=1
        elif grep -q "<builders>" "$config"; then
            echo "Freestyle job: $config"
            flag=2
        elif grep -q "<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" "$config"; then
            echo "Multibranch Pipeline job: $config"
            flag=3
        else
            echo "Skipping non-buildable job (probably folder): $config"
            echo "-----------------------"
            continue
        fi

        if [[ -n "$job_pattern" && "$specific_job" == *"$job_pattern"* ]]; then
            echo "Triggering Jenkins build for: $job_name"
             if trigger_jenkins_build "$job_name" "$flag"; then
                 ((TOTAL_SUCCESS++))
             else
                 ((TOTAL_FAILURE++))
             fi
            echo "-------------------------"
        fi
        done
        echo "Finished job trigger loop."
}

print_final_summary() {
    echo ""
    echo "=== FINAL TEST SUMMARY ==="
    echo "Total Successful: $TOTAL_SUCCESS"
    echo "Total Failed: $TOTAL_FAILURE"
    echo "Total Jobs: $((TOTAL_SUCCESS + TOTAL_FAILURE))"
    echo "=========================="

    return $((TOTAL_FAILURE > 0))
}

# Main
CREATE_FLAG=0
CREATE_DISCO_FLAG=0
TEST_API_KEY_JOBS=0
TEST_JWT_KEY_JOBS=0
IMPORT_CERTS_FLAG=0
INSTALL_PLUGINS_FLAG=0
RUN_DISCO_DISCOVERY_FLAG=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --create)
            CREATE_FLAG=1
            shift
            ;;
        --create-disco)
            CREATE_DISCO_FLAG=1
            shift
            ;;
        --test-api-key-jobs)
            TEST_API_KEY_JOBS=1
            shift
            ;;
        --test-jwt-jobs)
            TEST_JWT_KEY_JOBS=1
            shift
            ;;
        --import-certs)
            IMPORT_CERTS_FLAG=1
            shift
            ;;
        --install-plugins)
            INSTALL_PLUGINS_FLAG=1
            shift
            ;;
        --run-disco-discovery)
            RUN_DISCO_DISCOVERY_FLAG=1
            shift
            ;;
        *)
            echo "Usage: $0 [--create] [--create-disco] [--test-api-key-jobs] [--test-jwt-jobs] [--import-certs] [--install-plugins] [--run-disco-discovery]"
            exit 1
            ;;
    esac
done


if [ $IMPORT_CERTS_FLAG -eq 1 ]; then
    download_jenkins_cli
    import_ssl_certificate
    if [ -f /tmp/jenkinsConfig.xml ]; then
        cp -f /tmp/jenkinsConfig.xml /var/jenkins_home/jenkins.model.JenkinsLocationConfiguration.xml
    fi
    exit $?
elif [ $INSTALL_PLUGINS_FLAG -eq 1 ]; then
    download_jenkins_cli
    install_jenkins_plugins
    exit $?
elif [ $RUN_DISCO_DISCOVERY_FLAG -eq 1 ]; then
    download_jenkins_cli
# Skip running Disco discovery during the Jenkins setup, as it is not needed because the E2E tests will trigger it later.
#    run_disco_discovery
    exit $?
elif [ $CREATE_DISCO_FLAG -eq 1 ]; then
    download_jenkins_cli
    create_disco_credentials
    exit $?
elif [ $CREATE_FLAG -eq 1 ]; then
    create_credentials
    configure_auth_method "api-key"
    process_jobs "api-key"
    configure_auth_method "jwt"
    wait_for_conjur_refresh
    process_jobs "jwt"
    print_final_summary
    exit $?
elif [ $TEST_API_KEY_JOBS -eq 1 ]; then
    create_credentials
    configure_auth_method "api-key"
    process_jobs "api-key"
    print_final_summary
    exit $?
elif [ $TEST_JWT_KEY_JOBS -eq 1 ]; then
    create_credentials
    configure_auth_method "jwt"
    process_jobs "jwt"
    print_final_summary
    exit $?
else
    download_jenkins_cli
    import_ssl_certificate
    install_jenkins_plugins
    cp -f /tmp/jenkinsConfig.xml /var/jenkins_home/jenkins.model.JenkinsLocationConfiguration.xml
fi