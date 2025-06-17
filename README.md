
# Conjur Credentials Plugin

This Conjur plugin securely provides credentials that are stored in Conjur to Jenkins jobs.

## Certification level
![](https://img.shields.io/badge/Certification%20Level-Certified-28A745?link=https://github.com/cyberark/community/blob/master/Conjur/conventions/certification-levels.md)

This repo is a **Certified** level project. It's a community contributed project that **has been reviewed and tested by CyberArk and is trusted to use with Conjur Open Source, Conjur Enterprise, and Conjur Cloud**. For more detailed information on our certification levels, see [our community guidelines](https://github.com/cyberark/community/blob/master/Conjur/conventions/certification-levels.md#certified).

## Reference

* [SECURING SECRETS ACROSS THE CI/CD PIPELINE](https://www.conjur.org/use-cases/ci-cd-pipelines/)
* [CI/CD Servers Know All Your Plumbing Secrets](https://www.conjur.org/blog/ci-cd-servers-know-all-your-plumbing-secrets/)

## Usage

Install the plugin using Jenkins "Plugin Manager" with an administrator account. After installing the plugin and restarting Jenkins, you are ready to start.
Please follow this documentation to configure plugin:

### Configure the integration using JWT authentication

Step 1: Gather information
Conjur admin and Jenkins admin: Provide the following information:

| Provided by, to                  | Required information                                                                                                    |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Conjur admin to the Jenkins admin | The Jenkins admin needs the following information when configuring the Conjur Secrets plugin:                           |
|                                  | The Conjur details:                                                                                                     |
|                                  | Account - The Conjur organizational account that was assigned when Conjur was originally configured. For example, conjur. |
|                                  | Conjur appliance URL - The secure URL to Conjur.                                                                        |
|                                  | For example: https://conjur.example.com                                                                                 |
|                                  | The JWT authenticator service ID in the following format:                                                               |
|                                  | authn-jwt/<name>                                                                                                        |
|                                  | Example: authn-jwt/jenkins                                                                                              |
| Jenkins admin to the Conjur admin | Give the Conjur admin the following information to set up the JWT authenticator: |
| | The name of the claim in the JWT that will represent the Jenkins job. For our examples, we've used the sub claim. |
| | The Conjur admin needs this value for the token-app-property variable of the JWT authenticator |
| | The JWKS URI |
| | The JWT issuer (iss claim value) |
| | The audience (aud claim value) |

Demo JWT
We assume the following JWT in the policies and examples in this topic.

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

Step 2: Jenkins admin: Configure the Conjur Secrets plugin

In this step you configure the Conjur Secret plugin with the authentication details for your Jenkins job.
2.1 Provide the Conjur connection details (under Manage Jenkins > System):
Under Conjur Appliance enter the Conjur details:

| Field   | Description                                                                                   |
|---------|-----------------------------------------------------------------------------------------------|
| Account | As provided by your Conjur admin |
| Appliance URL | As provided by your Conjur admin |
| Conjur Auth Credentials | For JWT authentication, leave this as none |
| Conjur SSL Certificate | Please use [this documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/jenkins.htm) to configure SSL properly |

2.2 Configure JWT authentication in Jenkins.
Under Conjur JWT Authentication

| Setting   | Description                                                                                                                                                                          |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Auth Webservice ID | The service ID of the JWT authenticator in the following format; authn-jwt/NAME, as provided by your Conjur admin.                                                                   |
| | Example: authn-jwt/jenkins                                                                                                                                                           |
| JWT Audience| Set to: cyberark-conjur                                                                                                                                                              |
| Signing Key Lifetime in Minutes | The duration that the JWT signing key remains valid, based on your organization's security requirements; after this duration, the signing key must be refreshed. Default: 60 minutes |
| JWT Token Duration in Seconds | The duration after which the JWT needs to be regenerated, based on your organization's security requirements. Default: 120 seconds (2 minutes)                                       |
| Identity Format Fields | Set to : jenkins_full_name                                                                                                                                                           |

2.3 Save the configuration.

Step 3: Conjur admin: Define the Conjur resources
Set up a JWT authenticator.

You must have Conjur permissions to perform this step.

For information and guidelines about setting up JWT authentication, see JWT Authentication.

a) Copy the following policy into a text editor:
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
 
  # This variable tells Conjur which claim in the JWT to use to determine the host identity.
  - !variable
    id: token-app-property
 
  # This variable is used with token-app-property. This variable will hold the Conjur policy path that contains the host identity found by looking at the claim entered in token-app-property.
  - !variable
    id: identity-path
 
  # Uncomment ca-cert if the JWKS website cert isn't trusted by conjur
 
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

b) Save the policy as authn-jwt-jenkins.yml and use the Conjur CLI to load the policy into root:

> conjur policy load -f /path/to/file/authn-jwt-jenkins.yml -b root
c) Using the Conjur CLI populate the variables as follows:

Populate token-app-property with name of the claim that you received from the Jenkins admin for this purpose. In our example, this is the jenkins_name claim:
> conjur variable set -i conjur/authn-jwt/jenkins/token-app-property -v 'jenkins_name'
Populate identity-path the name you will give to the host policy for the Jenkins job, for example myspace/jwt-apps:

> conjur variable set -i conjur/authn-jwt/jenkins/identity-path -v 'myspace/jwt-apps'

Populate the rest of the variables with the information you received from the Jenkins admin:
> conjur variable set -i conjur/authn-jwt/jenkins/issuer -v 'https://Jenkins URL'

> conjur variable set -i conjur/authn-jwt/jenkins/jwks-uri -v 'https://Jenkins URL/jwtauth/conjur-jwk-set'

> conjur variable set -i conjur/authn-jwt/jenkins/audience -v "cyberark-conjur"

d) Enable the JWT authenticator in Conjur.
For details, see Allowlist the authenticators.

2. Define a host to represent your Jenkins job.

The host uses your JWT authenticator to authenticate to Conjur:

a) Copy the following policy into a text editor:
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

b) Save the policy as author-jwt-jenkins-host.yml and use the Conjur CLI to load it into root:
> conjur policy load -f /path/to/file/authn-jwt-jenkins-host.yml -b root
c) Give your host permission to authenticate to Conjur using the JWT authenticator:
```
- !grant
  role: !group conjur/authn-jwt/jenkins/jwt-authn-access
  members:
    - !group myspace/jwt-apps
```
d) Save the policy as grant-app-access.yml and use the Conjur CLI to load it into root:

> conjur policy load -f grant app-access.yml -b root

Step 4: Define variables in Conjur to represent your secrets and give the host permission to access to the secrets
Copy the following policy to a text editor:
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
2. Save the policy as secrets.yml and use the Conjur CLI to load into root:

> conjur policy load -f /path/to/file/secrets.yml -b root

Step 5: Populate the secret variables
Populate the variable with a secret value:

> conjur variable set -i devvariablesGlobal -v myglobalsecret

> conjur variable set -i devvariablesFolder -v myfoldersecret

> conjur variable set -i devvariablesPipeline -v mypipelinesecret

...

Inheritance feature
This section describes how to assign secrets to specified levels in Jenkins.
Plugin allow to assign secrets to folders or to global configuration and manage their access to items placed below.
By default inheritance is turned on and if you define secrets availability for folders all of subfolders and items will get access to them.
To disable access, you have to go to folder configuration disable option and save.

It is also important to mention that all secrets assigned to root (global configuration) are visible from any level of Jenkins even if "Inherit from parent" option is turned off.
When JWT authenticator is selected, you have to remember to set proper path in jenkins_full_name field.

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

IMPORTANT: id of host must be always equal to last part of jenkins_full_name.
Example: If the ID is:  PipelineHostID the annotation should be authn-jwt/jenkins/jenkins_full_name: Organisation/Folder/PipelineHostID

Use policy file to specify type of Credential in Jenkins
This section describes how to get specified Credentials in Jenkins by using policy files.

Jenkins give possibility of using many different type of Credentials. By default all secrets are mapped to Jenkins StandardCredentials (https://javadoc.jenkins.io/plugin/credentials/com/cloudbees/plugins/credentials/common/StandardCredentials.html).
To change that behaviour you must edit your policy with secret definitions as below and add annotations to the secrets.

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

### Configure the integration using API key authentication

Please follow [this documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/jenkins.htm) to run Jenkins Plugin in APIKey authentication mode. 

