package org.conjur.jenkins.disco.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

import org.conjur.jenkins.configuration.TelemetryConfiguration;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Fetches RSA/ECC public keys from the DisCo platform and encrypts
 * credential values using the key with the longest remaining validity period.
 *
 * Selection rule: the key whose {@code exp} Unix epoch value is highest wins.
 * Keys that carry no {@code exp} field are treated as having infinite validity
 * ({@link Long#MAX_VALUE}) and therefore always outrank keys with a finite expiry.
 * When multiple keys share the same effective expiry the first one in the
 * response array is used.
 *
 * All key material is held in memory only — never persisted.
 */
public class EncryptionService {

    private static final Logger LOGGER = Logger.getLogger(EncryptionService.class.getName());

    private final OkHttpClient httpClient;
    private volatile String selectedKid;
    private volatile PublicKey publicKey;

    public EncryptionService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches available public keys from the DisCo discovery-context JWKS endpoint.
     * Requires a bearer token obtained from CyberArk Identity.
     *
     * @param discoveryContextBaseUrl base URL of the discovery-context service
     *                                (e.g. https://tenant-discoverycontext.cyberark.cloud)
     * @param bearerToken             token bytes from {@link CyberArkIdentityClient#login}
     * @throws Exception on network or key-parsing failure
     */
    public synchronized void fetchLatestKeys(String discoveryContextBaseUrl, byte[] bearerToken)
            throws Exception {
        String keysUrl = discoveryContextBaseUrl.replaceAll("/$", "")
                + "/discovery-context/jwks";

        Request request = new Request.Builder()
                .url(keysUrl)
                .header("Authorization", "Bearer " + new String(bearerToken, java.nio.charset.StandardCharsets.UTF_8))
                .header("Accept", "application/json")
                .header("User-Agent", "Jenkins-Scanner/" + TelemetryConfiguration.getPluginVersion())
                .get()
                .build();

        OkHttpClient noRedirectClient = httpClient.newBuilder()
                .followRedirects(false)
                .build();

        try (Response response = noRedirectClient.newCall(request).execute()) {
            if (response.isRedirect()) {
                throw new Exception(JWKS_REDIRECT.format(response.code()));
            }
            if (!response.isSuccessful()) {
                throw new Exception(JWKS_FETCH_FAILED.format(response.code(), keysUrl));
            }
            try (ResponseBody responseBody = response.body()) {
                String body = responseBody != null ? responseBody.string() : "";
                parseAndSelectKey(body);
            }
            LOGGER.fine(KEYS_FETCHED.format("(redacted)"));
        }
    }

    /**
     * Encrypts a plaintext string as a compact JWE token.
     *
     * Key management : RSA-OAEP-256 (the RSA public key wraps a fresh AES-256 CEK)
     * Content encryption: A256GCM (AES-256-GCM with a per-call random IV)
     *
     * The returned compact serialization has the form:
     *   BASE64URL(header).BASE64URL(encrypted_cek).BASE64URL(iv).BASE64URL(ciphertext).BASE64URL(tag)
     *
     * @param plaintext the value to encrypt
     * @return compact JWE string
     * @throws Exception if encryption fails or no key is loaded
     */
    public String encryptValue(String plaintext) throws Exception {
        if (publicKey == null) {
            throw new IllegalStateException(NO_PUBLIC_KEY_LOADED.format());
        }
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
        jwe.setKey(publicKey);
        jwe.setKeyIdHeaderValue(selectedKid);
        jwe.setPlaintext(plaintext);
        return jwe.getCompactSerialization();
    }

    public String getSelectedKid() {
        return selectedKid;
    }

    // -------------------------------------------------------------------------

    private void parseAndSelectKey(String jsonBody) throws Exception {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
        JsonArray keys = root.has("keys") ? root.getAsJsonArray("keys") : root.getAsJsonArray("data");

        String bestKid = null;
        PublicKey bestKey = null;
        long bestExpiry = Long.MIN_VALUE;

        for (int i = 0; i < keys.size(); i++) {
            JsonObject keyObj = keys.get(i).getAsJsonObject();
            // No exp claim → key never expires → treat as infinite validity (MAX_VALUE)
            long expiry = keyObj.has("exp") ? keyObj.get("exp").getAsLong() : Long.MAX_VALUE;

            if (expiry > bestExpiry) {
                bestExpiry = expiry;
                bestKid = keyObj.has("kid") ? keyObj.get("kid").getAsString() : "key-" + i;
                bestKey = parsePublicKey(keyObj);
            }
        }

        if (bestKey == null) {
            throw new Exception(NO_VALID_KEYS.format());
        }

        this.selectedKid = bestKid;
        this.publicKey = bestKey;
    }

    private PublicKey parsePublicKey(JsonObject keyObj) throws Exception {
        // Expect a DER/Base64 encoded public key in a "publicKey" or "n"+"e" field
        if (keyObj.has("publicKey")) {
            byte[] decoded = Base64.getDecoder().decode(keyObj.get("publicKey").getAsString());
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        }
        // JWK RSA format: reconstruct from n and e
        if (keyObj.has("n") && keyObj.has("e")) {
            byte[] modulusBytes = Base64.getUrlDecoder().decode(keyObj.get("n").getAsString());
            byte[] exponentBytes = Base64.getUrlDecoder().decode(keyObj.get("e").getAsString());
            java.math.BigInteger modulus = new java.math.BigInteger(1, modulusBytes);
            java.math.BigInteger exponent = new java.math.BigInteger(1, exponentBytes);
            return KeyFactory.getInstance("RSA").generatePublic(
                    new java.security.spec.RSAPublicKeySpec(modulus, exponent));
        }
        throw new Exception(UNSUPPORTED_KEY_FORMAT.format());
    }
}
