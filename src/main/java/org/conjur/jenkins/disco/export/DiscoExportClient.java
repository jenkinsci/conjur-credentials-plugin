package org.conjur.jenkins.disco.export;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.conjur.jenkins.disco.model.DiscoverySnapshot;

import org.conjur.jenkins.disco.DiscoCode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Uploads the DiscoverySnapshot to the DisCo ingestion endpoint via a two-step
 * presigned S3 upload:
 *
 *   1. POST to snapshot-links endpoint (with bearer token) → receive presigned S3 PUT URL
 *   2. PUT the JSON payload to the presigned URL (with SHA-256 checksum + SSE header)
 *
 * Mirrors utils.py from discoverycontext-e2e/tests/FIS/:
 *   getPresignedUrl()       ↔ get_presigned_url()
 *   uploadFileToS3()        ↔ upload_file_to_s3()
 *   uploadViaSnapshotLinks() ↔ upload_file_via_snapshot_links()
 */
public class DiscoExportClient {

    private static final Logger LOGGER = Logger.getLogger(DiscoExportClient.class.getName());
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_PRESIGNED_URL_RESPONSE_BYTES = 10 * 1024;

    private final OkHttpClient httpClient;

    public DiscoExportClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // -------------------------------------------------------------------------
    // Public orchestration API — mirrors upload_file_via_snapshot_links()
    // -------------------------------------------------------------------------

    /**
     * Orchestrates the full two-step presigned S3 upload.
     * Mirrors upload_file_via_snapshot_links() in utils.py.
     */
    public void send(DiscoverySnapshot snapshot,
                     String snapshotLinksUrl,
                     byte[] bearerToken,
                     String agentVersion,
                     String tenantId,
                     String username,
                     String instanceId) throws ExportException {

        byte[] payload = new Gson().toJson(snapshot).getBytes(StandardCharsets.UTF_8);
         uploadViaSnapshotLinks(payload, snapshotLinksUrl, bearerToken, agentVersion,
                tenantId, username, instanceId);
    }

    /**
     * Orchestrates checksum → presigned URL → S3 PUT for raw payload bytes.
     * Mirrors upload_file_via_snapshot_links() in utils.py.
     */
    public void uploadViaSnapshotLinks(byte[] payload,
                                       String snapshotLinksUrl,
                                       byte[] bearerToken,
                                       String agentVersion,
                                       String tenantId,
                                       String username,
                                       String instanceId) throws ExportException {

        String sha256Hex = computeSha256Hex(payload);
        String sha256Base64 = hexToBase64(sha256Hex);

        LOGGER.info(PAYLOAD_INFO.format(payload.length, sha256Hex));

        String presignedUrl = getPresignedUrl(snapshotLinksUrl, bearerToken,
                sha256Hex, payload.length, agentVersion, tenantId, username, instanceId);

        uploadFileToS3(presignedUrl, payload, sha256Base64, agentVersion,
                tenantId, username, instanceId);
    }

    // -------------------------------------------------------------------------
    // Step 1 — mirrors get_presigned_url() in utils.py
    // -------------------------------------------------------------------------

    /**
     * POST to the snapshot-links endpoint to obtain a presigned S3 PUT URL.
     * Mirrors get_presigned_url() in utils.py.
     *
     * @param snapshotLinksUrl  e.g. https://…/api/ingestions/jenkins/snapshot-links
     * @param bearerToken       CyberArk Identity bearer token
     * @param sha256Hex         hex-encoded SHA-256 of the payload
     * @param payloadSize       size of the payload in bytes
     * @param agentVersion      plugin version string
     * @param tenantId          CyberArk tenant UUID
     * @param username          authenticated username
     * @param instanceId        Jenkins instance ID (identifier / jenkinsId)
     * @return presigned S3 URL
     */
    public String getPresignedUrl(String snapshotLinksUrl,
                                  byte[] bearerToken,
                                  String sha256Hex,
                                  int payloadSize,
                                  String agentVersion,
                                  String tenantId,
                                  String username,
                                  String instanceId) throws ExportException {

        JsonObject body = new JsonObject();
        body.addProperty("agent_version", agentVersion);
        body.addProperty("identifier", instanceId);
        body.addProperty("checksum_sha256", sha256Hex);
        body.addProperty("file_size", payloadSize);
        body.addProperty("signature_version", "sigv4");

        Request request = new Request.Builder()
                .url(snapshotLinksUrl)
                .header("Authorization", "Bearer " + new String(bearerToken, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                // API Gateway blocks the default OkHttp User-Agent; mirror the Python FIS suite
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            if (code == 401 || code == 403) {
                LOGGER.severe(SNAPSHOT_LINKS_AUTH_REJECTED.format(code));
                throw new ExportException(SNAPSHOT_LINKS_AUTH_REJECTED.format(code));
            }
            if (code == 429) {
                String retryAfter = response.header("Retry-After", "60");
                LOGGER.warning(SNAPSHOT_LINKS_RATE_LIMITED.format(retryAfter));
                throw new ExportException(SNAPSHOT_LINKS_RATE_LIMITED.format(retryAfter));
            }
            if (!response.isSuccessful()) {
                throw new ExportException(SNAPSHOT_LINKS_HTTP_ERROR.format(code));
            }

            ResponseBody respBody = response.body();
            String bodyStr = respBody != null
                    ? new String(respBody.bytes(), StandardCharsets.UTF_8)
                    : "";

            if (bodyStr.length() > MAX_PRESIGNED_URL_RESPONSE_BYTES) {
                throw new ExportException(SNAPSHOT_LINKS_RESPONSE_TOO_LARGE.format());
            }

            JsonObject parsed = JsonParser.parseString(bodyStr).getAsJsonObject();
            if (!parsed.has("url")) {
                throw new ExportException(SNAPSHOT_LINKS_MISSING_URL.format());
            }
            String presignedUrl = parsed.get("url").getAsString();
            LOGGER.info(PRESIGNED_URL_OBTAINED.format());
            return presignedUrl;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, SNAPSHOT_LINKS_IO_FAILURE.format(e.getMessage()), e);
            throw new ExportException(SNAPSHOT_LINKS_IO_FAILURE.format(e.getMessage()), e);
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — mirrors upload_file_to_s3() in utils.py
    // -------------------------------------------------------------------------

    /**
     * PUT payload bytes to a presigned S3 URL with SigV4 headers.
     * Mirrors upload_file_to_s3() in utils.py.
     *
     * Tag keys mirror the reference exactly (sorted, RFC 3986 encoded):
     *   agent_version, tenant_id, upload_type, uploader_id, username, vendor
     *
     * @param presignedUrl  S3 presigned URL from getPresignedUrl()
     * @param payload       raw bytes to upload
     * @param sha256Base64  base64-encoded SHA-256 of payload
     * @param agentVersion  plugin version string
     * @param tenantId      CyberArk tenant UUID
     * @param username      authenticated username
     * @param instanceId    Jenkins instance ID
     */
    public void uploadFileToS3(String presignedUrl,
                               byte[] payload,
                               String sha256Base64,
                               String agentVersion,
                               String tenantId,
                               String username,
                               String instanceId) throws ExportException {

        String tagging = buildTagging(agentVersion, tenantId, username, instanceId);

        Request request = new Request.Builder()
                .url(presignedUrl)
                .header("Content-Type", "application/json")
                .header("x-amz-checksum-sha256", sha256Base64)
                .header("x-amz-server-side-encryption", "AES256")
                .header("x-amz-tagging", tagging)
                .put(RequestBody.create(payload, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            if (code == 200 || code == 202 || code == 204) {
                LOGGER.info(S3_UPLOAD_SUCCESSFUL.format(code));
                return;
            }
            if (code == 413) {
                LOGGER.severe(S3_PUT_PAYLOAD_TOO_LARGE.format());
                throw new ExportException(S3_PUT_PAYLOAD_TOO_LARGE.format());
            }
            throw new ExportException(S3_PUT_HTTP_ERROR.format(code));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, S3_PUT_IO_FAILURE.format(e.getMessage()), e);
            throw new ExportException(S3_PUT_IO_FAILURE.format(e.getMessage()), e);
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers — mirrors calculate_sha256() / uri_encode() in utils.py
    // -------------------------------------------------------------------------

    /**
     * Compute SHA-256 hex digest of bytes.
     * Mirrors calculate_sha256() in utils.py.
     */
    public static String computeSha256Hex(byte[] data) throws ExportException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ExportException(SHA256_FAILED.format(e.getMessage()), e);
        }
    }

    /**
     * Convert hex-encoded SHA-256 digest to base64.
     * Mirrors bytes.fromhex() + base64.b64encode() in utils.py.
     */
    public static String hexToBase64(String hex) throws ExportException {
        try {
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new ExportException(HEX_TO_BASE64_FAILED.format(e.getMessage()), e);
        }
    }

    /**
     * URI-encode a string per RFC 3986.
     * Mirrors uri_encode() in utils.py — encodes all chars except A-Z a-z 0-9 - _ . ~
     */
    public static String uriEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Build the x-amz-tagging header value.
     * Mirrors utils.py tag construction: sorted keys, RFC 3986 encoded.
     *
     * Keys (matching reference):
     *   agent_version, tenant_id, upload_type, uploader_id, username, vendor
     */
    static String buildTagging(String agentVersion, String tenantId,
                               String username, String instanceId) {
        TreeMap<String, String> tags = new TreeMap<>();
        tags.put("agent_version", agentVersion);
        tags.put("tenant_id",     tenantId);
        tags.put("upload_type",   "jenkins_snapshot");
        tags.put("uploader_id",   instanceId);
        tags.put("username",      username);
        tags.put("vendor",        "jenkins");

        StringBuilder sb = new StringBuilder();
        tags.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(uriEncode(k)).append('=').append(uriEncode(v));
        });
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    public static class ExportException extends Exception {
        public ExportException(String message) { super(message); }
        public ExportException(String message, Throwable cause) { super(message, cause); }
    }
}
