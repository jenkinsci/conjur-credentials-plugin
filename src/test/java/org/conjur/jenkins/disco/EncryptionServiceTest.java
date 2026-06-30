package org.conjur.jenkins.disco;

import okhttp3.*;
import org.conjur.jenkins.disco.security.EncryptionService;
import org.jose4j.jwe.JsonWebEncryption;
import org.junit.Test;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EncryptionServiceTest {

    // ── Key selection ─────────────────────────────────────────────────────────

    @Test
    public void fetchLatestKeys_selectsKeyWithFarthestExpiry() throws Exception {
        KeyPair kp1 = generateRsaKeyPair();
        KeyPair kp2 = generateRsaKeyPair();

        String jwks = buildJwks(
                new JwkEntry("kid-old", 1000L, (RSAPublicKey) kp1.getPublic()),
                new JwkEntry("kid-new", 9999999999L, (RSAPublicKey) kp2.getPublic())
        );

        EncryptionService service = serviceWithBody(jwks);
        service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(service.getSelectedKid()).isEqualTo("kid-new");
    }

    @Test
    public void fetchLatestKeys_selectsOnlyKey_whenOnePresent() throws Exception {
        KeyPair kp = generateRsaKeyPair();
        String jwks = buildJwks(new JwkEntry("only-kid", 5000L, (RSAPublicKey) kp.getPublic()));

        EncryptionService service = serviceWithBody(jwks);
        service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(service.getSelectedKid()).isEqualTo("only-kid");
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    @Test
    public void encryptValue_returnsCompactJwe() throws Exception {
        KeyPair kp = generateRsaKeyPair();
        String jwks = buildJwks(new JwkEntry("test-kid", 9999L, (RSAPublicKey) kp.getPublic()));

        EncryptionService service = serviceWithBody(jwks);
        service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String encrypted = service.encryptValue("hello world");

        // Compact JWE has exactly 5 dot-separated parts
        assertThat(encrypted).isNotBlank();
        assertThat(encrypted.split("\\.", -1)).hasSize(5);
    }

    @Test
    public void encryptValue_producesDecryptableOutput() throws Exception {
        KeyPair kp = generateRsaKeyPair();
        String jwks = buildJwks(new JwkEntry("dec-kid", 9999L, (RSAPublicKey) kp.getPublic()));

        EncryptionService service = serviceWithBody(jwks);
        service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String plaintext = "supersecret";
        String encrypted = service.encryptValue(plaintext);

        // Decrypt via JWE to verify round-trip
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setCompactSerialization(encrypted);
        jwe.setKey(kp.getPrivate());
        assertThat(jwe.getPlaintextString()).isEqualTo(plaintext);
    }

    @Test
    public void encryptValue_throwsIllegalState_beforeKeysAreFetched() {
        EncryptionService service = new EncryptionService(Mockito.mock(OkHttpClient.class));

        assertThatThrownBy(() -> service.encryptValue("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISCO_039");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    public void fetchLatestKeys_throwsOnNonSuccessResponse() {
        OkHttpClient http = mockHttpWithCode(500);
        EncryptionService service = new EncryptionService(http);

        assertThatThrownBy(() -> service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("DISCO_038");
    }

    @Test
    public void fetchLatestKeys_throwsOnEmptyKeyArray() {
        EncryptionService service = serviceWithBody("{\"keys\":[]}");

        assertThatThrownBy(() -> service.fetchLatestKeys("https://disco.example.com", "test-token".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("DISCO_040");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private EncryptionService serviceWithBody(String body) {
        return new EncryptionService(mockHttpWithBody(body));
    }

    private OkHttpClient mockHttpWithBody(String body) {
        return mockHttpWithResponse(new Response.Builder()
                .request(new Request.Builder().url("https://disco.example.com/discovery-context/jwks").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build());
    }

    private OkHttpClient mockHttpWithCode(int code) {
        return mockHttpWithResponse(new Response.Builder()
                .request(new Request.Builder().url("https://disco.example.com/discovery-context/jwks").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code).message("Error")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build());
    }

    /**
     * Build a mock OkHttpClient that returns the given response.
     * Also stubs newBuilder() so that EncryptionService's followRedirects(false)
     * call does not NPE — the builder returns a client backed by the same mock call.
     */
    private OkHttpClient mockHttpWithResponse(Response response) {
        try {
            Call call = Mockito.mock(Call.class);
            Mockito.when(call.execute()).thenReturn(response);

            OkHttpClient client = Mockito.mock(OkHttpClient.class);
            Mockito.when(client.newCall(Mockito.any())).thenReturn(call);

            // EncryptionService calls httpClient.newBuilder().followRedirects(false).build()
            // We stub newBuilder() to return a builder whose build() returns the same mock client
            OkHttpClient.Builder builder = Mockito.mock(OkHttpClient.Builder.class);
            Mockito.when(builder.followRedirects(Mockito.anyBoolean())).thenReturn(builder);
            Mockito.when(builder.build()).thenReturn(client);
            Mockito.when(client.newBuilder()).thenReturn(builder);

            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private String buildJwks(JwkEntry... entries) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) sb.append(",");
            JwkEntry e = entries[i];
            RSAPublicKey pub = e.key;
            sb.append("{");
            sb.append("\"kid\":\"").append(e.kid).append("\",");
            sb.append("\"kty\":\"RSA\",");
            sb.append("\"use\":\"enc\",");
            sb.append("\"alg\":\"RSA-OAEP-256\",");
            sb.append("\"exp\":").append(e.exp).append(",");
            sb.append("\"n\":\"").append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(pub.getModulus().toByteArray())).append("\",");
            sb.append("\"e\":\"").append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(pub.getPublicExponent().toByteArray())).append("\"");
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private record JwkEntry(String kid, long exp, RSAPublicKey key) {}
}
