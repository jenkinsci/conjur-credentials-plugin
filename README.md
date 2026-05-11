# Conjur Credentials Plugin

This Conjur credentials plugin securely provides credentials that are stored in Conjur, Secrets Manager Self-Hosted, and Secrets Manager SaaS to Jenkins jobs.

## Certification level
![](https://img.shields.io/badge/Certification%20Level-Certified-28A745?link=https://github.com/cyberark/community/blob/master/Conjur/conventions/certification-levels.md)

This repo is a **Certified** level project. It's a community contributed project that **has been reviewed and tested by CyberArk and is trusted to use with Conjur OSS, Secrets Manager Self-Hosted, and Secrets Manager SaaS**. For more detailed information on our certification levels, see [our community guidelines](https://github.com/cyberark/community/blob/master/Conjur/conventions/certification-levels.md#certified).

## Reference

* [SECURING SECRETS ACROSS THE CI/CD PIPELINE](https://www.conjur.org/use-cases/ci-cd-pipelines/)
* [CI/CD Servers Know All Your Plumbing Secrets](https://www.conjur.org/blog/ci-cd-servers-know-all-your-plumbing-secrets/)

## Usage

Install the plugin using Jenkins "Plugin Manager" with an administrator account. After installing the plugin and restarting Jenkins, you are ready to start.

Please follow this documentation to configure plugin:

- [Configure JWT Authentication](#configure-jwt-authentication)
- [Configure API Key Authentication](#configure-api-key-authentication)
- [Migration Guide](#migration-guide)



## Configure JWT authentication

### Step 1: Gather information

The following information will be needed from the Secrets Manager installation in order to configure the Jenkins integration:

- Conjur Account - The account that was created when Secrets Manager was originally configured
- Conjur Appliance URL - The secure URL to Secrets Manager (ex: `https://conjur.example.com`)
- JWT authenticator service ID: The ID for the JWT authenticator service as defined in policy (ex: `authn-jwt/jenkins`)

The following information will be needed from the Jenkins environment:
- The name of the claim in the JWT that will represent the Jenkins job (ex: `sub`)
- The JWKS URI that will be used to validate JWT tokens
- The JWT issuer (`iss` claim value in the JWT token)
- The audience (`aud` claim value in the JWT token)

Here is an example of what a JWT token used by the Jenkins integration should look like.

```
{
    "sub": "Project1-Job1",
    "jenkins_parent_url_child_prefix": "job",
    "jenkins_parent_full_name": "Project1",
    "jenkins_parent_task_noun": "Build",
    "jenkins_full_name": "Project1/Job1",
    "iss": "https://Jenkins URL",
    "aud": "cyberark-conjur",
    "jenkins_name": "Job1",
    "nbf": 1693469511,
    "jenkins_parent_name": "Project1",
    "name": "admin",
    "jenkins_task_noun": "Build",
    "exp": 1693469661,
    "iat": 1693469541,
    "jenkins_pronoun": "Pipeline",
    "jti": "fe5fafc9c2964c69bfd2017f1be07dfa",
    "jenkins_job_buildir": "/var/jenkins_home/jobs/Project1/jobs/Job1/builds"
}
```

> **Note:** The JWT contains both `jenkins_name` (leaf job name only, `"Job1"`) and `jenkins_full_name` (folder-qualified path, `"Project1/Job1"`). When choosing a claim for `token-app-property`, make sure its value uniquely identifies the job across your entire Jenkins instance. `jenkins_name` is not unique in multi-folder deployments — two jobs in different folders with the same leaf name produce identical values. `jenkins_full_name` is unique by construction, but you may use any claim whose value is guaranteed to be unique for your setup.

### Step 2: Configure the Conjur Credentials Plugin in Jenkins

Configure the plugin within Jenkins with authentication details for your Jenkins job. This step will need to be done by a Jenkins admin.

Navigate to `Manage Jenkins > System`.

Under `Conjur Appliance`, provide the Secrets Manager connection details gathered earlier in this process:

| Field                   | Description                                |
|-------------------------|--------------------------------------------|
| Account                 | As provided by your Secrets Manager admin           |
| Appliance URL           | As provided by your Secrets Manager admin           |
| Conjur Auth Credentials | For JWT authentication, leave this as none |
| Conjur SSL Certificate  | Please use [this documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/jenkins.htm) to configure SSL properly |

Under `Conjur JWT Authentication`, provide the JWT authentication details gathered earlier in this process:

| Setting   | Description                                                                                                                                             |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Auth Webservice ID              | The service ID of the JWT authenticator as defined in Secrets Manager policy (ex: authn-jwt/jenkins) |
| JWT Audience                    | Set to `cyberark-conjur`   |
| Signing Key Lifetime in Minutes | The duration that the JWT signing key remains valid, based on your organization's security requirements (default: 60 mins)        |
| JWT Token Duration in Seconds   | The duration after which the JWT needs to be regenerated, based on your organization's security requirements (default: 2 minutes) |
| Identity Format Fields          | Set to `jenkins_full_name` |

Finally, save the configuration.

### Step 3: Define Secrets Manager Authenticator

Set up a JWT authenticator. This step will need to be done by the Secrets Manager admin.

For information and guidelines about setting up JWT authentication, see [JWT Authentication](https://docs.cyberark.com/conjur-open-source/latest/en/content/operations/services/cjr-authn-jwt-lp.htm).

Copy the following policy into a text editor:

```
# This policy defines a JWT authenticator
- !policy
  id: conjur/authn-jwt/jenkins
  body:
  - !webservice
 
  # Uncomment one of following variables depending on the public availability of the service
  # If the service is publicly available, uncomment 'jwks-uri'.
  # If the service is not available, uncomment 'public-keys'
 
  - !variable
    id: jwks-uri
 
  # - !variable
  #  id: public-keys
 
  # This variable tells Secrets Manager which claim in the JWT to use to determine the host identity.
  - !variable
    id: token-app-property
 
  # This variable is used with token-app-property. This variable will hold the Secrets Manager policy path that contains the host identity found by looking at the claim entered in token-app-property.
  - !variable
    id: identity-path
 
  # Uncomment ca-cert if the JWKS website cert isn't trusted by Secrets Manager
 
  # - !variable
  #   id: ca-cert
 
  # This variable contains the JWT's "iss" value.
  - !variable
    id: issuer
   
  # This variable contains the JWT's "aud" value.
  - !variable
    id: audience
   
  ## Group of hosts that can authenticate using this JWT Authenticator
  - !group
    id: jwt-authn-access
 
  # Permit the consumers group to authenticate to the JWT authn-jwt/jenkins web service
  - !permit
    role: !group jwt-authn-access
    privilege: [ read, authenticate ]
    resource: !webservice
 
  # Health check end-point
  - !webservice
    id: status
 
  # Group of users who can check the status of authn-jwt/jenkins
  - !group
    id: operators
 
  # Permit jenkins admins group to query the health check end-point
  - !permit
      role: !group operators
      privilege: [ read ]
      resource: !webservice status
```

Save the policy as `authn-jwt-jenkins.yml` and use the Secrets Manager CLI to load the policy into `root`:

```
conjur policy load -f /path/to/file/authn-jwt-jenkins.yml -b root
```

Using the Secrets Manager CLI populate the variables as follows:

Populate the `token-app-property` variable with the name of the claim that you received from the Jenkins admin, for example `jenkins_full_name`

```
conjur variable set -i conjur/authn-jwt/jenkins/token-app-property -v 'jenkins_full_name'
```

Populate the `identity-path` variable with the name you will give to the host policy for the Jenkins job, for example `myspace/jwt-apps`

```
conjur variable set -i conjur/authn-jwt/jenkins/identity-path -v 'myspace/jwt-apps'
```

Populate the rest of the variables with the information you received from the Jenkins admin:

```
conjur variable set -i conjur/authn-jwt/jenkins/issuer -v 'https://<Jenkins URL>'
conjur variable set -i conjur/authn-jwt/jenkins/jwks-uri -v 'https://<Jenkins URL>/jwtauth/conjur-jwk-set'
conjur variable set -i conjur/authn-jwt/jenkins/audience -v "cyberark-conjur"
```

Enable the JWT authenticator in Secrets Manager by [allow-listing the authenticator](https://docs.cyberark.com/conjur-open-source/latest/en/content/operations/services/authentication-types.htm#Allowlis).

### Step 4: Define Secrets Manager Host

Define a host in Secrets Manager policy to represent your Jenkins job. This step will need to be done by the Secrets Manager admin.

The host uses your JWT authenticator to authenticate to Secrets Manager:

Save the following policy as `author-jwt-jenkins-host.yml`. This policy includes examples of hosts that can be used to represent Jenkins jobs / folders / or global credentials:

```
- !policy
  id: myspace/jwt-apps
  body:
  
  # example of secrets assigned to Global Credentials
  - !host
    id: GlobalCredentials
    annotations:
      jenkins: true
      authn-jwt/jenkins/jenkins_full_name: GlobalCredentials
      authn-jwt/jenkins/jenkins_pronoun: Global
  
  # example of secrets assigned to Folder
  - !host
    id: Project1
    annotations:
      authn-jwt/jenkins/jenkins_pronoun: Folder
      authn-jwt/jenkins/jenkins_full_name: Project1
  
  # example of secrets assigned to pipeline
  - !host
    id: pipeline
    annotations:
      authn-jwt/jenkins/jenkins_pronoun: Pipeline
      authn-jwt/jenkins/jenkins_full_name: Project1/pipeline
  
  - !grant
    role: !group conjur/authn-jwt/jenkins/jwt-authn-access
    members:
      - !host GlobalCredentials
      - !host Project1
      - !host Pipeline
```

Use the Secrets Manager CLI to load `author-jwt-jenkins-host.yml` into `root`:

```
conjur policy load -f /path/to/file/authn-jwt-jenkins-host.yml -b root
```

Save the following policy as `grant-app-access.yml`. This policy allows your host permission to authenticate to Secrets Manager using the JWT authenticator:
```
- !grant
  role: !group conjur/authn-jwt/jenkins/jwt-authn-access
  members:
    - !group myspace/jwt-apps
```

Use the Secrets Manager CLI to load `grant-app-access.yml` into `root`:

```
conjur policy load -f grant app-access.yml -b root
```

### Step 5: Define and Grant Access to Variables

Save following policy as `secrets.yml`. This policy defines your secrets and grants access to the hosts defined in the previous step:

```
- &devvariablesGlobal
  - !variable secretGlobal
  # you may also set specified type like Username Credential, by using this annotation
  annotations:
    jenkins_credential_type: usernamecredential
    jenkins_credential_username: globaluser

# by default all secrets are mapped to Conjur Secret Credential
- &devvariablesFolder
  - !variable secretFolder

- &devvariablesPipeline
  - !variable secretPipeline
 
- !permit
  resource: *devvariablesGlobal
  privileges: [ read, execute ]
  roles: !host myspace/jwt-apps/GlobalCredentials
 
- !permit
  resource: *devvariablesFolder
  privileges: [ read, execute ]
  roles: !host myspace/jwt-apps/Project1
 
- !permit
  resource: *devvariablesPipeline
  privileges: [ read, execute ]
  roles: !host myspace/jwt-apps/Pipeline
```

Use the Secrets Manager CLI to load `secrets.yml` into `root`:

```
conjur policy load -f /path/to/file/secrets.yml -b root
```

Finally, use the CLI to assign secret values to your variables

```
conjur variable set -i devvariablesGlobal -v myglobalsecret
conjur variable set -i devvariablesFolder -v myfoldersecret
conjur variable set -i devvariablesPipeline -v mypipelinesecret
```

### Important Notes for Secrets Manager Admin

#### Folder Permission Inheritance

By default, subfolders **inherit the permissions of their parent folders** in Jenkins. This means that if you grant a folder access to a secret, all subfolders will also have access to that secret. This inheritance can be disabled in the folder settings in Jenkins.

It is also important to mention that all secrets assigned to `root` (global configuration) are visible from any level of Jenkins, even if the "Inherit from parent" option is turned off.

#### Host Annotation Examples

When JWT authenticator is selected, you have to remember to set the proper path in the `jenkins_full_name` field.

> **Security note:** The claim you set as `token-app-property` must uniquely identify the job across your entire Jenkins instance. `jenkins_name` contains only the leaf job name without folder qualification — in a multi-folder deployment, two jobs in different folders with the same name are indistinguishable by this claim alone. `jenkins_full_name` is unique by construction and is therefore the recommended choice, but any claim whose value is guaranteed unique for your setup is acceptable.

Example of policy with host assigned to folder:
```
- !host
  id: Folder
  annotations:
    jenkins: true
    authn-jwt/jenkins/jenkins_full_name: Organisation/Folder
    authn-jwt/jenkins/jenkins_pronoun: Folder
```

Example of policy with host assigned to root:
```
- !host
  id: GlobalCredentials
  annotations:
    jenkins: true
    authn-jwt/jenkins/jenkins_full_name: GlobalCredentials
    authn-jwt/jenkins/jenkins_pronoun: Global
```

Example of policy with host assigned to pipeline:
```
- !host
  id: Pipeline
  annotations:
    jenkins: true
    authn-jwt/jenkins/jenkins_full_name: Organisation/Folder/Pipeline
    authn-jwt/jenkins/jenkins_pronoun: Pipeline
```

**IMPORTANT**: The `id` of a host must always be equal to the last part of `jenkins_full_name`. For example, if the ID is `PipelineHostID` then the annotation should be:

```
authn-jwt/jenkins/jenkins_full_name: Organisation/Folder/PipelineHostID`
```

#### Using Policy File to Specify Jenkins Credential Type
This section describes how to get specified Credentials in Jenkins using Secrets Manager policy files.

Jenkins allows the possibility of using many different types of Credentials. By default, all secrets are mapped to [Jenkins StandardCredentials](https://javadoc.jenkins.io/plugin/credentials/com/cloudbees/plugins/credentials/common/StandardCredentials.html).

To change this behaviour, you must add annotations to the secrets in your Secrets Manager policy:

```
# mapped as string credentials
- &variables
  - !variable
    id: stringsecret
    annotations:
      jenkins_credential_type: stringcredential
 
# mapped as username credentials
  - !variable
    id: usernamesecret
    annotations:
      jenkins_credential_type: usernamecredential
      jenkins_credential_username: username
 
# mapped as usernamesshkeycredential credentials
  - !variable
    id: usernamesshkeysecret
    annotations:
      jenkins_credential_type: usernamesshkeycredential
      jenkins_credential_username: username
 
# mapped by default as secret credential
  - !variable local-secret3
```

## Configure API Key Authentication

Please follow [this documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/jenkins.htm) to run Jenkins Plugin in API key authentication mode. 

## Migration Guide

This part of documentation provides guidance for migrating from the previous version of the CyberArk Secrets Manager Jenkins integration to the latest version. The update introduces support for global credentials and credentials inheritance, while removing deprecated components.

For reference to the previous version’s functionality, see the [Conjur Jenkins Integration Documentation (Legacy)](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/jenkins.htm).

### Key Changes in the New Version

✅ New Features:
- Global Credentials Support:
  - Credentials can now be defined at the global level, enabling reuse across Jenkins jobs and pipelines. This significantly improves manageability and reduces duplication.
- Credentials Inheritance:
  - Jenkins jobs can now inherit credentials from parent folders or global configurations, simplifying credentials management in complex project hierarchies.
- New credential types:
  - File Secret Credential
  - Secret String Credential
- Increased minimum Jenkins version to 2.455.
- Added telemetry support

### Deprecated Features Removed

- All previously deprecated features (e.g. legacy environment variable injection methods, older configuration options) have been removed.
- Compatibility with outdated credential syntax and legacy configuration models is no longer supported.

### Migration Steps

1. Backup Current Configuration

Before proceeding with the upgrade, ensure you back up:
- Jenkins configuration files
- Secrets Manager policies and secrets
- Any pipeline scripts that interface with Secrets Manager

2. Review Deprecated Usage

Audit your Jenkins setup for use of:
- Deprecated credential definitions
- Legacy environment variable injection (e.g. through older `withCredentials` wrappers)
- Plugin configuration not aligned with the current Conjur Credentials Plugin version

Update or remove any deprecated patterns.

3. Update the Plugin

Upgrade the Jenkins Conjur Credentials Plugin to the latest version:
- Navigate to `Manage Jenkins > Plugin Manager`
- Search for `Conjur Secrets Plugin`
- Update to the latest version

4. Configure Global Credentials

You can now add global credentials (previously option "Enable Context-Aware Credential Stores"):

Edit your policy files and add a global credentials host like so:
```
# example of secrets assigned to Global Credentials
 - !host
   id: GlobalCredentials
   annotations:
     jenkins: true
     authn-jwt/jenkins/jenkins_full_name: GlobalCredentials
     authn-jwt/jenkins/jenkins_pronoun: Global
```
 
You will also need to grant variable access to the new host.

These credentials will now be accessible in any Jenkins job or folder.

5. Leverage Credential Inheritance

You can now create hosts to represent Jenkins jobs that are structured into folders.

Edit your policy files and add a folder host like so:
```
# example of secrets assigned to Folder
- !host
  id: Project1
  annotations:
    authn-jwt/jenkins/jenkins_pronoun: Folder
    authn-jwt/jenkins/jenkins_full_name: Project1
```

You will also need to grant variable access to the new host.

6. Convert old pipeline hosts to new format

The old Jenkins plugin defined pipeline hosts as follows:

```
- !host
  id: Project1-pipeline
  annotations:
      authn-jwt/jenkins/jenkins_task_noun: Build
      authn-jwt/jenkins/jenkins_parent_full_name: Project1
```

In the new plugin, the host `id` and annotations should be updated like so:

```
# example of secrets assigned to pipeline
- !host
  id: pipeline
  annotations:
    authn-jwt/jenkins/jenkins_pronoun: Pipeline
    authn-jwt/jenkins/jenkins_full_name: Project1/pipeline
```

### Compatibility Notes
- Requires Jenkins version `2.455` or newer
- Legacy configurations must be updated to comply with the new structure

### Troubleshooting
| Issue | Resolution |
| ----- | ---------- |
| Credentials not found	| Ensure credentials are correctly configured and accessible in the job / folder / global scope |
| Authentication errors | Verify Secrets Manager host and authentication identity used in the plugin |

In case you are using JWTAuthentication, please check JWT Claims