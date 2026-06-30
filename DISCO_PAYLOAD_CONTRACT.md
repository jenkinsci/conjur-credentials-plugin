# DisCo Payload Contract

This document is the authoritative mapping between Jenkins data and the JSON
payload posted to the DisCo ingestion endpoint.  Update it whenever a field
name, type, or source changes.

---


## Top-level snapshot (`DiscoverySnapshot`)

| JSON field            | Java field | Source                                                                      | Notes                                                                                                             |
|-----------------------|---|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `jenkinsId`           | `jenkinsId` | `Jenkins.get().getLegacyInstanceId()`                                       | Unique identifier of the Jenkins controller                                                                       |
| `originStoreId`       | `originStoreId` | `Jenkins.get().getLegacyInstanceId()`                                       | Same as `jenkinsId`; identifies where credentials originate                                                      |
| `dataSourceType`      | `dataSourceType` | Hardcoded `"JenkinsDiscoveryPlugin"`                                        |                                                                                                                   |
| `version`             | `version` | `Jenkins.get().getPlugin("conjur-credentials").getWrapper().getVersion()`   | Falls back to `"unknown"`                                                                                         |
| `snapshotId`          | `snapshotId` | `UUID.randomUUID().toString()`                                              | UUID v4; unique per run                                                                                           |
| `timestamp`           | `timestamp` | `DateTimeFormatter.ISO_INSTANT.format(Instant.now())`                       | ISO-8601 UTC, e.g. `2026-03-25T12:00:00Z`                                                                         |
| `disCoConfig`         | `disCoConfig` | `DiscoExporterConfigurationSnapshot.from(DiscoExporterConfiguration.get())` | See disCoConfig schema below                                                                                      |
| `kid`                 | `kid` | `EncryptionService.getSelectedKid()`                                        | Key ID of the RSA/ECC key used to encrypt values; selected by longest remaining validity (see key selection rule) |
| `openIdConfiguration` | `openIdConfiguration` | `DiscoveryOrchestrator.buildOpenIdConfiguration(config)`                    | Nested object; see `openIdConfiguration` schema below. Always present.                                           |
| `conjurConfig`        | `conjurConfig` | `GlobalConjurConfigurationSnapshot.from(GlobalConjurConfiguration.get())`   | See conjurConfig schema below; `null` if not configured                                                           |
| `credentials`         | `credentials` | `CredentialsDictionaryMapper.mapAll()`                                      | See credential schema below                                                                                       |
| `folders`             | `folders` | `DiscoveryOrchestrator.collectFolders()`                                    | Root entry always first; see folder schema below                                                                  |
| `jobs`                | `jobs` | `DiscoveryOrchestrator.collectJobs()`                                       | See job schema below                                                                                              |


### Example

```json
{
  "jenkinsId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "originStoreId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "dataSourceType": "JenkinsDiscoveryPlugin",
  "version": "3.1.0",
  "snapshotId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-03-25T12:00:00Z",
  "disCoConfig": {
    "subdomain": "cocacola",
    "authMode": "USERNAME_PASSWORD",
    "conjurCredentialId": "disco-api-cred",
    "discoUsernameCredentialId": null,
    "discoPasswordCredentialId": null,
    "exportIntervalHours": 12,
    "exportSecretValues": true,
    "discoveryBaseUrl": "https://service.management.cyberark.cloud/",
    "testEnvironment": false
  },
  "kid": "u12-122",
  "openIdConfiguration": {
    "issuer": "https://jenkins.internal",
    "jwksUri": "https://jenkins.internal/jwtauth/conjur-jwk-set",
    "jwksData": {
      "keys": [
        { "kty": "RSA", "kid": "abc123", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" }
      ]
    }
  },
  "conjurConfig": {
    "applianceURL": "https://conjur.internal",
    "account": "myaccount",
    "credentialID": "conjur-api-key",
    "certificateCredentialID": null,
    "inheritFromParent": false,
    "authWebServiceId": "conjur/authn-jwt/jenkins",
    "jwtAudience": "cyberark-conjur",
    "keyLifetimeInMinutes": 60,
    "tokenDurationInSeconds": 120,
    "selectAuthenticator": "JWTAuth",
    "selectIdentityFormatToken": "jenkins_full_name",
    "selectIdentityFieldsSeparator": "-",
    "identityFormatFieldsFromToken": "jenkins_full_name",
    "identityFieldName": "sub"
  },
  "credentials": [],
  "folders": [],
  "jobs": []
}
```

---

## OpenID configuration (`openIdConfiguration`)

Snapshot of `OpenIdConfiguration` built on the fly during each export run. Always present.

| JSON field | Java field | Source | Notes |
|---|---|---|---|
| `issuer` | `issuer` | `ConjurAPIUtils.getJenkinsIssuer()` | Jenkins root URL with trailing slash stripped; `null` if root URL not configured |
| `jwksUri` | `jwksUri` | `DiscoExporterConfiguration.getJwksUri()` | `{rootUrl}jwtauth/conjur-jwk-set` |
| `jwksData` | `jwksData` | `JwtAuthenticationService.getJwkSet()` deserialized via Gson | Entire JWKS object; `null` if unavailable (logged as DISC_009) |

### Example

```json
{
  "issuer": "https://jenkins.internal",
  "jwksUri": "https://jenkins.internal/jwtauth/conjur-jwk-set",
  "jwksData": {
    "keys": [
      { "kty": "RSA", "kid": "abc123", "use": "sig", "alg": "RS256", "n": "...", "e": "AQAB" }
    ]
  }
}
```


---

## Discovery configuration (`disCoConfig`)

Snapshot of `DiscoExporterConfiguration` at export time. Always present.

| JSON field | Java source | Notes |
|---|---|---|
| `subdomain` | `DiscoExporterConfiguration.getSubdomain()` | Tenant subdomain; empty → export aborts with DISC_003 |
| `authMode` | `DiscoExporterConfiguration.getAuthMode().name()` | `USERNAME_PASSWORD` or `TWO_SECRETS` |
| `conjurCredentialId` | `DiscoExporterConfiguration.getConjurCredentialId()` | Credential ID used in `USERNAME_PASSWORD` mode |
| `discoUsernameCredentialId` | `DiscoExporterConfiguration.getDiscoUsernameCredentialId()` | Username secret ID in `TWO_SECRETS` mode |
| `discoPasswordCredentialId` | `DiscoExporterConfiguration.getDiscoPasswordCredentialId()` | Password secret ID in `TWO_SECRETS` mode |
| `exportIntervalHours` | `DiscoExporterConfiguration.getExportIntervalHours()` | Scheduler recurrence interval (1–24) |
| `exportSecretValues` | `DiscoExporterConfiguration.isExportSecretValues()` | Whether `values` fields are present in credential records |
| `discoveryBaseUrl` | `DiscoExporterConfiguration.getDiscoveryBaseUrl()` | Active DisCo service base URL (derived from `CYBERARK_DISCO_ENV`) |
| `testEnvironment` | `DiscoExporterConfiguration.isTestEnvironment()` | `true` when the env points to a non-production environment |

---

## Global Conjur configuration (`conjurConfig`)

Snapshot of `GlobalConjurConfiguration` and its nested `ConjurConfiguration` at export time.
`null` when `GlobalConjurConfiguration` is unavailable.

| JSON field | Java source | Notes |
|---|---|---|
| `applianceURL` | `ConjurConfiguration.getApplianceURL()` | Conjur appliance URL; `null` if not set |
| `account` | `ConjurConfiguration.getAccount()` | Conjur account name |
| `credentialID` | `ConjurConfiguration.getCredentialID()` | ID of the API-key credential |
| `certificateCredentialID` | `ConjurConfiguration.getCertificateCredentialID()` | ID of the certificate credential; `null` if not set |
| `inheritFromParent` | `ConjurConfiguration.getInheritFromParent()` | Whether global config inherits from a parent |
| `authWebServiceId` | `GlobalConjurConfiguration.getAuthWebServiceId()` | JWT authenticator web service ID |
| `jwtAudience` | `GlobalConjurConfiguration.getJwtAudience()` | JWT audience claim |
| `keyLifetimeInMinutes` | `GlobalConjurConfiguration.getKeyLifetimeInMinutes()` | Signing key rotation interval |
| `tokenDurationInSeconds` | `GlobalConjurConfiguration.getTokenDurationInSeconds()` | JWT token TTL |
| `selectAuthenticator` | `GlobalConjurConfiguration.getSelectAuthenticator()` | Active authenticator (`APIKey`, `JWTAuth`, …) |
| `selectIdentityFormatToken` | `GlobalConjurConfiguration.getSelectIdentityFormatToken()` | Identity token field name |
| `selectIdentityFieldsSeparator` | `GlobalConjurConfiguration.getSelectIdentityFieldsSeparator()` | Separator for compound identity fields |
| `identityFormatFieldsFromToken` | `GlobalConjurConfiguration.getIdentityFormatFieldsFromToken()` | Fields used to build the Conjur identity |
| `identityFieldName` | `GlobalConjurConfiguration.getidentityFieldName()` | JWT claim used as identity (`sub` by default) |

---

## Credential record (`CredentialRecord`)

| JSON field | Java field | Source | Notes |
|---|---|---|---|
| `credentialId` | `credentialId` | `credential.getId()` | Jenkins credential ID |
| `name` | `name` | `credential.getId()` | Displayed name; currently same as credentialId |
| `displayName` | `displayName` | `credential.getName()` via reflection; falls back to `credentialId` | Human-readable display name set by the user |
| `description` | `description` | `credential.getDescription()` | Description. May be `null` |
| `error` | `error` | Set when the entire credential scan fails fatally | Non-null indicates the record could not be populated; logged as DISC_005 |
| `originId` | `originId` | `"{scopePath}:{credentialId}"` | Fully-qualified unique key in the export |
| `type` | `type` | `credential.getClass().getName()` | Fully-qualified Java class name |
| `location` | `location` | `scopePath` | `"Global"`, `"production/finance"`, etc. |
| `additionalData` | `additionalData` | See below | Map of provider metadata |
| `conjurization` | `conjurization` | `AnnotationMapper.map(credential)` | See conjurization table |
| `fields` | `fields` | Reflection over credential class hierarchy | `{fieldName: javaTypeName}` |
| `values` | `values` | `EncryptionService.encryptValue(gson.toJson(rawValues))` | Omitted when `exportSecretValues=false` or external provider |
| `valuesWithError` | `valuesWithError` | Field names that failed when they could not be delivered like there was timeout while calling getSecret() | Empty list when no errors; omitted when empty |
| `whereUsed` | `whereUsed` | `UsageTracker.getWhereUsed(credentialId)` | See whereUsed schema |
| `inheritancePath` | `inheritancePath` | `CredentialsDictionaryMapper.buildInheritancePath(cred.getClass())` | Comma-separated class hierarchy from concrete class up to (not including) `hudson.model.AbstractItem` |
| `levelUpdatedAt` | `levelUpdatedAt` | `DateTimeFormatter.ISO_INSTANT.format(Instant.now())` | Information about credentials when they were updated, its possible to check update timestamp of configuration on specified level |
| `createdAt` | `createdAt` | Timestamp if object have function getCreatedTime |
| `updatedAt` | `updatedAt` | Timestamp if object have function getUpdatedTime |

### `additionalData` fields

| Key | Source | Notes |
|---|---|---|
| `storeProvider` | Resolved from context type | `SystemCredentialsProvider` (global), `FolderCredentialsProvider` (folder), `JobCredentialsStore` (job) |
| `storeProviderVersion` | `Jenkins.get().getPlugin("{plugin}").getWrapper().getVersion()` | Plugin that owns the store |
| `scope` | Derived from context | `global`, `folder`, or `job` |
| `scopePath` | Traversal path | `""` for global, `"team/subteam"` for folders |

### `whereUsed` schema

```json
["team/deploy", "finance/release"]
```

A flat JSON array of strings.  **reference key**
pointing to the Jenkins item path where the credential is used.  Empty array when the credential
is not referenced anywhere.  Source: `UsageTracker.scan()` which regex-scans Pipeline script
bodies and config XML for `credentialsId('...')`, `credentialsId="..."`, and
`<credentialsId>...</credentialsId>` patterns.

### `inheritancePath` (credential)

Comma-separated Java class names starting from the credential's concrete class, walking up the
superclass chain, stopping **before** `hudson.model.AbstractItem` (or at `Object` if
`AbstractItem` is not in the hierarchy).

**Example:**

```
"com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl,com.cloudbees.plugins.credentials.impl.BaseStandardCredentials,com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials"
```

---

## Conjurization mapping (`AnnotationMapper`)

| Credential interface | `jenkins_credential_type` | Extra annotations | Additional information |
|---|---|---|---|
| `StringCredentials` | `stringcredential` | - |
| `UsernamePasswordCredentials` | `usernamecredential` | `jenkins_credential_username = {{username}}` | - |
| `BasicSSHUserPrivateKey` | `usernamesshkeycredential` | `jenkins_credential_username = {{username}}` | - |
| `SecretFileCredential` | `filecredential` | - |
| `DockerServerCredentials` | `dockercertcredential` | Add three variable ids with suffix key, cert and ca `variable:annotation:jenkins_credential_type/key`, `.../cert`, `.../ca` suffixes |
| Unknown / other | `stringcredential` | — |


### Conjurization map key format

```
variable:value                                   → template path for the primary secret value
variable:annotation:jenkins_credential_type      → type hint annotation (primary type)
variable:annotation:jenkins_credential_type_alt  → always "stringcredential" for double-mapped types
variable:annotation:jenkins_credential_username  → username annotation (where applicable)
```

### Example — UsernamePasswordCredentials

```json
{
  "variable:annotation:jenkins_credential_type":      "usernamecredential",
  "variable:annotation:jenkins_credential_username":  "{{username}}",
  "variable:value":                                    "{{password}}",
  "variable:annotation:jenkins_credential_type_alt":  "stringcredential"
}
```

The consumer resolves `{{username}}` and `{{password}}` by looking up those keys in the decrypted `values` map.

---

## JenkinsObject record (`JenkinsObject`)

Used for both `folders` and `jobs` arrays in the snapshot.  Fields that are not applicable
to a given type are left empty (`""`).

| JSON field | Java field | Source | Notes |
|---|---|---|---|
| `path` | `path` | `item.getFullName()` | Full path including folder hierarchy; `""` for global root |
| `description` | `description` | `item.getDescription()` | May be `null` |
| `scmUrl` | `scmUrl` | `job.getScm().getType()` | SCM type string; `""` if no SCM or for folders |
| `type` | `type` | `item.getClass().getName()` | Fully-qualified Java class name; `"GlobalConfiguration"` for root |
| `jenkins_pronoun` | `jenkins_pronoun` | `item.getPronoun()` | Jenkins display label, e.g. `"Pipeline"`, `"Folder"` |
| `lastBuildTs` | `lastBuildTs` | `job.getLastBuild().getTime().toInstant()` ISO-8601 | `""` if no builds or for folders |
| `inheritancePath` | `inheritancePath` | `CredentialsDictionaryMapper.buildInheritancePath(item.getClass())` | Comma-separated class hierarchy up to (not including) `hudson.model.AbstractItem`; omitted for root |
| `sub` | `sub` | `JwtToken.computeSubClaim(item, GlobalConjurConfiguration.get())` | JWT `sub` claim that would be issued for this item; mirrors the identity used in Conjur policy. `"GlobalCredentials"` for the root entry. Value depends on `GlobalConjurConfiguration` identity-format settings (same logic as `JwtToken.getUnsignedToken`). |
| `conjurConfiguration` | `conjurConfiguration` | See below | Full `ConjurConfiguration` object; omitted (`null`) if not set or if all fields are default |

### `conjurConfiguration` for folders

Retrieved via `FolderConjurConfiguration` property on `AbstractFolder`.  Omitted (serialized as
`null` and absent from JSON) when:
- The folder has no `FolderConjurConfiguration` property, **or**
- `inheritFromParent = true` and both `applianceURL` and `account` are blank.

### `conjurConfiguration` for jobs

Retrieved via `ConjurJITJobProperty` on `Job`.  Same omission rules as folders.

### Example (folder with explicit configuration)

```json
{
  "path": "team/finance",
  "description": "Finance team folder",
  "scmUrl": "",
  "type": "com.cloudbees.hudson.plugins.folder.Folder",
  "jenkins_pronoun": "Folder",
  "lastBuildTs": "",
  "inheritancePath": "com.cloudbees.hudson.plugins.folder.Folder,com.cloudbees.hudson.plugins.folder.AbstractFolder",
  "sub": "team/finance",
  "conjurConfiguration": {
    "applianceURL": "https://conjur.internal",
    "account": "myaccount",
    "inheritFromParent": false
  }
}
```

---

## DisCo service environments (`DiscoEnvironment`)

The plugin ships with all known CyberArk DisCo service URLs baked in as enum
constants.  The active environment is selected **at Jenkins startup** via the
OS/JVM environment variable:

```
CYBERARK_DISCO_ENV=<NAME>
```

When the variable is absent, blank, or contains an unrecognised value the
plugin falls back to **Production**.  The variable is read once per run via
`DiscoEnvironment.resolve()` (called inside `DiscoExporterConfiguration.getDiscoveryBaseUrl()`).

### Standard environments

| Name (env-var value) | Base URL |
|---|---|
| `DEV` | `https://service.management.cyberark-everest-dev.com/` |
| `DP` | `https://service.management.sandbox-cyberark.cloud/` |
| `INTEGRATION` | `https://service.management.integration-cyberark.cloud/` |
| `INTEGRATION_DEV` | `https://service.management.cyberark-everest-integdev.cloud/` |
| `PRE_PROD` | `https://service.management.cyberark-everest-pre-prod.cloud/` |
| `PRODUCTION` *(default)* | `https://service.management.cyberark.cloud/` |
| `PT` | `https://service.management.pt-cyberark.cloud/` |
| `STAGE` | `https://service.management.cyberark-everest-stage.com/` |
| `TEST` | `https://service.management.cyberark-everest-test.com/` |

### FedRAMP environments

| Name (env-var value) | Base URL |
|---|---|
| `GOV_DEV` | `https://service.management.dev-cyberarkgov.com/` |
| `GOV_TEST` | `https://service.management.test-cyberarkgov.com/` |
| `GOV_STAGE` | `https://service.management.stage-cyberarkgov.com/` |
| `GOV_STAGE_INTEGRATION` | `https://service.management.integration-cyberarkgov.cloud/` |
| `GOV_DEV_INTEGRATION` | `https://service.management.integdev-cyberarkgov.cloud/` |
| `GOV_PROD` | `https://service.management.cyberarkgov.cloud/` |

### Key selection rule (`EncryptionService`)

The plugin fetches all available public keys from `{resolvedUrl}/keys` and selects
the key with the **longest remaining validity period**:

- Each key may carry an `exp` field (Unix epoch seconds).
- The key with the **highest `exp` value** is selected.
- Keys that carry **no `exp` field** are treated as having infinite validity and
  always outrank keys with a finite expiry.
- When multiple keys share the same effective expiry the **first** one in the
  response array is used.
- The selected key's ID is recorded in `kid` on the snapshot.

### Conjur URL

The Conjur appliance URL included in the run result (`conjurUrl`) is **not**
configured in the DisCo settings page.  It is read from the main plugin
configuration: `GlobalConjurConfiguration → ConjurConfiguration.getApplianceURL()`.

---

## Error codes

| Code | Level | Meaning |
|---|---|---|
| DISC_001 | ERROR | Discovery service resolution failure (network, bad URL, empty body) |
| DISC_002 | WARN | Run skipped — already running or rate-limit active on MANUAL trigger |
| DISC_003 | ERROR | Configuration invalid — subdomain empty or unresolvable |
| DISC_004 | INFO | Credential context scan started (logged per scope) |
| DISC_005 | WARN | Individual credential or context scan failed (non-fatal) |
| DISC_006 | INFO | Encryption service ready — keys fetched successfully |
| DISC_007 | INFO | Export successful (HTTP 200/202) |
| DISC_008 | ERROR | Export failed (auth error, server error, I/O failure, payload too large) |
| DISC_009 | WARN | JWKS data unavailable or credential resolution failed |
| DISC_010 | WARN | Remote rate limit (HTTP 429) — respects `Retry-After` header |

---

## Authentication modes (`AuthMode`)

### `USERNAME_PASSWORD`
A single `UsernamePasswordCredentials` entry referenced by `conjurCredentialId`.
- Username: `StandardUsernamePasswordCredentials.getUsername()`
- Password: `StandardUsernamePasswordCredentials.getPassword().getPlainText()`

### `TWO_SECRETS`
Two separate `StringCredentials` entries.
- `discoUsernameCredentialId` → username (secret value is the username string)
- `discoPasswordCredentialId` → password (secret value is the password string)

Both modes produce a `Basic {base64(username:password)}` `Authorization` header.
