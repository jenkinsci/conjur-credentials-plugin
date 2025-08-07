#!/bin/bash

declare -x TOTAL_SUCCESS=0
declare -x TOTAL_FAILURE=0

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
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 install-plugin $(cat /tmp/plugins) -deploy
    wget https://updates.jenkins.io/latest/conjur-credentials.hpi -O /tmp/conjur-credentials.hpi
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 install-plugin file:///tmp/conjur-credentials.hpi
    check_command_success "Installing Jenkins plugins"
}

# Function to create global credentials
create_credentials() {
    echo "Create the global credentials..."
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 create-credentials-by-xml system::system::jenkins _ < /tmp/credential.xml
    check_command_success "Creating credentials"
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 create-credentials-by-xml system::system::jenkins _ < /tmp/secret.xml
    check_command_success "Creating secrets"
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 create-credentials-by-xml system::system::jenkins _ < /tmp/add-bitbucket-token.xml
    check_command_success "Adding bitbucket personal access token"
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 create-credentials-by-xml system::system::jenkins _ < /tmp/bitbucket-cred.xml
    check_command_success "Adding bitbucket username credentials"
    java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 groovy = < /tmp/bitbucket-config.groovy
    check_command_success "Adding bitbucket server instance"

}

# Function to import SSL certificate into Java keystore
import_ssl_certificate() {
    echo "Importing the SSL certificate into the Java keystore..."
    keytool -import -noprompt -file /var/jenkins_home/conjur.pem -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
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
            java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 reload-job "$1"
            check_command_success "Reloading multibranch job"

            echo "Waiting 10 seconds for branches to be discovered.."
            sleep 10
            branch_jobs=$(echo "import jenkins.model.*; Jenkins.instance.getItem('$1')?.getItems()?.each { println it.name } " | java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 groovy = )
            for branch_job in $branch_jobs; do
               full_job_name="$1/$branch_job"
               echo "Triggering branch build: $full_job_name"
               BUILD_STATUS=$(java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 build "$full_job_name" -f )
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
            BUILD_STATUS=$(java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 build "$1" -f)
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
cat << 'EOF' | java -jar /tmp/jenkins-cli.jar -s http://localhost:8080 groovy =
import jenkins.model.*;
import org.conjur.jenkins.configuration.*;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;

def instance = Jenkins.getInstance()
def conjurConfig = instance.getDescriptor(GlobalConjurConfiguration.class)
conjurConfig.load()
EOF
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
TEST_API_KEY_JOBS=0
TEST_JWT_KEY_JOBS=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --create)
            CREATE_FLAG=1
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
        *)
            echo "Usage: $0 [--create] [--test-api-key-jobs] [--test-jwt-jobs]"
            exit 1
            ;;
    esac
done


if [ $CREATE_FLAG -eq 1 ]; then
    create_credentials
    import_ssl_certificate
    configure_auth_method "api-key"
    process_jobs "api-key"
    configure_auth_method "jwt"
    wait_for_conjur_refresh
    process_jobs "jwt"
    print_final_summary
    exit $?
elif [ $TEST_API_KEY_JOBS -eq 1 ]; then
    create_credentials
    import_ssl_certificate
    configure_auth_method "api-key"
    process_jobs "api-key"
    print_final_summary
    exit $?
elif [ $TEST_JWT_KEY_JOBS -eq 1 ]; then
    create_credentials
    import_ssl_certificate
    configure_auth_method "jwt"
    process_jobs "jwt"
    print_final_summary
    exit $?
else
    download_jenkins_cli
    install_jenkins_plugins
    cp -f /tmp/jenkinsConfig.xml /var/jenkins_home/jenkins.model.JenkinsLocationConfiguration.xml
fi
