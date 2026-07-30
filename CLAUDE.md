# Conjur Credentials Jenkins Plugin — Codebase Guide

## What this repo is

A Jenkins HPI plugin (`conjur-credentials`) that integrates Jenkins with CyberArk Conjur for secret retrieval. Version **3.1.1**, built with Maven against Jenkins core **2.462.3**, Java **11**.

It has two distinct feature areas:

1. **Conjur Secret Credentials** — credential types that pull values from Conjur at build time (the original feature set).
2. **DisCo Discovery Pipeline** — a new, independently-operating pipeline that scans all Jenkins credentials and exports an encrypted inventory to the CyberArk DisCo ingestion platform.

---

## Build

```bash
mvn clean package -DskipTests   # build .hpi
mvn test                         # run all unit tests
mvn hpi:run                      # start a local Jenkins with the plugin loaded
```

---

## Package Layout

All code under `src/main/java/org/conjur/jenkins/`:

```
api/                     OkHttp3 client helpers (ConjurAPI, ConjurAPIUtils)
authenticator/           Strategy pattern: APIKey vs JWT authenticators
configuration/           GlobalConjurConfiguration, FolderConjurConfiguration,
                         ConjurJITJobProperty, ConjurConfiguration (base DTO)
conjursecrets/           Credential type implementations + bindings
                         (String, Username, SSH Key, File, DockerCert)
credentials/             ConjurCredentialProvider (@Extension, ordinal=1),
                         ConjurCredentialStore, caching supplier
exceptions/              AuthenticationConjurException, InvalidConjurSecretException
jwtauth/                 JWT endpoint (/jwtauth/conjur-jwk-set GET),
                         JwtToken, JwtRsaDigitalSignatureKey
disco/                   DisCo Discovery Pipeline (see below)
```

### DisCo sub-packages (all new, added 2026-05)

```
disco/config/            DiscoExporterConfiguration  — GlobalConfiguration subclass,
                                                        rate-limit logic, Stapler actions
disco/model/             CredentialRecord, FolderRecord, JobRecord,
                         DiscoverySnapshot, DiscoveryRunResult, WhereUsed
disco/discovery/         DiscoveryOrchestrator       — singleton pipeline runner
                         DiscoveryScheduler          — AsyncPeriodicWork (@Extension)
                         DiscoveryServiceClient      — pre-flight GET → resolve URL
                         CredentialsDictionaryMapper — hierarchy scan + reflection
                         AnnotationMapper            — credential type → conjurization
                         UsageTracker                — where-used graph (jobs/folders)
disco/security/          EncryptionService           — fetch RSA/ECC keys, encrypt values
disco/export/            DiscoExportClient           — authenticated HTTPS POST to DisCo
```

---

## Key Patterns

### HTTP client
Always use **OkHttp3** (`com.squareup.okhttp3:okhttp:4.12.0`).  
Helper: `api/ConjurAPIUtils.getHttpClient(ConjurConfiguration)` — handles proxy & TLS.  
Do **not** introduce `java.net.http.HttpClient`.

### Configuration persistence
`GlobalConfiguration` subclasses + `@DataBoundSetter` + `load()` / `save()` via XStream.  
Pattern: see `GlobalConjurConfiguration` or `DiscoExporterConfiguration`.

### Credential lookup
```java
// For ItemGroup contexts (Jenkins, AbstractFolder as ItemGroup):
CredentialsProvider.lookupCredentialsInItemGroup(StandardCredentials.class, context, ACL.SYSTEM2, Collections.emptyList())
// For Item contexts (AbstractFolder as Item, Job, etc.):
CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, context, ACL.SYSTEM2, Collections.emptyList())
```
`lookupCredentials(...)` with `ACL.SYSTEM` is deprecated — always use `ACL.SYSTEM2` with the typed `lookupCredentialsInItemGroup` / `lookupCredentialsInItem` variants.
Used in: `ConjurCredentialProvider`, `CredentialsDictionaryMapper`.

### JSON serialization
**Gson 2.8.9** — already in pom, used throughout the DisCo pipeline.  
Jackson is also present (`jackson-databind:2.17.0`) but used less.

### Secret handling
`hudson.util.Secret` — always use for sensitive values. Never log `.getPlainText()`.

### JWKS endpoint
Existing: `JwtAuthenticationServiceImpl` (`@Extension`) serves `GET /jwtauth/conjur-jwk-set`.  
DisCo uses: `Jenkins.get().getRootUrl() + "jwtauth/conjur-jwk-set"` for the `jwksUri` field  
and calls `JwtToken.getJwkset()` to embed the JWKS data in the discovery payload.

### Logging
`java.util.logging.Logger` everywhere. DisCo uses structured `DISC_XXX` event codes  
(DISC_001 through DISC_010) — see `DiscoveryOrchestrator`, `DiscoExportClient`, etc.

---

## DisCo Pipeline — How It Works

```
Trigger (UI button or AsyncPeriodicWork every n hours)
  ↓
DiscoExporterConfiguration.isRateLimitActive()  ← 1-hour guard (bypassed in testEnvironment)
  ↓
DiscoveryOrchestrator.run(triggerType)
  1. DiscoveryServiceClient.resolve(baseUrl, subdomain)
       → resolvedUrl (snapshot-links), tenantId, identityBaseUrl, discoveryContextBaseUrl
  2. CyberArkIdentityClient.login(identityBaseUrl, user, pass)
       → bearerToken
  3. EncryptionService.fetchLatestKeys(discoveryContextBaseUrl, bearerToken)
       → GET {discoveryContextBaseUrl}/discovery-context/jwks → kid + publicKey
  4. UsageTracker.scan()                                  → credentialId → {jobs, folders}
  5. CredentialsDictionaryMapper.mapAll()                 → List<CredentialRecord>
     └─ AnnotationMapper.map(cred)                        → conjurization block
  6. Collect FolderRecord + JobRecord lists
  7. Build DiscoverySnapshot (includes jwksUri, jwksData, kid, subdomain, ...)
  8. DiscoExportClient.send(snapshot, resolvedUrl, bearerToken, ...)
     └─ HTTP POST with Bearer auth + X-Jenkins-Instance-ID header
  9. On 200/202 → update lastExportTimestamp + log DISC_007
```

### Rate limiting & concurrency
- `volatile boolean isRunning` singleton guard — only one run at a time.
- Manual trigger: blocked if `(now - lastExportTimestamp) < 1h` unless `testEnvironment=true`.
- Scheduled trigger (CRON): bypasses the manual rate-limit check.
- `subdomain` empty → hard abort before any network call.

### Credential export modes
| `exportSecretValues` | `storeProvider` | Result |
|---|---|---|
| `true` | SystemCredentialsProvider / FolderCredentialsProvider | fields + encrypted values |
| `true` | External (HashiCorp, Azure, etc.) | fields only (no values) |
| `false` | any | fields only (no values) |

---

## Tests

36 original + 5 new DisCo tests in `src/test/java/org/conjur/jenkins/`:

- Framework: **JUnit 4.13.2** + **Mockito 3.11.2** + **AssertJ 3.15.0**
- DisCo tests are in `disco/` package and do **not** start a Jenkins instance.
- `DiscoExporterConfigurationTest` uses a stub subclass to bypass `Jenkins.get()`.
- `DiscoveryOrchestratorTest` uses a `TestableOrchestrator` inner subclass to test guard conditions without network.

---

## Dependencies Worth Knowing

| Artifact | Version | Used for |
|---|---|---|
| `okhttp` | 4.12.0 | All outbound HTTP |
| `gson` | 2.8.9 | JSON in DisCo pipeline |
| `jose4j` | 0.9.6 | JWT / JWK operations |
| `cloudbees-folder` | BOM | Folder hierarchy traversal |
| `ssh-credentials` | BOM | `SSHUserPrivateKey` in AnnotationMapper |
| `plain-credentials` | BOM | `StringCredentials` in AnnotationMapper / DiscoExportClient |
| `workflow-job` | BOM | `WorkflowJob` in UsageTracker |

---

## What Is NOT in this repo (DisCo/SMS platform owns these)

- Ingestion logic (mapping credential classes to SMS templates)
- Conjur policy generation from DisCo annotations
- TTL management for exported secrets
- Regional API endpoint definitions (returned dynamically by DiscoveryServiceClient)
