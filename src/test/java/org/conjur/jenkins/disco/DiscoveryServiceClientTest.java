package org.conjur.jenkins.disco;

import okhttp3.*;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceClient;
import org.conjur.jenkins.disco.discovery.DiscoveryServiceResult;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoveryServiceClientTest {

    private static final String BASE_URL = "https://platform-discovery.cyberark.cloud";
    private static final String SUBDOMAIN = "acme";

    private static final String VALID_RESPONSE = "{"
            + "\"tenant_id\":\"tenant-uuid-123\","
            + "\"services\":["
            + "  {\"service_name\":\"identity_administration\",\"endpoints\":[{\"is_active\":true,\"type\":\"main\",\"api\":\"https://xyz.id.cyberark.cloud\",\"ui\":\"\"}]},"
            + "  {\"service_name\":\"discoverycontext\",\"endpoints\":[{\"is_active\":true,\"type\":\"main\",\"api\":\"https://acme.inventory.cyberark.cloud/api\",\"ui\":\"\"}]}"
            + "]}";

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    public void resolve_returnsTenantId() throws Exception {
        DiscoveryServiceClient client = clientWithResponse(200, "OK", VALID_RESPONSE);
        DiscoveryServiceResult result = client.resolve(BASE_URL, SUBDOMAIN);
        assertThat(result.getTenantId()).isEqualTo("tenant-uuid-123");
    }

    @Test
    public void resolve_returnsIdentityBaseUrl() throws Exception {
        DiscoveryServiceClient client = clientWithResponse(200, "OK", VALID_RESPONSE);
        DiscoveryServiceResult result = client.resolve(BASE_URL, SUBDOMAIN);
        assertThat(result.getIdentityBaseUrl()).isEqualTo("https://xyz.id.cyberark.cloud");
    }

    @Test
    public void resolve_returnsSnapshotLinksUrl() throws Exception {
        DiscoveryServiceClient client = clientWithResponse(200, "OK", VALID_RESPONSE);
        DiscoveryServiceResult result = client.resolve(BASE_URL, SUBDOMAIN);
        assertThat(result.getResolvedUrl())
                .isEqualTo("https://acme.inventory.cyberark.cloud/api/ingestions/jenkins/snapshot-links");
    }

    @Test
    public void resolve_returnsDiscoveryContextBaseUrl() throws Exception {
        DiscoveryServiceClient client = clientWithResponse(200, "OK", VALID_RESPONSE);
        DiscoveryServiceResult result = client.resolve(BASE_URL, SUBDOMAIN);
        assertThat(result.getDiscoveryContextBaseUrl())
                .isEqualTo("https://acme.inventory.cyberark.cloud/api");
    }

    // ── 403 → access denied ───────────────────────────────────────────────────

    @Test
    public void resolve_throwsIOException_on403_withDisc010Code() {
        DiscoveryServiceClient client = clientWithResponse(403, "Forbidden", "");
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_056");
    }

    @Test
    public void resolve_throwsIOException_on403_mentionsSubdomain() {
        DiscoveryServiceClient client = clientWithResponse(403, "Forbidden", "");
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(SUBDOMAIN);
    }

    // ── 404 → invalid subdomain ───────────────────────────────────────────────

    @Test
    public void resolve_throwsIOException_on404() {
        DiscoveryServiceClient client = clientWithResponse(404, "Not Found", "");
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_022");
    }

    // ── Other failures ────────────────────────────────────────────────────────

    @Test
    public void resolve_throwsIOException_on503() {
        DiscoveryServiceClient client = clientWithResponse(503, "Service Unavailable", "");
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_006");
    }

    @Test
    public void resolve_throwsIOException_onEmptyBody() {
        DiscoveryServiceClient client = clientWithResponse(200, "OK", "");
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_007");
    }

    @Test
    public void resolve_throwsIOException_onMissingTenantId() {
        String noTenant = "{\"services\":[]}";
        DiscoveryServiceClient client = clientWithResponse(200, "OK", noTenant);
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_009");
    }

    @Test
    public void resolve_throwsIOException_onNetworkFailure() throws Exception {
        Call call = Mockito.mock(Call.class);
        Mockito.when(call.execute()).thenThrow(new IOException("connection refused"));
        OkHttpClient http = Mockito.mock(OkHttpClient.class);
        Mockito.when(http.newCall(Mockito.any())).thenReturn(call);

        DiscoveryServiceClient client = new DiscoveryServiceClient(http);
        assertThatThrownBy(() -> client.resolve(BASE_URL, SUBDOMAIN))
                .isInstanceOf(IOException.class);
    }

    @Test
    public void resolve_throwsIOException_onMalformedBaseUrl() {
        DiscoveryServiceClient client = new DiscoveryServiceClient(Mockito.mock(OkHttpClient.class));
        assertThatThrownBy(() -> client.resolve("not-a-url", SUBDOMAIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("DISCO_005");
    }

    // ── Query parameters ───────────────────────────────────────────────────

    @Test
    public void resolve_sendsRequiredQueryParameters() throws Exception {
        org.mockito.ArgumentCaptor<Request> captor = org.mockito.ArgumentCaptor.forClass(Request.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url(BASE_URL).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(VALID_RESPONSE, MediaType.get("application/json")))
                .build();
        Call call = Mockito.mock(Call.class);
        Mockito.when(call.execute()).thenReturn(response);
        OkHttpClient http = Mockito.mock(OkHttpClient.class);
        Mockito.when(http.newCall(captor.capture())).thenReturn(call);

        DiscoveryServiceClient client = new DiscoveryServiceClient(http);
        client.resolve(BASE_URL, SUBDOMAIN);

        String url = captor.getValue().url().toString();
        assertThat(url).contains("bySubdomain=acme");
        assertThat(url).contains("allEndpoints=false");
        assertThat(url).contains("selectedFields=tenant_id%2Cservices");
        assertThat(url).contains("selectedServices=identity_administration%2Cdiscoverycontext");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private DiscoveryServiceClient clientWithResponse(int code, String message, String body) {
        try {
            Response response = new Response.Builder()
                    .request(new Request.Builder().url(BASE_URL).build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code).message(message)
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build();
            Call call = Mockito.mock(Call.class);
            Mockito.when(call.execute()).thenReturn(response);
            OkHttpClient http = Mockito.mock(OkHttpClient.class);
            Mockito.when(http.newCall(Mockito.any())).thenReturn(call);
            return new DiscoveryServiceClient(http);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
