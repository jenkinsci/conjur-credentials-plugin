# FIS Integration Tests — Step-by-Step Guide

Manual integration tests that upload Jenkins discovery snapshots to the live
CyberArk FIS (File Ingestion Service) endpoint.  They are excluded from the
normal `mvn test` run and only execute when `-DARK_LIVE_TEST=true` is supplied.

---

## Prerequisites

- Java 11 and Maven 3.x installed
- Network access to `*.integration-cyberark.cloud`
- A CyberArk Identity account with access to the `your-tenant` integration tenant

---

## Step 1 — Create the credentials file

The credentials file lives at `integration-tests/.env` inside the project.
It is already listed in `.gitignore` and will never be committed.

```bash
# From the project root
cp integration-tests/.env.example integration-tests/.env
```

Open `integration-tests/.env` in any editor and set the line:

```
CREDENTIALS=Tina:user@cyberark.cloud.123456:YourPassword
```

Format is always `Tina:<username>:<password>`.  The `Tina:` prefix is
required — it matches the convention used by the Python FIS e2e suite.

Verify the file looks correct:

```bash
cat integration-tests/.env
# Expected output:
# CREDENTIALS=Tina:user@cyberark.cloud.123456:YourPassword
```

---

## Step 2 — Create the endpoint configuration file

The file `integration-tests/env_details_config.json` tells the tests which
FIS endpoint URL, tenant ID, and tenant name to use.

It is already present in this project at `integration-tests/env_details_config.json`.
If it is missing, create it manually:

```bash
cat > integration-tests/env_details_config.json << 'EOF'
{
  "integration": {
    "us-east-1": {
      "name": "Integration (US east 1)",
      "tenant_name": "your-tenant",
      "tenant_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "tenant_url": "https://your-tenant.integration-cyberark.cloud",
      "fis_api_endpoint": "https://your-tenant.inventory.integration-cyberark.cloud"
    }
  }
}
EOF
```

Verify it:

```bash
cat integration-tests/env_details_config.json
```

---

> **Important:** Because the surefire plugin in this project uses a static
> `<excludes>**/manual/**</excludes>` rule, manual tests must be specified
> using their **fully qualified class names** (package + class).  Short names
> like `-Dtest=FisJenkinsSnapshotIT` are silently filtered out.

---

## Step 3 — Run a single test to verify the setup

Run just the template-based upload test first.  This is the fastest sanity
check (~7 seconds).

```bash
mvn test -pl . \
  -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsSnapshotIT#scenario_uploadJenkinsSnapshot" \
  -DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_ENV_FILE=integration-tests/.env \
  -DARK_ENV_DETAILS=integration-tests/env_details_config.json \
  -DARK_ENV=integration \
  -DARK_REGION=us-east-1
```

Expected output (last few lines):

```
INFO: Login successful, token length=...
INFO: DISC_007: Payload size=... bytes  sha256=...
INFO: DISC_007: Presigned URL obtained.
INFO: DISC_007: S3 upload successful (HTTP 200).
INFO: scenario_uploadJenkinsSnapshot: PASSED (identifier=cda632057611ea5a...)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Step 4 — Run all template-based tests (`FisJenkinsSnapshotIT`)

```bash
mvn test -pl . \
  -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsSnapshotIT" \
  -DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_ENV_FILE=integration-tests/.env \
  -DARK_ENV_DETAILS=integration-tests/env_details_config.json \
  -DARK_ENV=integration \
  -DARK_REGION=us-east-1
```

This runs 2 tests:
- `scenario_uploadJenkinsSnapshot` — upload `jenkins-snapshot.json` template
- `scenario_uploadAndCleanupJenkinsSnapshot` — upload full then empty cleanup

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

---

## Step 5 — Run in-memory structure export tests (`FisJenkinsStructureExportIT`)

These build the Jenkins structure (folders, jobs, credentials) entirely in
Java — no Jenkins instance required.

```bash
mvn test -pl . \
  -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsStructureExportIT" \
  -DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_ENV_FILE=integration-tests/.env \
  -DARK_ENV_DETAILS=integration-tests/env_details_config.json \
  -DARK_ENV=integration \
  -DARK_REGION=us-east-1
```

This runs 5 tests:
- `scenario_minimalSnapshot_singleCredential` — one credential, no jobs or folders
- `scenario_fullHierarchySnapshot` — root → team → team/finance + 3 jobs + 3 credentials
- `scenario_largeCredentialList` — 50 credentials, 5 folders, 10 pipelines
- `scenario_uploadThenCleanup` — full snapshot followed by empty cleanup
- `scenario_logAndUploadSnapshot` — prints the full JSON payload to console then uploads

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

---

## Step 6 — Run Conjur credential type tests (`FisConjurCredentialsExportIT`)

These use the plugin's own Conjur-specific credential classes and wire them
to mock pipeline/freestyle jobs.

```bash
mvn test -pl . \
  -Dtest="org.conjur.jenkins.disco.manual.FisConjurCredentialsExportIT" \
  -DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_ENV_FILE=integration-tests/.env \
  -DARK_ENV_DETAILS=integration-tests/env_details_config.json \
  -DARK_ENV=integration \
  -DARK_REGION=us-east-1
```

This runs 8 tests:

| Test | Credential class | DisCo type |
|---|---|---|
| `scenario_conjurSecretCredential` | `ConjurSecretCredentialsImpl` | `stringcredential` |
| `scenario_conjurSecretStringCredential` | `ConjurSecretStringCredentialsImpl` | `stringcredential` |
| `scenario_conjurSecretUsernameCredential` | `ConjurSecretUsernameCredentialsImpl` | `usernamecredential` |
| `scenario_conjurSecretSSHKeyCredential` | `ConjurSecretUsernameSSHKeyCredentialsImpl` | `usernamesshkeycredential` |
| `scenario_conjurSecretFileCredential` | `ConjurSecretFileCredentialsImpl` | `filecredential` |
| `scenario_conjurSecretDockerCertCredential` | `ConjurSecretDockerCertCredentialsImpl` | `dockercertcredential` |
| `scenario_allConjurCredentialTypes` | All 6 types in one snapshot | Mixed |
| `scenario_largeConjurSnapshot_100x100x100` | 100 creds, 111 folders, 103 jobs (110 KB) | Mixed |

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

---

## Step 7 — Run all integration tests (one class at a time)

Surefire does not support combining multiple manual classes with `+` due to
the exclude filter.  Run them sequentially:

```bash
COMMON="-DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_ENV_FILE=integration-tests/.env \
  -DARK_ENV_DETAILS=integration-tests/env_details_config.json \
  -DARK_ENV=integration \
  -DARK_REGION=us-east-1"

mvn test -pl . -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsSnapshotIT"          $COMMON
mvn test -pl . -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsStructureExportIT"   $COMMON
mvn test -pl . -Dtest="org.conjur.jenkins.disco.manual.FisConjurCredentialsExportIT"  $COMMON
```

Total: 15 tests across 3 runs.  Expected in each run: 0 failures, 0 errors.

---

## Alternative: pass everything on the command line (no config files)

If you do not have the config files, supply all values directly as `-D` flags:

```bash
mvn test -pl . \
  -Dtest="org.conjur.jenkins.disco.manual.FisJenkinsSnapshotIT" \
  -DARK_LIVE_TEST=true \
  -DARK_IDENTITY_URL=https://yourpod.id.integration-cyberark.cloud \
  -DARK_SUBDOMAIN=your-tenant \
  -DARK_TENANT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx \
  -DARK_USERNAME=user@cyberark.cloud.123456 \
  -DARK_SECRET=YourPassword \
  -DARK_LAMBDA_URL=https://your-tenant.inventory.integration-cyberark.cloud/api/ingestions/jenkins/snapshot-links
```

---

## All environment variables / system properties

| Property | Description | Example value |
|---|---|---|
| `ARK_LIVE_TEST` | Must be `true` to enable tests | `true` |
| `ARK_IDENTITY_URL` | CyberArk Identity pod URL | `https://yourpod.id.integration-cyberark.cloud` |
| `ARK_SUBDOMAIN` | Tenant name | `your-tenant` |
| `ARK_TENANT_ID` | Tenant UUID | `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| `ARK_USERNAME` | Login username | `user@cyberark.cloud.123456` |
| `ARK_SECRET` | Login password | `YourPassword` |
| `ARK_LAMBDA_URL` | Full snapshot-links URL (skips config files) | `https://your-tenant.inventory.integration-cyberark.cloud/api/ingestions/jenkins/snapshot-links` |
| `ARK_FIS_BASE_URL` | Base URL when `ARK_LAMBDA_URL` is absent | `https://your-tenant.inventory.integration-cyberark.cloud` |
| `ARK_ENV_FILE` | Path to `.env` credentials file | `integration-tests/.env` |
| `ARK_ENV_DETAILS` | Path to `env_details_config.json` | `integration-tests/env_details_config.json` |
| `ARK_ENV` | Environment key inside config file | `integration` |
| `ARK_REGION` | Region key inside config file | `us-east-1` |

---

## File layout

```
conjur-credentials-plugin/
├── integration-tests/
│   ├── .env                        ← your credentials (gitignored, copy from .env.example)
│   ├── .env.example                ← template (committed)
│   ├── env_details_config.json     ← endpoint config (gitignored)
│   └── INTEGRATION_TESTS.md        ← this file
└── src/test/java/.../disco/manual/
    ├── FisJenkinsSnapshotIT.java           ← template-file upload tests
    ├── FisJenkinsStructureExportIT.java    ← in-memory structure export tests
    └── FisConjurCredentialsExportIT.java   ← Conjur credential type tests
```

---

## How each test uploads data (pipeline overview)

1. **Login** — two-step CyberArk Identity login via `CyberArkIdentityClient`:
   `POST /Security/StartAuthentication` → `POST /Security/AdvanceAuthentication`.
   Returns a short-lived RS256 JWT bearer token.

2. **Serialize** — assemble a `DiscoverySnapshot` object and serialize it with
   Gson to UTF-8 JSON bytes.

3. **Get presigned URL** — `POST` to the snapshot-links endpoint with:
   `agent_version`, `identifier` (32-char hex), `checksum_sha256` (hex),
   `file_size` (integer), `signature_version: "sigv4"`.
   Response contains a presigned AWS S3 PUT URL.

4. **Upload to S3** — `PUT` the bytes to the presigned URL with headers:
   `x-amz-checksum-sha256` (base64), `x-amz-server-side-encryption: AES256`,
   `x-amz-tagging` (sorted RFC 3986-encoded tags).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Assumption failed: Set ARK_LIVE_TEST=true` | Flag missing | Add `-DARK_LIVE_TEST=true` |
| `HTTP 403 on snapshot-links` | Wrong token or API Gateway block | Verify `ARK_IDENTITY_URL`, username, password |
| `.env file not found` | File not created | Run `cp integration-tests/.env.example integration-tests/.env` |
| `No CREDENTIALS= entry found` | Malformed `.env` | Line must be exactly `CREDENTIALS=Tina:<user>:<pass>` |
| `ARK_IDENTITY_URL is required` | Using config files without identity URL | Always pass `-DARK_IDENTITY_URL=...` explicitly |
| `DISC_008: S3 PUT failed with HTTP 400` | Checksum mismatch | Do not re-encode bytes between sha256 and PUT |
| `DISC_010: Rate limit` | Too many uploads | Wait 60 seconds and retry |
