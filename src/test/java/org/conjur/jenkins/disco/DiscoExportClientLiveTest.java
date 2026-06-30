package org.conjur.jenkins.disco;

import okhttp3.*;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.model.DiscoverySnapshot;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DiscoExportClient against a mock server (always runs) and against the
 * real CyberArk inventory API (opt-in via environment variables).
 *
 * Mirrors the structure of client_test.go from the k8s provider:
 *   TestCyberArkClient_PutSnapshot_MockAPI  →  putSnapshot_MockAPI_*()
 *   TestCyberArkClient_PutSnapshot_RealAPI  →  putSnapshot_RealAPI()
 *
 * ── Mock tests ──────────────────────────────────────────────────────────────
 * Always run. The mock OkHttpClient is configured per scenario.
 *
 * ── Real-API test ────────────────────────────────────────────────────────────
 * Runs only when ARK_LIVE_TEST=true. Requires:
 *
 *   ARK_LIVE_TEST=true
 *   ARK_SUBDOMAIN=disco4asaf
 *   ARK_USERNAME=diego@cyberark.cloud.465930
 *   ARK_SECRET=<password>
 *   ARK_IDENTITY_URL=https://aoj5620.id.integration-cyberark.cloud
 *   ARK_LAMBDA_URL=https://disco4asaf.inventory.integration-cyberark.cloud/api/ingestions/jenkins/snapshot-links
 *
 * Or pass them as -D system properties:
 *   -DARK_LIVE_TEST=true  -DARK_SUBDOMAIN=... etc.
 */
public class DiscoExportClientLiveTest {

    private static final Logger LOG = Logger.getLogger(DiscoExportClientLiveTest.class.getName());

    // Mirrors successClusterID in dataupload/mock.go — a valid 32-char hex Jenkins controller ID
    private static final String SUCCESS_CONTROLLER_ID = "ffffffffffffffffffffffffffffffff";

    // =========================================================================
    // Mock-API tests  (mirrors TestCyberArkClient_PutSnapshot_MockAPI)
    // =========================================================================

    /**
     * Happy path: mock returns presigned URL, S3 PUT succeeds.
     * Mirrors the "successful upload" case in dataupload_test.go.
     */
    @Test
    public void putSnapshot_MockAPI_successfulUpload() throws Exception {
        String presignedUrl = "https://s3.example.com/presigned/upload/abc123";
        OkHttpClient mockClient = buildTwoStepMockClient(
                new MockR(200, "{\"url\":\"" + presignedUrl + "\"}"),
                new MockR(200, "")
        );

        DiscoExportClient client = new DiscoExportClient(mockClient);
        client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                "bearer-token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID);
        // no exception = pass
    }

    /**
     * Bearer token rejected by snapshot-links → DISCO_044.
     * Mirrors "error when bearer token is incorrect".
     */
    @Test
    public void putSnapshot_MockAPI_wrongBearerToken_throwsDISC008() {
        OkHttpClient mockClient = buildSingleStepMockClient(
                new MockR(403, "{\"message\":\"Forbidden\"}"));

        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "bad-token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_044");
    }

    /**
     * snapshot-links returns 401 → DISCO_044.
     */
    @Test
    public void putSnapshot_MockAPI_401FromSnapshotLinks_throwsDISC008() {
        OkHttpClient mockClient = buildSingleStepMockClient(new MockR(401, "{}"));

        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "expired-token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_044");
    }

    /**
     * snapshot-links returns 429 with Retry-After → DISCO_057.
     */
    @Test
    public void putSnapshot_MockAPI_429RateLimit_throwsDISC010() throws IOException {
        Response resp = new Response.Builder()
                .request(new Request.Builder().url("https://mock.example.com/snapshot-links").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429).message("Too Many Requests")
                .header("Retry-After", "60")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build();

        OkHttpClient mockClient = mockClientWithResponse(resp);
        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_057");
    }

    /**
     * snapshot-links returns 200 but missing "url" field → DISCO_047.
     * Mirrors "invalid JSON from server (RetrievePresignedUploadURL step)".
     */
    @Test
    public void putSnapshot_MockAPI_missingUrlInResponse_throwsDISC008() {
        OkHttpClient mockClient = buildSingleStepMockClient(
                new MockR(200, "{\"other\":\"field\"}"));

        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_047");
    }

    /**
     * snapshot-links returns 500 → DISCO_045.
     * Mirrors "500 from server (RetrievePresignedUploadURL step)".
     */
    @Test
    public void putSnapshot_MockAPI_500FromSnapshotLinks_throwsDISC008() {
        OkHttpClient mockClient = buildSingleStepMockClient(
                new MockR(500, "{\"error\":\"mock error\"}"));

        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_045");
    }

    /**
     * S3 PUT returns 413 Payload Too Large → DISCO_049.
     */
    @Test
    public void putSnapshot_MockAPI_413FromS3_throwsDISC008() {
        String presignedUrl = "https://s3.example.com/presigned/upload/abc123";
        OkHttpClient mockClient = buildTwoStepMockClient(
                new MockR(200, "{\"url\":\"" + presignedUrl + "\"}"),
                new MockR(413, "<Error><Code>EntityTooLarge</Code></Error>")
        );

        DiscoExportClient client = new DiscoExportClient(mockClient);
        assertThatThrownBy(() ->
                client.send(buildSnapshot(SUCCESS_CONTROLLER_ID), "https://mock.example.com/snapshot-links",
                        "token".getBytes(StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_049");
    }

    /**
     * Checksum mismatch: presigned URL is obtained with the correct checksum,
     * but the S3 PUT is made with a deliberately wrong checksum header.
     * S3 returns 400 BadDigest → DISCO_050.
     * Mirrors the checksum-mismatch scenario from mock.go handlePresignedUpload.
     */
    @Test
    public void putSnapshot_MockAPI_checksumMismatch_throwsDISC008() throws Exception {
        byte[] payload = toJson(buildSnapshot(SUCCESS_CONTROLLER_ID));
        String correctHex  = DiscoExportClient.computeSha256Hex(payload);
        String presignedUrl = "https://s3.example.com/presigned/upload/checksum-mismatch";

        OkHttpClient mockClient = buildTwoStepMockClient(
                new MockR(200, "{\"url\":\"" + presignedUrl + "\"}"),
                new MockR(400,
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Error><Code>BadDigest</Code>" +
                        "<Message>The SHA256 you specified did not match.</Message></Error>")
        );

        DiscoExportClient client = new DiscoExportClient(mockClient);

        String wrongHex    = "deadbeef" + correctHex.substring(8);
        String wrongBase64 = DiscoExportClient.hexToBase64(wrongHex);

        String presigned = client.getPresignedUrl("https://mock.example.com/snapshot-links",
                "token".getBytes(StandardCharsets.UTF_8), correctHex, payload.length, "3.1.0", "tenant-uuid", "user@example.com",
                SUCCESS_CONTROLLER_ID);

        assertThatThrownBy(() ->
                client.uploadFileToS3(presigned, payload, wrongBase64,
                        "3.1.0", "tenant-uuid", "user@example.com", SUCCESS_CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_050");
    }

    // =========================================================================
    // Real-API test  (mirrors TestCyberArkClient_PutSnapshot_RealAPI)
    //
    // Gated by ARK_LIVE_TEST=true.
    // Uses CyberArk Identity two-step login to obtain a bearer token, then calls
    // the real snapshot-links and S3 endpoints.
    // =========================================================================

    /**
     * Uploads a minimal Jenkins snapshot to the real CyberArk inventory API.
     *
     * Set environment variables (or -D system properties):
     *   ARK_LIVE_TEST=true
     *   ARK_IDENTITY_URL=https://aoj5620.id.integration-cyberark.cloud
     *   ARK_SUBDOMAIN=disco4asaf
     *   ARK_USERNAME=diego@cyberark.cloud.465930
     *   ARK_SECRET=<password>
     *   ARK_LAMBDA_URL=https://disco4asaf.inventory.integration-cyberark.cloud/api/ingestions/jenkins/snapshot-links
     */
    @Test
    public void putSnapshot_RealAPI() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run this test against the live service",
                "true".equalsIgnoreCase(env("ARK_LIVE_TEST")));

        String identityUrl = requireEnv("ARK_IDENTITY_URL");
        String subdomain   = requireEnv("ARK_SUBDOMAIN");
        String username    = requireEnv("ARK_USERNAME");
        String secret      = requireEnv("ARK_SECRET");
        String lambdaUrl   = requireEnv("ARK_LAMBDA_URL");

        LOG.info("putSnapshot_RealAPI: authenticating as " + username + " against " + identityUrl);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(httpClient);
        byte[] tokenBytes = identityClient.login(identityUrl, subdomain, username,
                secret.getBytes(StandardCharsets.UTF_8));
        String tokenStr = new String(tokenBytes, StandardCharsets.UTF_8);

        String tenantId     = extractClaim(tokenStr, "tenant_id");
        String tokenUser    = extractClaim(tokenStr, "preferred_username");
        String controllerId = SUCCESS_CONTROLLER_ID;

        LOG.info("putSnapshot_RealAPI: tenant=" + tenantId + "  user=" + tokenUser);
        LOG.info("This test runs against a live service and has been known to flake. "
                + "Timeouts may be unrelated to your changes.");

        DiscoverySnapshot snapshot = buildSnapshot(controllerId);
        DiscoExportClient client   = new DiscoExportClient(httpClient);
        client.send(snapshot, lambdaUrl, tokenBytes, "3.1.0", tenantId, tokenUser, controllerId);

        LOG.info("putSnapshot_RealAPI: PASSED");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static DiscoverySnapshot buildSnapshot(String controllerId) {
        DiscoverySnapshot snapshot = new DiscoverySnapshot();
        snapshot.setJenkinsId(controllerId);
        snapshot.setOriginStoreId(controllerId);
        snapshot.setDataSourceType("JenkinsDiscoveryPlugin");
        snapshot.setVersion("3.1.0");
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        snapshot.setTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        snapshot.setCredentials(new ArrayList<>());
        snapshot.setFolders(new ArrayList<>());
        snapshot.setJobs(new ArrayList<>());
        return snapshot;
    }

    static byte[] toJson(DiscoverySnapshot snapshot) {
        return new com.google.gson.Gson().toJson(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    /** Read from system property first, then env var. */
    static String env(String key) {
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getenv(key);
        return val != null ? val : "";
    }

    static String requireEnv(String key) {
        String val = env(key);
        Assume.assumeFalse("Skipping: " + key + " is required", val.isBlank());
        return val;
    }

    /** Decode a JWT claim without signature verification. */
    static String extractClaim(String token, String claim) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            String padded = parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4);
            String json = new String(java.util.Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            return obj.has(claim) ? obj.get(claim).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ── Simple mock response holder ──────────────────────────────────────────

    record MockR(int code, String body) {}

    // ── OkHttp mock helpers ──────────────────────────────────────────────────

    /** Client where first call returns r1, second returns r2 (snapshot-links then S3). */
    OkHttpClient buildTwoStepMockClient(MockR r1, MockR r2) {
        OkHttpClient client = org.mockito.Mockito.mock(OkHttpClient.class);
        Call call1 = mockCall(r1, "https://mock.example.com/snapshot-links");
        Call call2 = mockCall(r2, "https://s3.example.com/presigned/upload/put");
        org.mockito.Mockito.when(client.newCall(org.mockito.Mockito.any()))
                .thenReturn(call1, call2);
        return client;
    }

    /** Client where every call returns the same response (single-step: snapshot-links only). */
    OkHttpClient buildSingleStepMockClient(MockR r) {
        OkHttpClient client = org.mockito.Mockito.mock(OkHttpClient.class);
        Call call = mockCall(r, "https://mock.example.com/snapshot-links");
        org.mockito.Mockito.when(client.newCall(org.mockito.Mockito.any())).thenReturn(call);
        return client;
    }

    Call mockCall(MockR r, String url) {
        try {
            Response resp = new Response.Builder()
                    .request(new Request.Builder().url(url).build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(r.code()).message("")
                    .body(ResponseBody.create(r.body(), MediaType.get("application/json")))
                    .build();
            Call call = org.mockito.Mockito.mock(Call.class);
            org.mockito.Mockito.when(call.execute()).thenReturn(resp);
            return call;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    OkHttpClient mockClientWithResponse(Response response) throws IOException {
        Call call = org.mockito.Mockito.mock(Call.class);
        org.mockito.Mockito.when(call.execute()).thenReturn(response);
        OkHttpClient client = org.mockito.Mockito.mock(OkHttpClient.class);
        org.mockito.Mockito.when(client.newCall(org.mockito.Mockito.any())).thenReturn(call);
        return client;
    }
}
