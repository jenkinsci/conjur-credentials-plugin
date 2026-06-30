package org.conjur.jenkins.disco.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.conjur.jenkins.disco.config.DiscoEnvironment;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceClient;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceResult;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Manual integration tests for the CyberArk discovery-context JWKS endpoint.
 *
 * Tests the endpoint directly (raw HTTP) and through EncryptionService to validate:
 *   - HTTP 200 + JSON content-type
 *   - Response body contains a "keys" array
 *   - Each key has the required JWK fields (kty, kid, n, e for RSA)
 *   - Unauthenticated request is rejected (401 or 403)
 *   - EncryptionService can fetch and encrypt end-to-end
 *
 * ── How to run ────────────────────────────────────────────────────────────────
 *
 *   mvn test -pl . \
 *     -Dtest="org.conjur.jenkins.disco.manual.JwksEndpointIT" \
 *     -DARK_LIVE_TEST=true \
 *     -DCYBERARK_DISCO_ENV=INTEGRATION \
 *     -DARK_SUBDOMAIN=disco4asaf \
 *     -DARK_USERNAME=diego@cyberark.cloud.465930 \
 *     -DARK_SECRET=123Cyber
 *
 * Run a single test:
 *   -Dtest="org.conjur.jenkins.disco.manual.JwksEndpointIT#jwksEndpoint_unauthenticated_isRejected"
 *
 * ── Environment variables / system properties ─────────────────────────────────
 *
 *   ARK_LIVE_TEST          (required) set to "true" to enable these tests
 *   CYBERARK_DISCO_ENV     INTEGRATION | PRODUCTION (default: PRODUCTION)
 *   ARK_SUBDOMAIN          tenant subdomain (e.g. disco4asaf)
 *   ARK_USERNAME           CyberArk Identity username
 *   ARK_SECRET             CyberArk Identity password
 */
public class JwksEndpointIT {

    private static final Logger LOG = Logger.getLogger(JwksEndpointIT.class.getName());

    private static String jwksUrl;
    private static String subdomain;
    private static String username;
    private static String secret;
    private static String platformDiscoveryUrl;

    private OkHttpClient httpClient;

    @BeforeClass
    public static void loadConfiguration() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run JWKS endpoint integration tests",
                "true".equalsIgnoreCase(env("ARK_LIVE_TEST")));

        subdomain            = requireEnv("ARK_SUBDOMAIN");
        username             = requireEnv("ARK_USERNAME");
        secret               = requireEnv("ARK_SECRET");
        platformDiscoveryUrl = DiscoEnvironment.resolve().getPlatformDiscoveryUrl();

        // Resolve the JWKS URL once — reused across all tests in this class
        OkHttpClient bootstrap = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(bootstrap);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);
        jwksUrl = discovery.getDiscoveryContextBaseUrl() + "/discovery-context/jwks";

        LOG.info("JwksEndpointIT config:"
                + "  env=" + DiscoEnvironment.resolve().name()
                + "  subdomain=" + subdomain
                + "  jwksUrl=" + jwksUrl);
    }

    @Before
    public void buildHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // ── HTTP basics ───────────────────────────────────────────────────────────

    @Test
    public void jwksEndpoint_browserUserAgent_returns200() throws Exception {
        String token = login();

        Request request = new Request.Builder()
                .url(jwksUrl)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", "Jenkins-Scanner/3.1.0")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            LOG.info("jwksEndpoint_browserUserAgent: status=" + response.code()
                    + "  body=" + body.substring(0, Math.min(300, body.length())));
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void jwksEndpoint_authenticatedRequest_returns200() throws Exception {
        String token = login();

        try (Response response = get(jwksUrl, token)) {
            LOG.info("jwksEndpoint_returns200: status=" + response.code());
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void jwksEndpoint_authenticatedRequest_returnsJsonContentType() throws Exception {
        String token = login();

        try (Response response = get(jwksUrl, token)) {
            String contentType = response.header("Content-Type", "");
            LOG.info("jwksEndpoint_contentType: " + contentType);
            assertThat(contentType).containsIgnoringCase("application/json");
        }
    }

    // ── Response body structure ───────────────────────────────────────────────

    @Test
    public void jwksEndpoint_responseBody_containsKeysArray() throws Exception {
        String token = login();
        JsonObject body = fetchJson(token);

        LOG.info("jwksEndpoint_keysArray: body keys=" + body.keySet());
        assertThat(body.has("keys")).as("response must have a 'keys' array").isTrue();
        assertThat(body.get("keys").isJsonArray()).isTrue();
    }

    @Test
    public void jwksEndpoint_keysArray_isNonEmpty() throws Exception {
        String token = login();
        JsonObject body = fetchJson(token);

        JsonArray keys = body.getAsJsonArray("keys");
        LOG.info("jwksEndpoint_nonEmpty: keys.size=" + keys.size());
        assertThat(keys.size()).as("keys array must not be empty").isGreaterThan(0);
    }

    @Test
    public void jwksEndpoint_eachKey_hasKidField() throws Exception {
        String token = login();
        JsonArray keys = fetchJson(token).getAsJsonArray("keys");

        for (int i = 0; i < keys.size(); i++) {
            JsonObject key = keys.get(i).getAsJsonObject();
            assertThat(key.has("kid"))
                    .as("key[%d] must have 'kid' field", i).isTrue();
            assertThat(key.get("kid").getAsString())
                    .as("key[%d].kid must not be blank", i).isNotBlank();
        }
        LOG.info("jwksEndpoint_hasKid: all " + keys.size() + " key(s) have kid");
    }

    @Test
    public void jwksEndpoint_eachKey_hasRsaFields() throws Exception {
        String token = login();
        JsonArray keys = fetchJson(token).getAsJsonArray("keys");

        for (int i = 0; i < keys.size(); i++) {
            JsonObject key = keys.get(i).getAsJsonObject();
            String kty = key.has("kty") ? key.get("kty").getAsString() : "";
            LOG.info("jwksEndpoint_rsaFields: key[" + i + "] kty=" + kty
                    + " kid=" + (key.has("kid") ? key.get("kid").getAsString() : "(none)")
                    + " alg=" + (key.has("alg") ? key.get("alg").getAsString() : "(none)"));

            assertThat(kty).as("key[%d].kty must be RSA", i).isEqualTo("RSA");
            assertThat(key.has("n")).as("key[%d] must have modulus 'n'", i).isTrue();
            assertThat(key.has("e")).as("key[%d] must have exponent 'e'", i).isTrue();
            assertThat(key.get("n").getAsString()).isNotBlank();
            assertThat(key.get("e").getAsString()).isNotBlank();
        }
    }

    // ── Authentication enforcement ────────────────────────────────────────────

    @Test
    public void jwksEndpoint_unauthenticated_isRejected() throws Exception {
        Request request = new Request.Builder()
                .url(jwksUrl)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            LOG.info("jwksEndpoint_unauthenticated: status=" + response.code());
            assertThat(response.code())
                    .as("unauthenticated request should be rejected with 401 or 403")
                    .isIn(401, 403);
        }
    }

    @Test
    public void jwksEndpoint_invalidToken_isRejected() throws Exception {
        try (Response response = get(jwksUrl, "this-is-not-a-valid-token")) {
            LOG.info("jwksEndpoint_invalidToken: status=" + response.code());
            assertThat(response.code())
                    .as("invalid bearer token should be rejected with 401 or 403")
                    .isIn(401, 403);
        }
    }

    // ── EncryptionService integration ─────────────────────────────────────────

    @Test
    public void encryptionService_fetchLatestKeys_selectsKid() throws Exception {
        String token = login();

        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        EncryptionService encryptionService = new EncryptionService(httpClient);
        encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), token.getBytes(StandardCharsets.UTF_8));

        String kid = encryptionService.getSelectedKid();
        LOG.info("encryptionService_fetchLatestKeys: selected kid=" + kid);
        assertThat(kid).isNotBlank();
    }

    @Test
    public void encryptionService_canEncryptAfterFetch() throws Exception {
        String token = login();

        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        EncryptionService encryptionService = new EncryptionService(httpClient);
        encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), token.getBytes(StandardCharsets.UTF_8));

        String plaintext = "integration-test-secret";
        String ciphertext = encryptionService.encryptValue(plaintext);

        LOG.info("encryptionService_canEncrypt: ciphertext length=" + ciphertext.length()
                + " kid=" + encryptionService.getSelectedKid());
        assertThat(ciphertext).isNotBlank();
        assertThat(ciphertext).isNotEqualTo(plaintext);
        // compact JWE has exactly 5 dot-separated parts
        assertThat(ciphertext.split("\\.", -1)).hasSize(5);
    }

    @Test
    public void encryptionService_fetchWithWrongToken_throwsDisc008() {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        EncryptionService encryptionService = new EncryptionService(httpClient);

        assertThatThrownBy(() -> {
            DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);
            encryptionService.fetchLatestKeys(discovery.getDiscoveryContextBaseUrl(), "bad-token".getBytes(StandardCharsets.UTF_8));
        })
                .isInstanceOf(Exception.class)
                .hasMessageMatching(".*(DISCO_037|DISCO_038).*");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String login() throws Exception {
        DiscoveryServiceClient discoveryClient = new DiscoveryServiceClient(httpClient);
        DiscoveryServiceResult discovery = discoveryClient.resolve(platformDiscoveryUrl, subdomain);

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] tokenBytes = identityClient.login(
                discovery.getIdentityBaseUrl(),
                username,
                secret.getBytes(StandardCharsets.UTF_8));
        String token = new String(tokenBytes, StandardCharsets.UTF_8);
        LOG.info("login: token length=" + token.length());
        decodeAndLogJwt("JwksEndpointIT.login", token);
        return token;
    }

    private Response get(String url, String bearerToken) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .header("User-Agent", "Jenkins-Scanner/3.1.0")
                .get()
                .build();
        return httpClient.newCall(request).execute();
    }

    private JsonObject fetchJson(String bearerToken) throws Exception {
        try (Response response = get(jwksUrl, bearerToken)) {
            assertThat(response.code()).as("JWKS fetch should return 200").isEqualTo(200);
            String body = response.body() != null ? response.body().string() : "";
            assertThat(body).isNotBlank();
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

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

    // ── JWT decode helper ─────────────────────────────────────────────────────

    static com.google.gson.JsonObject parseJwtPart(String base64UrlPart) {
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
}
