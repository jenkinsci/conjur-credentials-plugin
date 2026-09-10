package org.conjur.jenkins.authenticator;

import hudson.model.ModelObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Integration test for ConjurCertAuthenticator using MockWebServer.
 * Verifies the authenticator correctly calls the Conjur authn-cert endpoint.
 *
 * Run with: mvn test -DforkCount=0 -Dtest=ConjurCertAuthenticatorIT
 *
 * Note: Full end-to-end testing with real Conjur requires Conjur Enterprise
 * with the Authenticator Service binary, which is not available in OSS.
 */
public class ConjurCertAuthenticatorIT {

    private static final Logger LOG = Logger.getLogger(ConjurCertAuthenticatorIT.class.getName());

    private MockWebServer server;
    private ConjurCertAuthenticator authenticator;
    private ConjurAuthnInfo authnInfo;
    private ModelObject mockContext;

    @Before
    public void setup() throws Exception {
        server = new MockWebServer();
        server.start();

        authenticator = new ConjurCertAuthenticator();
        mockContext = mock(ModelObject.class);

        authnInfo = new ConjurAuthnInfo();
        authnInfo.setApplianceUrl(server.url("").toString().replaceAll("/$", ""));
        authnInfo.setAuthnPath("jenkins-cert");
        authnInfo.setAccount("myaccount");
        authnInfo.setLogin("host/jenkins/my-node");
        authnInfo.setConjurConfiguration(new ConjurConfiguration());
    }

    @After
    public void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    public void testSuccessfulAuthentication() throws Exception {
        String tokenPayload = "{\"protected\":\"...\",\"payload\":\"...\",\"signature\":\"...\"}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(tokenPayload));

        byte[] result = authenticator.getAuthorizationToken(authnInfo, mockContext);

        assertNotNull("Token should not be null", result);
        String encoded = new String(result, StandardCharsets.US_ASCII);
        String expectedEncoded = Base64.getEncoder().withoutPadding()
                .encodeToString(tokenPayload.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedEncoded, encoded);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        String path = request.getPath();
        LOG.info("Request path: " + path);
        assertTrue("Path should contain authn-cert", path.contains("/authn-cert/jenkins-cert/"));
        assertTrue("Path should contain account", path.contains("/myaccount/"));
        assertTrue("Path should contain encoded host ID", path.contains("host%2Fjenkins%2Fmy-node"));
        assertTrue("Path should end with /authenticate", path.endsWith("/authenticate"));
    }

    @Test
    public void testUrlEncodesHostIdWithSlashes() throws Exception {
        authnInfo.setLogin("host/very/deep/path/node");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("token"));

        authenticator.getAuthorizationToken(authnInfo, mockContext);

        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertTrue("Host ID slashes should be URL-encoded",
                path.contains("host%2Fvery%2Fdeep%2Fpath%2Fnode"));
    }

    @Test
    public void testFullAuthnPathNotPrefixed() throws Exception {
        authnInfo.setAuthnPath("authn-cert/custom-service");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("token"));

        authenticator.getAuthorizationToken(authnInfo, mockContext);

        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertTrue("Full authn path should not get double-prefixed",
                path.startsWith("/authn-cert/custom-service/"));
        assertFalse("Should not have authn-cert twice",
                path.contains("/authn-cert/authn-cert/"));
    }

    @Test
    public void testServiceIdOnlyGetsPrefixed() throws Exception {
        authnInfo.setAuthnPath("my-cert-svc");
        server.enqueue(new MockResponse().setResponseCode(200).setBody("token"));

        authenticator.getAuthorizationToken(authnInfo, mockContext);

        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertTrue("Short service ID should get authn-cert/ prefix",
                path.startsWith("/authn-cert/my-cert-svc/"));
    }

    @Test
    public void testPostsEmptyBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("token"));

        authenticator.getAuthorizationToken(authnInfo, mockContext);

        RecordedRequest request = server.takeRequest();
        assertEquals("Body should be empty (cert presented via mTLS)", 0, request.getBodySize());
    }

    @Test
    public void test401ReturnsAuthenticationException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized"));

        try {
            authenticator.getAuthorizationToken(authnInfo, mockContext);
            fail("Should have thrown AuthenticationConjurException");
        } catch (org.conjur.jenkins.exceptions.AuthenticationConjurException e) {
            assertEquals(401, e.getErrorCode());
        }
    }

    @Test
    public void test403ReturnsIOException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden"));

        try {
            authenticator.getAuthorizationToken(authnInfo, mockContext);
            fail("Should have thrown IOException");
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage().contains("403"));
        }
    }

    @Test
    public void test500ReturnsIOException() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        try {
            authenticator.getAuthorizationToken(authnInfo, mockContext);
            fail("Should have thrown IOException");
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    @Test
    public void testNullAuthnPathReturnsNull() throws Exception {
        authnInfo.setAuthnPath(null);
        byte[] result = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull("Should return null when authnPath is missing", result);
        assertEquals("No request should be made", 0, server.getRequestCount());
    }

    @Test
    public void testNullLoginReturnsNull() throws Exception {
        authnInfo.setLogin(null);
        byte[] result = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull("Should return null when login is missing", result);
        assertEquals("No request should be made", 0, server.getRequestCount());
    }

    @Test
    public void testTokenIsBase64EncodedWithoutPadding() throws Exception {
        String rawToken = "conjur-token-response";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(rawToken));

        byte[] result = authenticator.getAuthorizationToken(authnInfo, mockContext);

        String encoded = new String(result, StandardCharsets.US_ASCII);
        assertFalse("Token should not have padding", encoded.contains("="));
        // Decode using MIME decoder which is lenient about padding
        byte[] decoded = Base64.getMimeDecoder().decode(encoded);
        assertEquals(rawToken, new String(decoded, StandardCharsets.UTF_8));
    }
}
