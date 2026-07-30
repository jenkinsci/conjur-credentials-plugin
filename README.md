# Jenkins

The Conjur Secrets plugin enables Jenkins to authenticate to Secrets Manager and retrieve secrets for use in Jenkins pipeline code or Freestyle projects.

## Upgrade guide

Release 3.x of the Jenkins Conjur Secrets plugin introduces changes that may affect your existing Secrets Manager policies. If you enabled the removed **Enable Context-Aware Credential Stores** setting in a previous version, you need to create a workload mapped to the `GlobalCredentials` value of the `jenkins_full_name` claim (as shown in this topic's examples).

If you are upgrading the plugin from version 2.x to 3.x, use the instructions in this [migration guide](https://github.com/jenkinsci/conjur-credentials-plugin/wiki/Upgrade-Guide).

## Benefits

The Jenkins plugin provides these advantages to Jenkins DevOps administrators:

| Advantage | Description |
|---|---|
| Security | Secret values are stored and obtained securely. Secrets are not exposed in Jenkins jobs or referenced files. |
| Central management | Secrets are managed in a central location. |
| Automatic rotation | Secret value rotations are recommended for security. Secrets Manager handles rotation so that no changes are required on the Jenkins side. |
| Segregation of duties | The plugin isolates Jenkins DevOps administrators from secrets management. |
| Flexibility | The plugin supports Jenkins scripts or projects. It supports global or folder-specific configurations. |
| Simplification | The plugin simplifies Jenkins job and project creation by requiring only a reference ID to a secret. |
| Familiarity | You configure the plugin using the Jenkins UI, a familiar interface for Jenkins users. |

## Before you begin

Before you set up the Jenkins plugin, the following is assumed:

**Jenkins admin:**

- You have a Jenkins installation that is operational.
- You are familiar with working with Jenkins configurations.

**Secrets Manager admin:**

- You have a fully operational Secrets Manager configured and running.
- You are familiar with Secrets Manager policy.

This integration supports both JWT and API key authentication. If you are using JWT authentication, make sure you are familiar with setting up JWT authentication to Secrets Manager. For details, see [JWT authenticator](https://docs.cyberark.com/conjur-enterprise/latest/en/content/operations/services/jwks_authn.htm).

## Prerequisite: Install the Jenkins Conjur Secrets plugin

This section describes how to install the Conjur Secrets plugin in Jenkins. This plugin is used for both JWT and API key authentications.

Install the Conjur Secrets plugin from:

- **Jenkins's Plugins page** (**Manage Jenkins** > **Plugins** > **Available plugins**) — requires an administrator account.
- **[Jenkins Plugins Index](https://plugins.jenkins.io/conjur-credentials/)** website — search for the Conjur Secrets plugin and install the relevant release (from the **Releases** tab).

The minimum supported version of the Jenkins plugin is 2.462.3.

Restart Jenkins.

- If you are using JWT authentication, continue with [Configure the integration using JWT authentication](#configure-the-integration-using-jwt-authentication).
- If you are using API key authentication, continue with [Configure the integration using API key authentication](#configure-the-integration-using-api-key-authentication).

## Configure the integration using JWT authentication

This section describes how to set up the Secrets Manager-Jenkins integration using JWT authentication.

### Step 1: Gather information

Secrets Manager admin and Jenkins admin: Provide the following information:

| Provided by, to | Required information |
|---|---|
| Secrets Manager admin to the Jenkins admin | The Jenkins admin needs the following information when configuring the Conjur Secrets plugin:<br><br>**The Secrets Manager details:**<br>- **Account** — The Secrets Manager organizational account that was assigned when Secrets Manager was originally configured. For example, `conjur`.<br>- **Secrets Manager appliance URL** — The secure URL to Secrets Manager. For example: `https://conjur.example.com`<br><br>**The JWT authenticator service ID** in the following format: `authn-jwt/<name>`. Example: `authn-jwt/jenkins` |
| Jenkins admin to the Secrets Manager admin | Give the Secrets Manager admin the following information to set up the JWT authenticator:<br><br>- The name of the claim in the JWT that will represent the Jenkins job. For our examples, we've used the `jenkins_full_name` claim, which is the pathname of the Jenkins item (for example, `<parent>/<child>`). The Secrets Manager admin needs this value for the `token-app-property` variable of the JWT authenticator.<br>- The JWKS URL<br>- The JWT issuer (`iss` claim value)<br>- The audience (`aud` claim value) |

#### Example JWT

The following JWT is used in this topic.

```json
{
  "sub": "GlobalCredentials",
  "jenkins_full_name": "GlobalCredentials",
  "iss": "http://jenkins-url",
  "aud": "cyberark-conjur",
  "jenkins_name": "GlobalCredentials",
  "nbf": 1751338290,
  "jenkins_parent_name": "/",
  "name": "admin",
  "jenkins_task_noun": "Build",
  "exp": 1751338440,
  "iat": 1751338320,
  "jenkins_pronoun": "Global",
  "jti": "1e1fe90bd4c74189b6c5e27c10a58f47"
}
```

The example JWT is generated for global use (can be used for authentication in all Jenkins tasks). For more information on how to scope access to secrets, see [Jenkins secret inheritance](#jenkins-secret-inheritance).

### Step 2: Jenkins admin: Configure the Conjur Secrets plugin

In this step you configure the Conjur Secrets plugin with the authentication details for your Jenkins job.

On every page that you can configure the plugin (System and Jenkins item configuration pages), you can click **JWT Token Claims** to see the JWT used to authenticate to Secrets Manager.

1. In Jenkins, go to **Manage Jenkins** > **System**.

2. Under **Secrets Manager Configuration**, enter the Secrets Manager details:

   | Field | Description |
      |---|---|
   | Select Secrets Manager Authentication Type | Select **JWT** |
   | Secrets Manager Account | The Secrets Manager account name, as provided by your Secrets Manager admin |
   | Secrets Manager Appliance URL | **Optional.** The Secrets Manager URL, as provided by your Secrets Manager admin.<br><br>If you specify the Secrets Manager URL on the System page, Jenkins sends a request to Secrets Manager when a job runs, even if the job does not need to retrieve secrets from Secrets Manager.<br><br>To set up the Conjur Secrets plugin so that it only runs for specific jobs, leave this field blank and add the Secrets Manager URL on the relevant job configuration pages (such as folder, pipeline, and freestyle configuration pages). For more information about scoping the plugin to specific Jenkins items, see [Jenkins secret inheritance](#jenkins-secret-inheritance). |
   | Secrets Manager SSL Certificate | Only relevant for Secrets Manager Self-Hosted. This certificate authority (CA) certificate verifies the Secrets Manager connection.<br><br>Select a previously configured CA certificate or add a new one to secure the Secrets Manager connection.<br><br>To add a certificate in p12 or PEM format:<br><br>1. Retrieve the certificate from Secrets Manager using the OpenSSL Client tool. The following command retrieves the certificate and stores it on your local machine:<br>```bash<br>openssl s_client -showcerts -connect <CONJUR_FQDN>:443 < /dev/null 2> /dev/null \| sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > <file-name>.pem<br>```<br>For example:<br>```bash<br>openssl s_client -showcerts -connect myorg.example.com:443 < /dev/null 2> /dev/null \| sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > conjur.pem<br>```<br>2. To convert to p12 format:<br>```bash<br>openssl pkcs12 -export -nokeys -in <file-name>.pem -out <file-name>.p12<br>```<br>3. Click **Add** > **Jenkins**. From the **Kind** dropdown list, select **Certificate**.<br>   - **PEM:** Select **PEM encoded certificate and key**. In the **Certificates** textbox, click **Add** and paste the contents of the PEM file.<br>   - **p12:** Select **Upload PKCS#12 certificate and key** and select the p12 file.<br><br>You may need to use the `keytool` command to add the certificate to the Java trust store. For example:<br>```bash<br>keytool -importcert -v \<br>        -alias server-alias \<br>        -file server.cer \<br>        -keystore cacerts.jks \<br>        -storepass changeit<br>``` |

3. Under **Secrets Manager JWT Authentication**, provide the JWT authentication details:

   | Setting | Description |
      |---|---|
   | Service ID | **Required.** The service ID of the JWT authenticator in the format `authn-jwt/<name>`, as provided by your Secrets Manager admin. Example: `authn-jwt/jenkins` |
   | JWT Audience | **Optional.** The audience value injected into the JWT's `aud` claim. Example: `cyberark-conjur` |
   | Signing Key Lifetime in Minutes | The duration that the JWT signing key remains valid. Default: 60 minutes |
   | JWT Token Duration in Seconds | The duration after which the JWT needs to be regenerated. Default: 120 seconds (2 minutes) |
   | Identity Format Fields *(deprecated)* | When **Enable Identity Format Fields From Token** is enabled, you can select the JWT `sub` claim's value from the drop-down list: `jenkins_full_name`, `jenkins_parent_full_name-jenkins_name`, `jenkins_parent_full_name:jenkins_name`, `jenkins_parent_full_name+jenkins_name`, `jenkins_parent_full_name.jenkins_name`, `jenkins_parent_full_name\|jenkins_name`.<br><br>When the option is cleared, the `sub` claim's value is `jenkins_parent_full_name/jenkins_name` (identical to the `jenkins_full_name` claim value). |

4. Save the configuration.

### Step 3: Secrets Manager admin: Define the Secrets Manager resources

1. Set up a JWT authenticator. For information and guidelines, see [JWT authenticator](https://docs.cyberark.com/conjur-enterprise/latest/en/content/operations/services/jwks_authn.htm).

2. Copy the following policy into a text editor:

   ```yaml
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

     # This variable is used with token-app-property. This variable will hold the Secrets Manager policy path
     # that contains the host identity found by looking at the claim entered in token-app-property.
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
       id: apps

     # Permit the consumers group to authenticate to the jenkins web service
     - !permit
       role: !group apps
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

3. Save the policy as `authn-jwt-jenkins.yml`, and use the Secrets Manager CLI to load the policy into root:

   ```bash
   $ conjur policy load -f /path/to/file/authn-jwt-jenkins.yml -b root
   ```

4. Populate the variables using the Secrets Manager CLI:

   Set `token-app-property` to the claim name you received from the Jenkins admin. In our example, this is the `jenkins_full_name` claim:

   ```bash
   $ conjur variable set -i conjur/authn-jwt/jenkins/token-app-property -v jenkins_full_name
   ```

   Set `identity-path` to the host policy path for the Jenkins job, for example `myspace/jenkins-apps`:

   ```bash
   $ conjur variable set -i conjur/authn-jwt/jenkins/identity-path -v 'myspace/jenkins-apps'
   ```

   Populate the remaining variables with the information you received from the Jenkins admin:

   ```bash
   $ conjur variable set -i conjur/authn-jwt/jenkins/issuer -v 'https://<jenkins-URL>'
   $ conjur variable set -i conjur/authn-jwt/jenkins/jwks-uri -v 'https://<jenkins-URL>/jwtauth/conjur-jwk-set'
   $ conjur variable set -i conjur/authn-jwt/jenkins/audience -v "cyberark-conjur"
   ```

5. Enable the JWT authenticator in Secrets Manager. For details, see [Allowlist the authenticators](https://docs.cyberark.com/conjur-enterprise/latest/en/content/operations/services/authentication-types.htm).

6. Define a host to represent your Jenkins job. The host uses your JWT authenticator to authenticate to Secrets Manager.

   Copy the following policy into a text editor:

   ```yaml
   - !policy
     id: myspace/jenkins-apps
     body:
       - !host
         id: GlobalCredentials
         annotations:
             authn-jwt/jenkins/jenkins_task_noun: Build
             authn-jwt/jenkins/jenkins_pronoun: Global
   ```

   Save the policy as `authn-jwt-jenkins-host.yml`, and load it into root:

   ```bash
   $ conjur policy load -f /path/to/file/authn-jwt-jenkins-host.yml -b root
   ```

7. Give your host permission to authenticate to Secrets Manager using the JWT authenticator:

   ```yaml
   - !grant
     role: !group conjur/authn-jwt/jenkins/apps
     member: !host myspace/jenkins-apps/GlobalCredentials
   ```

   Save the policy as `grant-app-access.yml`, and load it into root:

   ```bash
   $ conjur policy load -f grant-app-access.yml -b root
   ```

### Step 4: Define variables in Secrets Manager to represent your secrets and give the host access

Copy the following policy to a text editor:

```yaml
- &devvariables
   - !variable secretVar

- !permit
  resource: *devvariables
  privileges: [ read, execute ]
  roles: !host myspace/jenkins-apps/GlobalCredentials
```

Save the policy as `secrets.yml`, and load it into root:

```bash
conjur policy load -f /path/to/file/secrets.yml -b root
```

### Step 5: Populate the secret variables

```bash
$ conjur variable set -i secretVar -v mysecretvalue
```

## Configure the integration using API key authentication

This section describes how to set up the Jenkins plugin using API key authentication.

If you are working with JWT authentication, follow the instructions under [Configure the integration using JWT authentication](#configure-the-integration-using-jwt-authentication).

### Step 1: Secrets Manager admin: Define the Secrets Manager resources

1. Define a workload (host) policy in Secrets Manager to represent your workload.

   Copy the following policy to a text editor:

   ```yaml
   - !policy
     id: jenkins
     body:
     - !host Job1
     - &variables
         - !variable secretVar
     - !permit
       role: !host Job1
       privileges: [read, execute]
       resource: *variables
   ```

   Save the policy as `jenkins-job.yml`, and load it to root:

   ```bash
   $ conjur policy load -f jenkins-job.yml -b root
   ```

   Secrets Manager generates an API key for the Jenkins job to authenticate to Secrets Manager. You will need this API key when you configure the Conjur Secrets plugin in Jenkins.

2. Populate the secret variable:

   ```bash
   $ conjur variable set -i jenkins/secretVar -v my-secret-value
   ```

3. Provide the Jenkins admin with the following information:

  - **Account** — The Secrets Manager organizational account that was assigned when Secrets Manager was originally configured. For example, `conjur`.
  - **Secrets Manager appliance URL** — The secure URL to Secrets Manager. For example: `https://conjur.example.com`
  - **The full workload (host) path and its API key**, which are used to populate the **Username** and **Password** fields respectively in the Jenkins Conjur Secrets plugin. The host path must be in the following format: `host/<host-name>`, where:
    - `host/` is a required prefix
    - `<host-name>` is the full path of the host you just created

   For example: `host/path/to/Project1/Job1`

### Step 2: Jenkins admin: Configure the Conjur Secrets plugin

1. Create a credential in Jenkins for API key authentication to Secrets Manager. In the Jenkins credentials, define a **Username with password** credential as follows:

   | Field | Description |
      |---|---|
   | Username | The full Secrets Manager workload (host) path, as provided by your Secrets Manager admin (for example, `host/path/to/Project1/Job1`) |
   | Password | The host's API key, as provided by your Secrets Manager admin |
   | ID | Optional. A unique ID for the credentials (if undefined, Jenkins populates the value) |
   | Description | Optional. A description to identify this credential |

2. In the Jenkins system configuration, set up the Secrets Manager access details. Under **Secrets Manager Configuration**, enter the Secrets Manager details and save the configuration:

   | Field | Description |
      |---|---|
   | Select Secrets Manager Authentication Type | Select **APIKey** |
   | Secrets Manager Account | The Secrets Manager account name, as provided by your Secrets Manager admin |
   | Secrets Manager Appliance URL | **Optional.** The Secrets Manager URL, as provided by your Secrets Manager admin.<br><br>If you specify the Secrets Manager URL on the System page, Jenkins sends a request to Secrets Manager when a job runs, even if the job does not need to retrieve secrets from Secrets Manager.<br><br>To set up the Conjur Secrets plugin so that it only runs for specific jobs, leave this field blank and add the Secrets Manager URL on the relevant job configuration pages. For more information, see [Jenkins secret inheritance](#jenkins-secret-inheritance). |
   | Secrets Manager APIKey Credential | The credential that you created in the previous step. |
   | Secrets Manager SSL Certificate | Only relevant for Secrets Manager Self-Hosted. This certificate authority (CA) certificate verifies the Secrets Manager connection. See the SSL certificate instructions in [Step 2 of the JWT configuration](#step-2-jenkins-admin-configure-the-conjur-secrets-plugin) for details on adding a PEM or p12 certificate. |

## Define Secrets Manager secret credentials in Jenkins

This section describes how to define Secrets Manager Secret Credentials in Jenkins that map to your Secrets Manager secrets.

You do not need to define credentials for your secrets. Secrets for which the workload (host) has permissions are automatically retrieved. Use this section if you want to create different credential types using Secrets Manager secrets. You can also use Secrets Manager variable annotations to create specific Jenkins credentials (see [Secrets for different Jenkins credential types](#secrets-for-different-jenkins-credential-types)).

These steps are required for each secret in Secrets Manager that a Jenkins job or project needs to access, and assume that a corresponding secret is already stored in Secrets Manager.

In Jenkins, add a credential for a Secrets Manager secret by selecting **Add credentials** (for example, **Manage Jenkins** > **Credentials** > **System** > **Global credentials**). Select the required secret type and fill in the fields. For Secrets Manager Secret Credentials, provide the following details:

| Field | Description |
|---|---|
| Scope | Select an appropriate value for your use case |
| Variable path | The complete path of the variable in Secrets Manager that represents your secret. For example, if a variable named `db_password` is defined in a policy hierarchy identified as `db`, the variable path is: `db/db_password`. |
| ID | An ID to use in Jenkins to reference this variable. It does not need to match the name in Secrets Manager. Example: `CONJUR_SECRET` |
| Description | Optional. A description to identify this credential |

Save your changes.

## Usage (for the developer)

This section describes how Jenkins job code and projects access secrets stored in Secrets Manager using the Conjur Secrets plugin.

### Jenkins pipeline code

To reference Secrets Manager secrets in a Jenkins pipeline, use `withCredentials` and the symbol `conjurSecretCredential`.

Here is an example showing how to fetch the secret from a Jenkins job pipeline definition:

```groovy
node {
  stage('Build') {
    sh './bin/build'
  }

  stage('Publish') {
    withCredentials([conjurSecretCredential(credentialsId: 'CONJUR_SECRET',
      variable: 'DB_PASSWORD')]) {
        sh 'docker login -u dockeruser -p $DB_PASSWORD registry.mysite.com'
        sh 'docker push registry/myimage:tag'
    }
  }
}
```

Here is an example that retrieves a username and password from Secrets Manager:

```groovy
node {
    stage('STAGENAME') {
        withCredentials([conjurSecretUsername(credentialsId: 'CREDENTIALID',
            usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            // you can now access the credentials via $USERNAME and $PASSWORD
            // some block
        }
    }
}
```

### Jenkins Freestyle projects

To bind to Secrets Manager secrets, use the option **Use secret text(s) or file(s)** in the **Build Environment** section of a Freestyle project. From the **Credentials** dropdown list, you select secrets that are injected as environment variables to the project's build steps.

## Jenkins secret inheritance

This section describes how to assign secrets to nested items in Jenkins. The plugin enables you to configure global access to secrets or scope their access to folders and their child items.

By default, secrets are inherited. That is, if you define secrets for a folder, all of its nested items get access to the secrets.

**To disable inherited access to secrets:**

1. Go to the required item (folder, job).
2. Click **Configure**.
3. Under **Secrets Manager Appliance**, clear the **Inherit from parent** option and save your changes.

Secrets assigned at the global configuration level are accessible from any level, even if the **Inherit from parent** option is disabled.

When you use JWT authentication and need to create a workload for a nested Jenkins item, use the last part of the `jenkins_full_name` value as the workload's ID. For example, if the value is `parent/pipeline`, the workload's ID must be `pipeline`.

### Example: workload for Jenkins folder

To create a workload for a folder where the JWT `jenkins_full_name` claim is `Organisation/Folder`:

```yaml
- !host
  id: Folder
  annotations:
    jenkins: true
    authn-jwt/jenkins/jenkins_full_name: Organisation/Folder
    authn-jwt/jenkins/jenkins_pronoun: Folder
```

If you use `jenkins_full_name` as a workload annotation, you must specify its full path (in this example, `Organisation/Folder`).

### Example: workload for Jenkins nested pipeline

To create a workload for a pipeline where the JWT `jenkins_full_name` claim is `Organisation/Folder/Pipeline`:

```yaml
- !host
  id: Pipeline
  annotations:
    jenkins: true
    authn-jwt/jenkins/jenkins_full_name: Organisation/Folder/Pipeline
    authn-jwt/jenkins/jenkins_pronoun: Pipeline
```

## Secrets for different Jenkins credential types

Jenkins supports different credential types. To automatically map Secrets Manager secrets to different credential types, you define variable annotations:

- `jenkins_credential_type`: specifies the credential type, which can be:
  - `stringcredential` (string)
  - `usernamecredential` (username credential)
  - `usernamesshkeycredential` (username SSH key credential)
- `jenkins_credential_username`: the username that is passed to the Jenkins credential username field

### Example variable annotations

```yaml
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
  - !variable local-secret
```

## Idira Secrets and Machine Identities Discovery Configuration

Starting with version 3.0.9, the Jenkins Conjur Secrets plugin includes an optional, independently operating inventory pipeline called **Idira Secrets and Machine Identities Discovery Configuration**. This feature scans the credentials, jobs, and folder structure stored in Jenkins (regardless of whether the credentials originate from Secrets Manager or another provider) and exports an encrypted snapshot to the Idira secret and machine identities inventories in Manage Space, so that security teams can gain visibility into where secrets are used across Jenkins without changing how jobs consume them.

Idira Secrets and Machine Identities Discovery Configuration is a separate feature from the Secrets Manager secret retrieval described earlier in this topic. You can use Secrets Manager secret credentials without enabling Idira Secrets and Machine Identities Discovery Configuration, and you can enable Idira Secrets and Machine Identities Discovery Configuration to inventory credentials from any provider (such as Secrets Manager, HashiCorp, Azure Key Vault, and plain Jenkins credentials), independent of which secret provider is configured.

### What Idira Secrets and Machine Identities Discovery Configuration collects

On each run, Idira Secrets and Machine Identities Discovery Configuration builds a snapshot that includes:

- **Credentials** - an inventory of Jenkins credentials with their metadata (type, scope, and, if enabled, the annotated conjurization details showing how each credential maps to a Secrets Manager variable), grouped by folder and by the jobs that reference them (a "where-used" map that shows which jobs and pipelines consume each credential). Optionally, the encrypted secret values themselves - see [Export modes](#export-modes) below.
- **Folders** - the folder hierarchy of the Jenkins instance.
- **Jobs and pipelines** - freestyle jobs and pipelines, exported as machine identities.
- **Relations** - the links between credentials and the jobs and pipelines that consume them.

### Enable and configure Idira Secrets and Machine Identities Discovery Configuration

1. In Jenkins, go to **Manage Jenkins** > **System**.

2. Locate the **Idira Secrets and Machine Identities Discovery Configuration** section and configure the following fields:

   | Field | Description |
      |---|---|
   | Subdomain | **Required.** The Idira tenant subdomain that identifies where the discovery inventory is sent. If this field is left empty, discovery runs are aborted before any network call is made. |
   | Authentication Mode | Choose how Jenkins authenticates to Idira Identity to obtain the bearer token used for the discovery export:<br>- **Username + Password** - use a single Jenkins Username with password credential.<br>- **Two Secrets** - supply the username and password as two separate secret credentials. |
   | Export Interval (hours) | How often the scheduled background export runs, from 1-24 hours. Default: 12 hours. |
   | Export Secret Values | When enabled, secret values are encrypted and included in the export for credentials stored directly in Jenkins. See [Export modes](#export-modes). |

3. Click **Save**. To export an inventory immediately after configuring Idira Secrets and Machine Identities Discovery Configuration, click **Run Discovery Now** rather than waiting for the first scheduled run.

The configuration page shows a live discovery status - including the timestamp of the last export, its result (**SUCCESS**, **ERROR**, or **ABORTED**), the encryption key identifier (kid) used, and the JWKS URI - so you can confirm a run completed without checking the Jenkins system log.

### Secret value collection behavior

When Export Secret Values is enabled, the Jenkins Conjur Secrets plugin encrypts and exports Jenkins credential values to Idira secrets and machine identities inventories to support automated remediation of unmanaged secrets.

Security and privacy safeguards:

- **Encryption at source** - Credential values are encrypted within your Jenkins instance before transmission.
- **Secure storage** - Encrypted values are stored in an isolated internal branch of your Secrets Manager SaaS instance. Stored values automatically expire after 30 days.

**Control and opt-out**

You can disable secret value collection at any time by clearing the Export Secret Values checkbox. When disabled, risk analysis and text-only remediation guidance remain available.

### Export modes

Whether secret values are included in the exported inventory depends on both the **Export Secret Values** setting and where the credential is actually stored:

| Export Secret Values | Credential store | Result |
|---|---|---|
| Enabled | Jenkins built-in store (System or Folder credentials) | Metadata fields and the encrypted secret value are exported |
| Enabled | External provider (for example, HashiCorp Vault, Azure Key Vault) | Metadata fields only - the secret value is never retrieved from the external provider for export |
| Disabled | Any | Metadata fields only |

The credential used to authenticate Idira Secrets and Machine Identities Discovery Configuration itself (configured in **Authentication Mode**) is always excluded from the exported inventory, to prevent the discovery pipeline from exporting the value it uses to authenticate.

### How secret values are protected in transit

When **Export Secret Values** is enabled, secret values are never sent in plaintext:

- Before each export, the plugin retrieves the current signing keys from the Idira Discovery & Context service key endpoint and selects the key with the longest remaining validity.
- Secret values are encrypted locally using that key before being included in the export payload.
- The key identifier (kid) used is recorded in the discovery status so that it can be matched to the correct decryption key on the Idira Discovery & Context service side.
- The plugin also publishes its own JSON Web Key Set (JWKS) at `<Jenkins root URL>/jwtauth/conjur-jwk-set`, which is referenced in the export payload as the jwksUri.

### Rate limiting and scheduling

- Only one discovery run can be active at a time; a run already in progress blocks a new manual or scheduled trigger from starting.
- A manually triggered run (**Run Discovery Now**) is blocked if less than one hour has passed since the last successful export. This one-hour guard is persisted across Jenkins restarts.
- Scheduled runs, driven by the **Export Interval** setting, are not subject to the one-hour manual rate limit - scheduled runs bypass the rate limit entirely.
- The first scheduled run fires at a random delay of up to one Export Interval after Jenkins startup - not after you save the configuration - and a manual trigger does not reset that timer.
- If the configured subdomain is changed to one that resolves to a different Idira tenant than the previous run, the plugin logs a warning so administrators are aware the export destination has changed.
- The scheduler's timing is fixed once at Jenkins startup using whatever **Export Interval** value is in effect at boot time. If you change the **Export Interval** afterward, the status page's "next run" countdown updates to reflect the new value, but the underlying schedule does not actually change until Jenkins is restarted.

### Troubleshooting

The Jenkins system log records each discovery step using a structured event code in the format `DISCO_NNN`. Common codes you may encounter are described below; for the full list, see the plugin's system log output, which includes a human-readable message alongside each code.

| Code | Meaning / recommended action |
|---|---|
| DISCO_0xx | Configuration and rate-limit issues - for example, a missing subdomain, or a manual run blocked by the one-hour rate limit. Verify the **Subdomain** field and the time since the last export. |
| DISCO_03x-DISCO_05x | Key retrieval, encryption, or upload failures. Verify network connectivity from Jenkins to the Idira discovery service and that the authentication credential has not expired. |
| DISCO_06x | Authentication and safety-check failures, including an untrusted discovery host or a detected tenant/subdomain change. Confirm the **Subdomain** and authentication credentials are correct for your Idira tenant. |

Idira Secrets and Machine Identities Discovery Configuration does not affect Secrets Manager secret retrieval at build time. Disabling or misconfiguring Idira Secrets and Machine Identities Discovery Configuration does not prevent Jenkins jobs from retrieving Secrets Manager secrets as described earlier in this topic.
