package org.conjur.jenkins.disco;

import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.conjur.jenkins.disco.model.DiscoverySnapshot;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenario-style tests that use MockWebServer for realistic request inspection.
 *
 * Mirrors the V2-scenarios pattern — each scenario exercises a specific
 * protocol behaviour by asserting on headers, body structure, or error
 * responses from a real in-process HTTP server.
 *
 * ── Mock scenarios (always run) ─────────────────────────────────────────────
 *   scenario_upload                  — happy-path, verifies request shape
 *   scenario_uploadChecksumHeaders   — verifies all SigV4 headers are sent
 *   scenario_uploadTaggingHeader     — verifies x-amz-tagging is sorted/encoded
 *   scenario_largePayload            — 1 MB payload, verifies no size truncation
 *   scenario_presignedUrlWithoutSse  — S3 returns 400 (SSE rejected) → DISCO_050
 *
 * ── Real-API scenario (gated by ARK_LIVE_TEST=true) ─────────────────────────
 *   scenario_RealAPI_upload          — full round-trip against live service
 */
public class DiscoExportClientScenariosTest {

    private static final Logger LOG = Logger.getLogger(DiscoExportClientScenariosTest.class.getName());

    private static final String CONTROLLER_ID = "ffffffffffffffffffffffffffffffff";
    private static final String PRESIGNED_PATH = "/presigned/upload/abc123";

    private MockWebServer snapshotLinksServer;
    private MockWebServer s3Server;
    private OkHttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        snapshotLinksServer = new MockWebServer();
        s3Server            = new MockWebServer();
        snapshotLinksServer.start();
        s3Server.start();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        snapshotLinksServer.shutdown();
        s3Server.shutdown();
    }

    // =========================================================================
    // Scenario: happy-path upload — inspect the full request shape
    // =========================================================================

    @Test
    public void scenario_upload() throws Exception {
        String presignedUrl = s3Server.url(PRESIGNED_PATH).toString();

        snapshotLinksServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"" + presignedUrl + "\"}"));
        s3Server.enqueue(new MockResponse().setResponseCode(200));

        DiscoExportClient client = new DiscoExportClient(httpClient);
        client.send(DiscoExportClientLiveTest.buildSnapshot(CONTROLLER_ID),
                snapshotLinksServer.url("/snapshot-links").toString(),
                "test-bearer-token".getBytes(java.nio.charset.StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", CONTROLLER_ID);

        // Verify snapshot-links request
        RecordedRequest snapshotReq = snapshotLinksServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(snapshotReq).isNotNull();
        assertThat(snapshotReq.getMethod()).isEqualTo("POST");
        assertThat(snapshotReq.getHeader("Authorization")).isEqualTo("Bearer test-bearer-token");
        assertThat(snapshotReq.getHeader("Content-Type")).contains("application/json");

        String snapshotBody = snapshotReq.getBody().readUtf8();
        assertThat(snapshotBody).contains("\"agent_version\"");
        assertThat(snapshotBody).contains("\"identifier\"");
        assertThat(snapshotBody).contains("\"checksum_sha256\"");
        assertThat(snapshotBody).contains("\"file_size\"");
        assertThat(snapshotBody).contains("\"signature_version\"");
        assertThat(snapshotBody).contains("\"sigv4\"");
        // file_size must be a JSON number, not a string
        assertThat(snapshotBody).doesNotContainPattern("\"file_size\"\\s*:\\s*\"");

        // Verify S3 PUT request
        RecordedRequest s3Req = s3Server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(s3Req).isNotNull();
        assertThat(s3Req.getMethod()).isEqualTo("PUT");
        assertThat(s3Req.getPath()).isEqualTo(PRESIGNED_PATH);
    }

    // =========================================================================
    // Scenario: verify all SigV4 checksum + SSE headers are present on S3 PUT
    // =========================================================================

    @Test
    public void scenario_uploadChecksumHeaders() throws Exception {
        String presignedUrl = s3Server.url(PRESIGNED_PATH).toString();

        snapshotLinksServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"" + presignedUrl + "\"}"));
        s3Server.enqueue(new MockResponse().setResponseCode(200));

        DiscoExportClient client = new DiscoExportClient(httpClient);
        client.send(DiscoExportClientLiveTest.buildSnapshot(CONTROLLER_ID),
                snapshotLinksServer.url("/snapshot-links").toString(),
                "bearer".getBytes(java.nio.charset.StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", CONTROLLER_ID);

        snapshotLinksServer.takeRequest(2, TimeUnit.SECONDS); // consume

        RecordedRequest s3Req = s3Server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(s3Req).isNotNull();

        String checksum = s3Req.getHeader("x-amz-checksum-sha256");
        assertThat(checksum).as("x-amz-checksum-sha256 must be present").isNotBlank();
        // base64 is always 44 chars for a 256-bit hash (with padding)
        assertThat(checksum).hasSize(44);

        assertThat(s3Req.getHeader("x-amz-server-side-encryption"))
                .as("SSE header must be AES256")
                .isEqualTo("AES256");

        assertThat(s3Req.getHeader("x-amz-tagging"))
                .as("tagging header must be present")
                .isNotBlank();
    }

    // =========================================================================
    // Scenario: verify x-amz-tagging is sorted alphabetically and RFC 3986 encoded
    // =========================================================================

    @Test
    public void scenario_uploadTaggingHeader() throws Exception {
        String presignedUrl = s3Server.url(PRESIGNED_PATH).toString();

        snapshotLinksServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"" + presignedUrl + "\"}"));
        s3Server.enqueue(new MockResponse().setResponseCode(200));

        DiscoExportClient client = new DiscoExportClient(httpClient);
        client.send(DiscoExportClientLiveTest.buildSnapshot(CONTROLLER_ID),
                snapshotLinksServer.url("/snapshot-links").toString(),
                "bearer".getBytes(java.nio.charset.StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", CONTROLLER_ID);

        snapshotLinksServer.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest s3Req = s3Server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(s3Req).isNotNull();

        String tagging = s3Req.getHeader("x-amz-tagging");
        assertThat(tagging).isNotBlank();

        // All 6 expected keys must be present (mirrors utils.py tag construction)
        assertThat(tagging).contains("agent_version=");
        assertThat(tagging).contains("tenant_id=");
        assertThat(tagging).contains("upload_type=jenkins_snapshot");
        assertThat(tagging).contains("uploader_id=");
        assertThat(tagging).contains("username=");
        assertThat(tagging).contains("vendor=jenkins");

        // Keys must appear in alphabetical order
        int posAgentVersion = tagging.indexOf("agent_version");
        int posTenantId     = tagging.indexOf("tenant_id");
        int posUploadType   = tagging.indexOf("upload_type");
        int posUploaderId   = tagging.indexOf("uploader_id");
        int posUsername     = tagging.indexOf("username");
        int posVendor       = tagging.indexOf("vendor");
        assertThat(posAgentVersion).isLessThan(posTenantId);
        assertThat(posTenantId).isLessThan(posUploadType);
        assertThat(posUploadType).isLessThan(posUploaderId);
        assertThat(posUploaderId).isLessThan(posUsername);
        assertThat(posUsername).isLessThan(posVendor);
    }

    // =========================================================================
    // Scenario: large payload (1 MB) — no truncation, correct checksum
    // =========================================================================

    @Test
    public void scenario_largePayload() throws Exception {
        // Build a 1 MB payload by overriding the snapshot's credential list with padding
        byte[] largePadding = new byte[1024 * 1024];
        java.util.Arrays.fill(largePadding, (byte) 'x');
        String paddedJson = "{\"padding\":\"" + new String(largePadding, StandardCharsets.UTF_8) + "\"}";
        byte[] payload = paddedJson.getBytes(StandardCharsets.UTF_8);

        String expectedChecksum = DiscoExportClient.computeSha256Hex(payload);
        String expectedBase64   = DiscoExportClient.hexToBase64(expectedChecksum);

        String presignedUrl = s3Server.url(PRESIGNED_PATH).toString();

        snapshotLinksServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"" + presignedUrl + "\"}"));
        s3Server.enqueue(new MockResponse().setResponseCode(200));

        DiscoExportClient client = new DiscoExportClient(httpClient);
        // Use the lower-level uploadViaSnapshotLinks to inject our raw payload
        client.uploadViaSnapshotLinks(payload,
                snapshotLinksServer.url("/snapshot-links").toString(),
                "bearer".getBytes(java.nio.charset.StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", CONTROLLER_ID);

        snapshotLinksServer.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest s3Req = s3Server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(s3Req).isNotNull();

        // The checksum sent must match what was computed from the full payload
        assertThat(s3Req.getHeader("x-amz-checksum-sha256")).isEqualTo(expectedBase64);

        // The body delivered to S3 must be the full payload, no truncation
        assertThat(s3Req.getBodySize()).isEqualTo(payload.length);
    }

    // =========================================================================
    // Scenario: S3 rejects the request with 400 (missing/wrong SSE header) → DISCO_050
    // Mirrors the case where the server enforces SSE and rejects non-compliant PUTs
    // =========================================================================

    @Test
    public void scenario_presignedUrlWithoutSse_throwsDISC008() {
        String presignedUrl = s3Server.url(PRESIGNED_PATH).toString();

        snapshotLinksServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"" + presignedUrl + "\"}"));
        // S3 rejects with 400 when SSE enforcement is configured
        s3Server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("<?xml version=\"1.0\"?><Error><Code>InvalidRequest</Code>" +
                         "<Message>SSE headers required.</Message></Error>"));

        DiscoExportClient client = new DiscoExportClient(httpClient);
        assertThatThrownBy(() ->
                client.send(DiscoExportClientLiveTest.buildSnapshot(CONTROLLER_ID),
                        snapshotLinksServer.url("/snapshot-links").toString(),
                        "bearer".getBytes(java.nio.charset.StandardCharsets.UTF_8), "3.1.0", "tenant-uuid", "user@example.com", CONTROLLER_ID))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_050");
    }

    // =========================================================================
    // Real-API scenario  (gated by ARK_LIVE_TEST=true)
    // Full round-trip: identity login → snapshot-links → S3 PUT
    // =========================================================================

    /**
     * Full round-trip upload scenario against the live CyberArk service.
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
    public void scenario_RealAPI_upload() throws Exception {
        Assume.assumeTrue(
                "Set ARK_LIVE_TEST=true to run this test against the live service",
                "true".equalsIgnoreCase(DiscoExportClientLiveTest.env("ARK_LIVE_TEST")));

        String identityUrl = DiscoExportClientLiveTest.requireEnv("ARK_IDENTITY_URL");
        String subdomain   = DiscoExportClientLiveTest.requireEnv("ARK_SUBDOMAIN");
        String username    = DiscoExportClientLiveTest.requireEnv("ARK_USERNAME");
        String secret      = DiscoExportClientLiveTest.requireEnv("ARK_SECRET");
        String lambdaUrl   = DiscoExportClientLiveTest.requireEnv("ARK_LAMBDA_URL");

        OkHttpClient liveClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        CyberArkIdentityClient identityClient = new CyberArkIdentityClient(liveClient);
        byte[] tokenBytes = identityClient.login(identityUrl, subdomain, username,
                secret.getBytes(StandardCharsets.UTF_8));
        String tokenStr = new String(tokenBytes, StandardCharsets.UTF_8);

        String tenantId     = DiscoExportClientLiveTest.extractClaim(tokenStr, "tenant_id");
        String tokenUser    = DiscoExportClientLiveTest.extractClaim(tokenStr, "preferred_username");

        LOG.info("scenario_RealAPI_upload: tenant=" + tenantId + "  user=" + tokenUser);
        LOG.info("This test runs against a live service — timeouts may be unrelated to your changes.");

        DiscoverySnapshot snapshot = DiscoExportClientLiveTest.buildSnapshot(CONTROLLER_ID);
        DiscoExportClient client   = new DiscoExportClient(liveClient);
        client.send(snapshot, lambdaUrl, tokenBytes, "3.1.0", tenantId, tokenUser, CONTROLLER_ID);

        LOG.info("scenario_RealAPI_upload: PASSED");
    }
}
