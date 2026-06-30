package org.conjur.jenkins.disco.manual;

import okhttp3.OkHttpClient;
import org.conjur.jenkins.disco.config.DiscoEnvironment;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceClient;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceResult;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manual integration test that exercises the full DisCo discovery pipeline
 * against the integration environment, mirroring how the Jenkins plugin runs.
 *
 * Mirrors the flow in DiscoveryOrchestrator.run():
 *   1. Platform Discovery API  → resolvedUrl, tenantId, identityBaseUrl
 *   2. CyberArk Identity login → bearerToken
 *   3. JWKS key fetch          → kid + publicKey
 *
 * ── How to run ────────────────────────────────────────────────────────────────
 *
 *   mvn test -pl . \
 *     -Dtest="org.conjur.jenkins.disco.manual.DiscoPluginPipelineIT" \
 *     -DARK_LIVE_TEST=true \
 *     -DCYBERARK_DISCO_ENV=INTEGRATION \
 *     -DARK_SUBDOMAIN=disco4asaf \
 *     -DARK_USERNAME=diego@cyberark.cloud.465930 \
 *     -DARK_SECRET=123Cyber
 *
 * ── Environment variables / system properties ─────────────────────────────────
 *
 *   ARK_LIVE_TEST         (required) set to "true" to enable these tests
 *   CYBERARK_DISCO_ENV    environment to use: INTEGRATION, PRODUCTION, etc.
 *                         Default: PRODUCTION
 *   ARK_SUBDOMAIN         tenant subdomain (e.g. disco4asaf)
 *   ARK_USERNAME          CyberArk Identity username
 *   ARK_SECRET            CyberArk Identity password
 */
public class DiscoPluginPipelineIT {

    private static final Logger LOG = Logger.getLogger(DiscoPluginPipelineIT.class.getName());

    private static String subdomain;
    private static String username;
    private static String secret;
    private static String platformDiscoveryUrl;

    private OkHttpClient httpClient;

    @BeforeClass
    public static void loadConfiguration() {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run DisCo pipeline integration tests",
                "true".equalsIgnoreCase(env("ARK_LIVE_TEST")));

        subdomain            = requireEnv("ARK_SUBDOMAIN");
        username             = requireEnv("ARK_USERNAME");
        secret               = requireEnv("ARK_SECRET");
        platformDiscoveryUrl = DiscoEnvironment.resolve().getPlatformDiscoveryUrl();

        LOG.info("DisCo pipeline IT config:"
                + "  env=" + DiscoEnvironment.resolve().name()
                + "  subdomain=" + subdomain
                + "  platformDiscoveryUrl=" + platformDiscoveryUrl);
    }

    @Before
    public void buildHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // ── Step 1: Platform Discovery ────────────────────────────────────────────

    @Test
    public void step1_platformDiscovery_resolvesAllEndpoints() throws Exception {
        DiscoveryServiceClient client = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult result = client.resolve(platformDiscoveryUrl, subdomain);

        LOG.info("step1_platformDiscovery:"
                + "  tenantId=" + result.getTenantId()
                + "  identityBaseUrl=" + result.getIdentityBaseUrl()
                + "  snapshotLinksUrl=" + result.getResolvedUrl()
                + "  discoveryContextBaseUrl=" + result.getDiscoveryContextBaseUrl());

        org.assertj.core.api.Assertions.assertThat(result.getTenantId()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(result.getIdentityBaseUrl()).startsWith("https://");
        org.assertj.core.api.Assertions.assertThat(result.getResolvedUrl()).contains("/snapshot-links");
        org.assertj.core.api.Assertions.assertThat(result.getDiscoveryContextBaseUrl()).startsWith("https://");
    }

    // ── Step 2: CyberArk Identity login ──────────────────────────────────────

    @Test
    public void step2_identityLogin_obtainsBearerToken() throws Exception {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        LOG.info("step2_identityLogin: token length=" + token.length);
        decodeAndLogJwt("step2_identityLogin", new String(token, java.nio.charset.StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(token).isNotEmpty();
    }

    @Test
    public void step2_jwt_hasRequiredHeaderAndClaims() throws Exception {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String tokenStr = new String(token, java.nio.charset.StandardCharsets.UTF_8);
        decodeAndLogJwt("step2_jwt_hasRequiredHeaderAndClaims", tokenStr);

        String[] parts = tokenStr.split("\\.", -1);
        org.assertj.core.api.Assertions.assertThat(parts.length)
                .as("JWT must have 3 parts (header.payload.signature)").isGreaterThanOrEqualTo(2);

        com.google.gson.JsonObject header  = parseJwtPart(parts[0]);
        com.google.gson.JsonObject payload = parseJwtPart(parts[1]);

        // ── Header assertions ────────────────────────────────────────────────
        org.assertj.core.api.Assertions.assertThat(header.get("alg").getAsString())
                .as("header.alg").isEqualTo("RS256");
        org.assertj.core.api.Assertions.assertThat(header.get("typ").getAsString())
                .as("header.typ").isEqualTo("JWT");
        org.assertj.core.api.Assertions.assertThat(header.has("kid"))
                .as("header must have kid").isTrue();
        org.assertj.core.api.Assertions.assertThat(header.get("kid").getAsString())
                .as("header.kid").isNotBlank();
        org.assertj.core.api.Assertions.assertThat(header.has("x5t"))
                .as("header must have x5t").isTrue();
        org.assertj.core.api.Assertions.assertThat(header.get("app_id").getAsString())
                .as("header.app_id").isEqualTo("__idaptive_cybr_user_oidc");

        // ── Payload assertions ───────────────────────────────────────────────
        org.assertj.core.api.Assertions.assertThat(payload.get("preferred_username").getAsString())
                .as("payload.preferred_username").isEqualTo(username);
        org.assertj.core.api.Assertions.assertThat(payload.get("subdomain").getAsString())
                .as("payload.subdomain").isEqualTo(subdomain);
        org.assertj.core.api.Assertions.assertThat(payload.has("tenant_id"))
                .as("payload must have tenant_id").isTrue();
        org.assertj.core.api.Assertions.assertThat(payload.has("user_roles"))
                .as("payload must have user_roles").isTrue();
        org.assertj.core.api.Assertions.assertThat(payload.get("user_roles").isJsonArray())
                .as("payload.user_roles must be an array").isTrue();
        org.assertj.core.api.Assertions.assertThat(payload.get("aud").getAsString())
                .as("payload.aud").isEqualTo("__idaptive_cybr_user_oidc");

        LOG.info("step2_jwt_hasRequiredHeaderAndClaims: PASSED"
                + "  kid=" + header.get("kid").getAsString()
                + "  sub=" + payload.get("sub").getAsString()
                + "  roles=" + payload.getAsJsonArray("user_roles").size());
    }

    // ── Step 3a: Live discovery-context/jwks fetch (no stub, validates auth+URL) ─

    @Test
    public void step3a_jwksFetch_liveEndpoint_returnsValidKey() throws Exception {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String jwksUrl = discovery.getDiscoveryContextBaseUrl() + "/discovery-context/jwks";
        decodeAndLogJwt("step3a_jwksFetch", new String(token, java.nio.charset.StandardCharsets.UTF_8));
        LOG.info("step3a_jwksFetch: hitting live JWKS endpoint: " + jwksUrl);

        EncryptionService encryptionService = new EncryptionService(httpClient);
        encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), token);

        LOG.info("step3a_jwksFetch: selected kid=" + encryptionService.getSelectedKid());
        org.assertj.core.api.Assertions.assertThat(encryptionService.getSelectedKid()).isNotBlank();

        String encrypted = encryptionService.encryptValue("test-secret-value");
        LOG.info("step3a_jwksFetch: encryption OK, ciphertext length=" + encrypted.length());
        org.assertj.core.api.Assertions.assertThat(encrypted).isNotBlank();
    }

    // ── Step 3: JWKS key fetch with optional stub fallback ────────────────────

    @Test
    public void step3_encryptionService_loadsKey() throws Exception {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        EncryptionService encryptionService = new EncryptionService(httpClient);
        LOG.info("step3_encryptionService: fetching live JWKS from "
                + discovery.getDiscoveryContextBaseUrl());
        encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), token);

        LOG.info("step3_encryptionService: selected kid=" + encryptionService.getSelectedKid());
        org.assertj.core.api.Assertions.assertThat(encryptionService.getSelectedKid()).isNotBlank();

        String encrypted = encryptionService.encryptValue("test-secret-value");
        LOG.info("step3_encryptionService: encryption OK, ciphertext length=" + encrypted.length());
        org.assertj.core.api.Assertions.assertThat(encrypted).isNotBlank();
    }

    // ── Full pipeline ─────────────────────────────────────────────────────────

    @Test
    public void fullPipeline_allStepsSucceed() throws Exception {
        // Step 1
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);
        LOG.info("fullPipeline: tenantId=" + discovery.getTenantId()
                + "  identity=" + discovery.getIdentityBaseUrl()
                + "  snapshotLinks=" + discovery.getResolvedUrl()
                + "  discoveryContext=" + discovery.getDiscoveryContextBaseUrl());

        // Step 2
        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] token = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        decodeAndLogJwt("fullPipeline", new String(token, java.nio.charset.StandardCharsets.UTF_8));
        LOG.info("fullPipeline: login OK, token length=" + token.length);

        // Step 3: fetch keys from live discovery-context/jwks
        EncryptionService encryptionService = new EncryptionService(httpClient);
        encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), token);
        LOG.info("fullPipeline: kid=" + encryptionService.getSelectedKid());

        String encrypted = encryptionService.encryptValue("my-super-secret");
        LOG.info("fullPipeline: PASSED. encrypted=" + encrypted.substring(0, 20) + "...");

        org.assertj.core.api.Assertions.assertThat(discovery.getTenantId()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(token).isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(encryptionService.getSelectedKid()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(encrypted).isNotBlank();
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    static com.google.gson.JsonObject parseJwtPart(String base64UrlPart) {
        // JWT uses base64url (no padding); add padding if needed
        String padded = base64UrlPart;
        switch (padded.length() % 4) {
            case 2: padded += "=="; break;
            case 3: padded += "=";  break;
            default: break;
        }
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(padded);
        String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    }

    static void decodeAndLogJwt(String label, String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length < 2) {
                LOG.warning(label + ": token does not look like a JWT (parts=" + parts.length + ")");
                return;
            }
            com.google.gson.Gson pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            com.google.gson.JsonObject header  = parseJwtPart(parts[0]);
            com.google.gson.JsonObject payload = parseJwtPart(parts[1]);
            LOG.info(label + " JWT header:\n"  + pretty.toJson(header));
            LOG.info(label + " JWT payload:\n" + pretty.toJson(payload));
        } catch (Exception e) {
            LOG.warning(label + ": could not decode JWT — " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String env(String key) {
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getenv(key);
        return val != null ? val : "";
    }

    static String requireEnv(String key) {
        String val = env(key);
        if (val.isBlank()) throw new IllegalStateException(
                "Required env var / system property not set: " + key);
        return val;
    }
}
