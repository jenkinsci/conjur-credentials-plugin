package org.conjur.jenkins.disco;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CyberArkIdentityClient two-step login flow.
 * Uses MockWebServer to simulate CyberArk Identity API responses.
 */
public class CyberArkIdentityClientTest {

    private MockWebServer server;
    private CyberArkIdentityClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new CyberArkIdentityClient(new OkHttpClient());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    public void login_returnsToken_onSuccessfulTwoStepFlow() throws Exception {
        enqueuStartAuth("session-123", "mech-456");
        enqueueAdvanceAuth("my-bearer-token");

        byte[] token = client.login(server.url("").toString(), "user@example.com",
                "password".getBytes(StandardCharsets.UTF_8));

        assertThat(new String(token, StandardCharsets.UTF_8)).isEqualTo("my-bearer-token");
    }

    @Test
    public void login_withTenantId_returnsToken() throws Exception {
        enqueuStartAuth("sess-abc", "mech-xyz");
        enqueueAdvanceAuth("tenant-token-99");

        byte[] token = client.login(server.url("").toString(), "tenant-uuid",
                "user@example.com", "pass".getBytes(StandardCharsets.UTF_8));

        assertThat(new String(token, StandardCharsets.UTF_8)).isEqualTo("tenant-token-99");
    }

    // ── Request structure ─────────────────────────────────────────────────────

    @Test
    public void login_startAuthRequest_usesUserField() throws Exception {
        enqueuStartAuth("s1", "m1");
        enqueueAdvanceAuth("tok");

        client.login(server.url("").toString(), "alice@example.com",
                "secret".getBytes(StandardCharsets.UTF_8));

        RecordedRequest startReq = server.takeRequest();
        assertThat(startReq.getPath()).endsWith("/Security/StartAuthentication");
        assertThat(startReq.getBody().readUtf8()).contains("\"User\":\"alice@example.com\"");
    }

    @Test
    public void login_advanceAuthRequest_containsSessionAndMechanismId() throws Exception {
        enqueuStartAuth("my-session", "my-mech");
        enqueueAdvanceAuth("tok");

        client.login(server.url("").toString(), "user@example.com",
                "pw".getBytes(StandardCharsets.UTF_8));

        server.takeRequest(); // consume StartAuthentication
        RecordedRequest advanceReq = server.takeRequest();
        String body = advanceReq.getBody().readUtf8();
        assertThat(advanceReq.getPath()).endsWith("/Security/AdvanceAuthentication");
        assertThat(body).contains("\"SessionId\":\"my-session\"");
        assertThat(body).contains("\"MechanismId\":\"my-mech\"");
        assertThat(body).contains("\"Action\":\"Answer\"");
    }

    @Test
    public void login_startAuthRequest_setsNativeClientHeader() throws Exception {
        enqueuStartAuth("s", "m");
        enqueueAdvanceAuth("t");

        client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("X-IDAP-NATIVE-CLIENT")).isEqualTo("true");
    }

    // ── Token caching ─────────────────────────────────────────────────────────

    @Test
    public void login_returnsCachedToken_onSecondCallWithinTtl() throws Exception {
        enqueuStartAuth("s", "m");
        enqueueAdvanceAuth("cached-token");

        byte[] pw1 = "pass".getBytes(StandardCharsets.UTF_8);
        byte[] pw2 = "pass".getBytes(StandardCharsets.UTF_8);

        byte[] first  = client.login(server.url("").toString(), "user@example.com", pw1);
        byte[] second = client.login(server.url("").toString(), "user@example.com", pw2);

        assertThat(new String(first, StandardCharsets.UTF_8)).isEqualTo("cached-token");
        assertThat(new String(second, StandardCharsets.UTF_8)).isEqualTo("cached-token");
        // Only two requests made (StartAuth + AdvanceAuth), not four
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    // ── Password zeroing ──────────────────────────────────────────────────────

    @Test
    public void login_zeroesPasswordBytesAfterUse() throws Exception {
        enqueuStartAuth("s", "m");
        enqueueAdvanceAuth("tok");

        byte[] password = "secret123".getBytes(StandardCharsets.UTF_8);
        client.login(server.url("").toString(), "user@example.com", password);

        for (byte b : password) {
            assertThat(b).isEqualTo((byte) 0);
        }
    }

    @Test
    public void login_zeroesPasswordBytes_evenOnFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        byte[] password = "secret".getBytes(StandardCharsets.UTF_8);
        try {
            client.login(server.url("").toString(), "user@example.com", password);
        } catch (Exception ignored) {}

        for (byte b : password) {
            assertThat(b).isEqualTo((byte) 0);
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    public void login_throwsOnStartAuth500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_032")
                .hasMessageContaining("StartAuthentication");
    }

    @Test
    public void login_throwsOnStartAuth401() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_032");
    }

    @Test
    public void login_throwsWhenStartAuthHasNoResult() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"Success\":true}"));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_033")
                .hasMessageContaining("no Result");
    }

    @Test
    public void login_throwsWhenNoUpMechanism() {
        String noUpMech = "{"
                + "\"Result\":{"
                + "  \"SessionId\":\"s1\","
                + "  \"Challenges\":[{\"Mechanisms\":[{\"Name\":\"OATH\",\"MechanismId\":\"m1\"}]}]"
                + "}}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(noUpMech));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_034")
                .hasMessageContaining("password (UP) mechanism");
    }

    @Test
    public void login_throwsOnAdvanceAuth500() {
        enqueuStartAuth("s", "m");
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_035")
                .hasMessageContaining("AdvanceAuthentication");
    }

    @Test
    public void login_throwsWhenAdvanceAuthHasNoToken() {
        enqueuStartAuth("s", "m");
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"Result\":{\"SomeOtherField\":\"value\"}}"));

        assertThatThrownBy(() -> client.login(server.url("").toString(), "u@example.com",
                "p".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("DISCO_036")
                .hasMessageContaining("no auth token");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enqueuStartAuth(String sessionId, String mechanismId) {
        String body = "{"
                + "\"Result\":{"
                + "  \"SessionId\":\"" + sessionId + "\","
                + "  \"Challenges\":[{"
                + "    \"Mechanisms\":[{"
                + "      \"Name\":\"UP\","
                + "      \"MechanismId\":\"" + mechanismId + "\""
                + "    }]"
                + "  }]"
                + "}}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body));
    }

    private void enqueueAdvanceAuth(String token) {
        String body = "{\"Result\":{\"Token\":\"" + token + "\"}}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body));
    }
}
