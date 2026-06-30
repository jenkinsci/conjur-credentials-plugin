package org.conjur.jenkins.disco.manual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Manual FIS (File Ingestion Service) integration tests for the Jenkins snapshot upload pipeline.
 *
 * Mirrors the Python FIS e2e test suite in discoverycontext-e2e/tests/FIS/.
 * Reads credentials from a .env file and environment configuration from
 * env_details_config.json — exactly the same inputs as the Python tests.
 *
 * ── How to run ────────────────────────────────────────────────────────────────
 *
 * 1. Ensure a .env file exists (same one used by the Python suite), e.g.:
 *      CREDENTIALS=Tina:diego@cyberark.cloud.465930:123Cyber
 *
 * 2. Ensure env_details_config.json is populated for the target environment.
 *    Alternatively, run the Python script first:
 *      poetry run python scripts/update_env_config.py \
 *          --tenant-name disco4asaf \
 *          --tenant-id 3ac63bfb-fd47-433a-bb93-2f290e5388b8 \
 *          --env integration --region us-east-1
 *
 * 3. Pass the flag that enables these tests:
 *      mvn test -pl . -Dtest=FisJenkinsSnapshotIT \
 *               -DARK_LIVE_TEST=true \
 *               -DARK_ENV_FILE=/path/to/discoverycontext-e2e/.env \
 *               -DARK_ENV_DETAILS=/path/to/discoverycontext-e2e/env_details_config.json \
 *               -DARK_ENV=integration \
 *               -DARK_REGION=us-east-1
 *
 * ── Environment variables / system properties ─────────────────────────────────
 *
 *   ARK_LIVE_TEST       (required) set to "true" to enable the tests
 *   ARK_ENV_FILE        path to .env file; defaults to ~/discoverycontext-e2e/.env
 *   ARK_ENV_DETAILS     path to env_details_config.json; defaults to the file next to .env
 *   ARK_ENV             environment key in env_details_config.json (default: "integration")
 *   ARK_REGION          region key (default: "us-east-1")
 *
 *   Alternatively, bypass the config files and supply values directly:
 *   ARK_IDENTITY_URL    CyberArk Identity base URL
 *   ARK_SUBDOMAIN       tenant subdomain
 *   ARK_TENANT_ID       tenant UUID
 *   ARK_USERNAME        login username
 *   ARK_SECRET          login password
 *   ARK_LAMBDA_URL      full snapshot-links URL (derived from fis_api_endpoint if absent)
 *
 * ── What these tests do ───────────────────────────────────────────────────────
 *
 *   scenario_uploadJenkinsSnapshot — upload the jenkins-snapshot.json template
 *       with a freshly generated 32-char hex identifier, verifying the full
 *       presigned-S3 upload pipeline works end-to-end.
 *
 *   scenario_uploadAndCleanupJenkinsSnapshot — upload the full snapshot, then
 *       upload the empty cleanup snapshot (same identifier), mirroring the
 *       "Then I upload the Jenkins clean up snapshot file via FIS" step.
 */
public class FisJenkinsSnapshotIT {

    private static final Logger LOG = Logger.getLogger(FisJenkinsSnapshotIT.class.getName());

    // Jenkins uses 32-char hex identifiers — mirrors _generate_identifier("JENKINS") in fis_upload_steps.py
    private static final String IDENTIFIER_PLACEHOLDER = "<IDENTIFIER>";
    private static final String JENKINS_API_SUFFIX     = "/api/ingestions/jenkins/snapshot-links";

    // Loaded once per test class from .env + env_details_config.json
    private static String identityUrl;
    private static String subdomain;
    private static String tenantId;
    private static String username;
    private static String secret;
    private static String fisBaseUrl;    // base URL only (no path suffix)
    private static String snapshotLinksUrl; // full URL, used when ARK_LAMBDA_URL is the full path

    private OkHttpClient httpClient;

    // =========================================================================
    // Setup — parse .env + env_details_config.json, mirrors conftest.py fixtures
    // =========================================================================

    @BeforeClass
    public static void loadConfiguration() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run FIS integration tests",
                "true".equalsIgnoreCase(env("ARK_LIVE_TEST")));

        // Prefer explicit overrides; fall back to config-file derived values
        if (!env("ARK_IDENTITY_URL").isBlank() && !env("ARK_USERNAME").isBlank()) {
            identityUrl = requireEnv("ARK_IDENTITY_URL");
            subdomain   = requireEnv("ARK_SUBDOMAIN");
            tenantId    = requireEnv("ARK_TENANT_ID");
            username    = requireEnv("ARK_USERNAME");
            secret      = requireEnv("ARK_SECRET");
            // ARK_LAMBDA_URL is the full URL including path — use it directly
            String lambdaUrl = env("ARK_LAMBDA_URL");
            if (!lambdaUrl.isBlank()) {
                snapshotLinksUrl = lambdaUrl;
                // Derive fisBaseUrl by stripping the known suffix so config-file path also works
                fisBaseUrl = lambdaUrl.contains(JENKINS_API_SUFFIX)
                        ? lambdaUrl.substring(0, lambdaUrl.indexOf(JENKINS_API_SUFFIX))
                        : lambdaUrl;
            } else {
                fisBaseUrl       = requireEnv("ARK_FIS_BASE_URL");
                snapshotLinksUrl = fisBaseUrl + JENKINS_API_SUFFIX;
            }
        } else {
            loadFromConfigFiles();
        }

        LOG.info("FIS config: env=" + env("ARK_ENV", "integration")
                + "  region=" + env("ARK_REGION", "us-east-1")
                + "  subdomain=" + subdomain
                + "  fisBase=" + fisBaseUrl);
    }

    private static void loadFromConfigFiles() throws Exception {
        // 1. Read .env for credentials
        String envFilePath = env("ARK_ENV_FILE");
        if (envFilePath.isBlank()) {
            // Default: integration-tests/.env inside this project (copy from .env.example)
            Path candidate = Paths.get(System.getProperty("user.dir"), "integration-tests", ".env");
            envFilePath = candidate.toString();
        }
        Map<String, String> dotEnv = parseDotEnv(envFilePath);
        String credentials = dotEnv.getOrDefault("CREDENTIALS", "");
        if (credentials.isBlank()) {
            throw new IllegalStateException(
                    "No CREDENTIALS= entry found in .env at: " + envFilePath);
        }
        // Format: Tina:<username>:<password>  (matches Python's CredentialManager)
        String[] parts = credentials.split(":", 3);
        if (parts.length < 3) {
            throw new IllegalStateException(
                    "CREDENTIALS must be in format Tina:<username>:<password>, got: " + credentials);
        }
        username = parts[1];
        secret   = parts[2];

        // 2. Read env_details_config.json
        String envDetailsPath = env("ARK_ENV_DETAILS");
        if (envDetailsPath.isBlank()) {
            // Default: sibling directory to the .env file
            envDetailsPath = Paths.get(envFilePath).getParent()
                    .resolve("env_details_config.json").toString();
        }
        String envKey    = env("ARK_ENV", "integration");
        String regionKey = env("ARK_REGION", "us-east-1");

        JsonObject envDetails = parseEnvDetails(envDetailsPath, envKey, regionKey);
        tenantId         = envDetails.get("tenant_id").getAsString();
        subdomain        = envDetails.get("tenant_name").getAsString();
        fisBaseUrl       = envDetails.get("fis_api_endpoint").getAsString();
        snapshotLinksUrl = fisBaseUrl + JENKINS_API_SUFFIX;

        // Derive identity URL: format https://<subdomain prefix>.id.<platform_domain>
        // Read from explicit override or use a well-known pattern
        identityUrl = env("ARK_IDENTITY_URL");
        if (identityUrl.isBlank()) {
            // Derive from tenant_url pattern: replace tenant subdomain with identity subdomain
            String tenantUrl = envDetails.get("tenant_url").getAsString();
            // e.g. https://disco4asaf.integration-cyberark.cloud → https://aoj5620.id.integration-cyberark.cloud
            // The identity URL cannot be derived from tenant_url without knowing the pod ID;
            // require explicit ARK_IDENTITY_URL when config files are used
            throw new IllegalStateException(
                    "ARK_IDENTITY_URL is required when using config files. "
                    + "The identity pod URL cannot be derived from tenant_url: " + tenantUrl);
        }
    }

    @Before
    public void buildHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    // =========================================================================
    // Scenario 1: Upload Jenkins snapshot
    // Mirrors: "Given I upload the JENKINS snapshot file via FIS"
    // =========================================================================

    /**
     * Upload a Jenkins snapshot with 2 credentials and 2 jobs.
     * Uses a freshly-generated 32-char hex identifier so the upload does not
     * collide with previous test runs (mirrors _generate_identifier("JENKINS")).
     */
    @Test
    public void scenario_uploadJenkinsSnapshot() throws Exception {
        String identifier = generateJenkinsIdentifier();
        LOG.info("scenario_uploadJenkinsSnapshot: identifier=" + identifier);

        byte[] token   = login();
        byte[] payload = loadTemplate("fis/jenkins-snapshot.json", identifier);

        DiscoExportClient client = new DiscoExportClient(httpClient);
        client.uploadViaSnapshotLinks(payload, snapshotLinksUrl, token,
                "v2.0.0", tenantId, username, identifier);

        LOG.info("scenario_uploadJenkinsSnapshot: PASSED (identifier=" + identifier + ")");
    }

    // =========================================================================
    // Scenario 2: Upload snapshot then cleanup (empty) snapshot
    // Mirrors: "Given I upload the JENKINS snapshot file via FIS"
    //          "Then I upload the JENKINS clean up snapshot file via FIS"
    // =========================================================================

    /**
     * Upload a full Jenkins snapshot then immediately upload an empty cleanup
     * snapshot for the same identifier, leaving the tenant clean.
     */
    @Test
    public void scenario_uploadAndCleanupJenkinsSnapshot() throws Exception {
        String identifier = generateJenkinsIdentifier();
        byte[] token      = login();

        LOG.info("scenario_uploadAndCleanupJenkinsSnapshot: uploading full snapshot, identifier=" + identifier);
        DiscoExportClient client = new DiscoExportClient(httpClient);

        byte[] fullPayload = loadTemplate("fis/jenkins-snapshot.json", identifier);
        client.uploadViaSnapshotLinks(fullPayload, snapshotLinksUrl, token,
                "v2.0.0", tenantId, username, identifier);
        LOG.info("scenario_uploadAndCleanupJenkinsSnapshot: full snapshot uploaded");

        // Re-login to get a fresh token (in case the 15-min TTL cache was invalidated)
        token = login();

        byte[] emptyPayload = loadTemplate("fis/jenkins-snapshot-empty.json", identifier);
        client.uploadViaSnapshotLinks(emptyPayload, snapshotLinksUrl, token,
                "v2.0.0", tenantId, username, identifier);
        LOG.info("scenario_uploadAndCleanupJenkinsSnapshot: cleanup snapshot uploaded — PASSED");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Two-step CyberArk Identity login; returns the bearer token. */
    private byte[] login() throws Exception {
        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(identityUrl, subdomain, username,
                secret.getBytes(StandardCharsets.UTF_8));
        LOG.info("Login successful, token length=" + token.length);
        return token;
    }

    /**
     * Load a JSON template from the test classpath, substituting every
     * occurrence of {@code <IDENTIFIER>} with the supplied identifier.
     * Mirrors _upload_fis_snapshot_from_template() in fis_upload_steps.py.
     */
    private static byte[] loadTemplate(String classpathResource, String identifier) throws IOException {
        InputStream in = FisJenkinsSnapshotIT.class.getClassLoader()
                .getResourceAsStream(classpathResource);
        if (in == null) {
            throw new IOException("Classpath resource not found: " + classpathResource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            return content.replace(IDENTIFIER_PLACEHOLDER, identifier)
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Generate a 32-char lowercase hex identifier for a Jenkins controller.
     * Mirrors _generate_identifier("JENKINS") in fis_upload_steps.py.
     */
    private static String generateJenkinsIdentifier() {
        var rng = RandomGenerator.getDefault();
        var sb  = new StringBuilder(32);
        String hex = "0123456789abcdef";
        for (int i = 0; i < 32; i++) sb.append(hex.charAt(rng.nextInt(16)));
        return sb.toString();
    }

    /**
     * Parse a .env file into a key→value map.
     * Ignores blank lines and lines starting with #.
     * Strips surrounding quotes from values.
     * Mirrors python-dotenv behaviour used by the Python suite.
     */
    static Map<String, String> parseDotEnv(String filePath) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException(".env file not found: " + filePath);
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            String key   = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            // Strip surrounding single or double quotes
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * Read env_details_config.json and return the JsonObject for the given
     * environment + region. Mirrors how conftest.py reads env_details.
     */
    static JsonObject parseEnvDetails(String filePath, String envKey, String regionKey) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("env_details_config.json not found: " + filePath);
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has(envKey)) {
            throw new IllegalArgumentException(
                    "Environment key '" + envKey + "' not found in " + filePath);
        }
        JsonObject envObj = root.getAsJsonObject(envKey);
        if (!envObj.has(regionKey)) {
            throw new IllegalArgumentException(
                    "Region key '" + regionKey + "' not found under environment '"
                    + envKey + "' in " + filePath);
        }
        return envObj.getAsJsonObject(regionKey);
    }

    /** Read from system property first, then env var. Returns "" if absent. */
    static String env(String key) {
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getenv(key);
        return val != null ? val : "";
    }

    /** Read from system property / env var with a default fallback. */
    static String env(String key, String defaultValue) {
        String val = env(key);
        return val.isBlank() ? defaultValue : val;
    }

    static String requireEnv(String key) {
        String val = env(key);
        if (val.isBlank()) {
            throw new IllegalStateException(
                    "Required env var / system property not set: " + key);
        }
        return val;
    }
}
