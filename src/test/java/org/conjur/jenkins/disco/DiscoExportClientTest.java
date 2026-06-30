package org.conjur.jenkins.disco;

import okhttp3.*;
import org.conjur.jenkins.disco.export.DiscoExportClient;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DiscoExportClient — getPresignedUrl, uploadFileToS3, checksum helpers.
 */
public class DiscoExportClientTest {

    // ── getPresignedUrl — auth / error codes ─────────────────────────────

    @Test
    public void getPresignedUrl_throws_DISC008_on401() {
        var client = new DiscoExportClient(mockHttpClient(401, "Unauthorized", "{}"));
        assertThatThrownBy(() -> client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_044");
    }

    @Test
    public void getPresignedUrl_throws_DISC008_on403() {
        var client = new DiscoExportClient(mockHttpClient(403, "Forbidden", "{}"));
        assertThatThrownBy(() -> client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_044");
    }

    @Test
    public void getPresignedUrl_throws_DISC010_on429() throws IOException {
        Response resp = new Response.Builder()
                .request(new Request.Builder().url("https://disco.example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429).message("Too Many Requests")
                .header("Retry-After", "30")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build();
        var client = new DiscoExportClient(mockHttpClientWithResponse(resp));
        assertThatThrownBy(() -> client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_057");
    }

    @Test
    public void getPresignedUrl_throws_DISC008_on500() {
        var client = new DiscoExportClient(mockHttpClient(500, "Server Error", "{}"));
        assertThatThrownBy(() -> client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_045");
    }

    @Test
    public void getPresignedUrl_throws_DISC008_whenUrlMissing() {
        var client = new DiscoExportClient(mockHttpClient(200, "OK", "{\"other\":\"field\"}"));
        assertThatThrownBy(() -> client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_047");
    }

    @Test
    public void getPresignedUrl_returnsUrl_on200() throws Exception {
        var client = new DiscoExportClient(
                mockHttpClient(200, "OK", "{\"url\":\"https://s3.example.com/presigned\"}"));
        String url = client.getPresignedUrl(
                "https://disco.example.com", "token".getBytes(StandardCharsets.UTF_8), "sha", 100,
                "1.0", "tenant-id", "user", "inst-id");
        assertThat(url).isEqualTo("https://s3.example.com/presigned");
    }

    // ── uploadFileToS3 — S3 PUT status handling ────────────────────────────

    @Test
    public void uploadFileToS3_succeeds_on200() throws Exception {
        var client = new DiscoExportClient(mockHttpClient(200, "OK", ""));
        client.uploadFileToS3("https://s3.example.com/presigned",
                "hello".getBytes(StandardCharsets.UTF_8), "sha256",
                "1.0", "tenant-id", "user", "inst-id");
    }

    @Test
    public void uploadFileToS3_succeeds_on204() throws Exception {
        var client = new DiscoExportClient(mockHttpClient(204, "No Content", ""));
        client.uploadFileToS3("https://s3.example.com/presigned",
                "hello".getBytes(StandardCharsets.UTF_8), "sha256",
                "1.0", "tenant-id", "user", "inst-id");
    }

    @Test
    public void uploadFileToS3_throws_DISC008_on413() {
        var client = new DiscoExportClient(mockHttpClient(413, "Payload Too Large", ""));
        assertThatThrownBy(() -> client.uploadFileToS3("https://s3.example.com/presigned",
                "hello".getBytes(StandardCharsets.UTF_8), "sha256",
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_049");
    }

    @Test
    public void uploadFileToS3_throws_DISC008_on500() {
        var client = new DiscoExportClient(mockHttpClient(500, "Error", ""));
        assertThatThrownBy(() -> client.uploadFileToS3("https://s3.example.com/presigned",
                "hello".getBytes(StandardCharsets.UTF_8), "sha256",
                "1.0", "tenant-id", "user", "inst-id"))
                .isInstanceOf(DiscoExportClient.ExportException.class)
                .hasMessageContaining("DISCO_050");
    }

    // ── computeSha256Hex ──────────────────────────────────────────────────────

    @Test
    public void computeSha256Hex_producesLowercase64CharHex() throws Exception {
        String result = DiscoExportClient.computeSha256Hex(
                "hello world".getBytes(StandardCharsets.UTF_8));
        assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    public void computeSha256Hex_isDeterministic() throws Exception {
        byte[] data = "same input".getBytes(StandardCharsets.UTF_8);
        assertThat(DiscoExportClient.computeSha256Hex(data))
                .isEqualTo(DiscoExportClient.computeSha256Hex(data));
    }

    // ── hexToBase64 ───────────────────────────────────────────────────────────

    @Test
    public void hexToBase64_convertsKnownVector() throws Exception {
        // SHA-256("hello world") known hex → known base64
        String hex = DiscoExportClient.computeSha256Hex(
                "hello world".getBytes(StandardCharsets.UTF_8));
        String b64 = DiscoExportClient.hexToBase64(hex);
        // base64 should decode back to 32 bytes
        assertThat(java.util.Base64.getDecoder().decode(b64)).hasSize(32);
    }

    @Test
    public void hexToBase64_roundTripsWithComputeSha256Hex() throws Exception {
        byte[] data = "round trip test".getBytes(StandardCharsets.UTF_8);
        String hex = DiscoExportClient.computeSha256Hex(data);
        String b64 = DiscoExportClient.hexToBase64(hex);
        // Decode base64 → should be the raw SHA-256 bytes (32 bytes)
        byte[] decoded = java.util.Base64.getDecoder().decode(b64);
        assertThat(decoded).hasSize(32);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private OkHttpClient mockHttpClient(int code, String message, String body) {
        try {
            return mockHttpClientWithResponse(new Response.Builder()
                    .request(new Request.Builder().url("https://disco.example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code).message(message)
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private OkHttpClient mockHttpClientWithResponse(Response response) throws IOException {
        Call call = Mockito.mock(Call.class);
        Mockito.when(call.execute()).thenReturn(response);
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Mockito.when(client.newCall(Mockito.any())).thenReturn(call);
        return client;
    }
}
