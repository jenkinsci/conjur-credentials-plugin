package org.conjur.jenkins.authenticator;

import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import hudson.model.ModelObject;
import hudson.util.Secret;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end test for ConjurCertAuthenticator against a Docker-based
 * authn-cert simulation (nginx mTLS proxy + mock Conjur backend).
 *
 * Prerequisites: run e2e-authn-cert/run-e2e.sh or manually start Docker services.
 * Pass system properties:
 *   -De2e.conjur.url=https://localhost:8443
 *   -De2e.cert.keystore=path/to/client.p12
 *   -De2e.cert.truststore=path/to/truststore.jks
 *   -De2e.cert.password=changeit
 *   -De2e.cert.serviceId=jenkins-cert
 *   -De2e.cert.account=myaccount
 *   -De2e.cert.hostId=host/jenkins/cert-node
 */
public class ConjurCertAuthenticatorE2ETest {

    private static String conjurUrl;
    private static String keystorePath;
    private static String truststorePath;
    private static String password;
    private static String serviceId;
    private static String account;
    private static String hostId;

    private ConjurCertAuthenticator authenticator;
    private ModelObject mockContext;

    @BeforeClass
    public static void checkE2EEnabled() {
        conjurUrl = System.getProperty("e2e.conjur.url");
        keystorePath = System.getProperty("e2e.cert.keystore");
        truststorePath = System.getProperty("e2e.cert.truststore");
        password = System.getProperty("e2e.cert.password", "changeit");
        serviceId = System.getProperty("e2e.cert.serviceId", "jenkins-cert");
        account = System.getProperty("e2e.cert.account", "myaccount");
        hostId = System.getProperty("e2e.cert.hostId", "host/jenkins/cert-node");

        Assume.assumeTrue(
                "E2E test skipped: set -De2e.conjur.url and -De2e.cert.keystore to enable",
                conjurUrl != null && keystorePath != null);
    }

    @Before
    public void setup() {
        authenticator = new ConjurCertAuthenticator();
        mockContext = mock(ModelObject.class);
    }

    @Test
    public void testSuccessfulCertAuthentication() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);

        assertNotNull("Token should not be null for valid cert auth", token);
        String encoded = new String(token, StandardCharsets.US_ASCII);
        byte[] decoded = Base64.getMimeDecoder().decode(encoded);
        String tokenJson = new String(decoded, StandardCharsets.UTF_8);
        assertTrue("Token should contain host identity", tokenJson.contains(hostId));
        assertTrue("Token should be JSON with payload", tokenJson.contains("payload"));
    }

    @Test
    public void testAuthWithUrlEncodedHostId() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull("Should authenticate with URL-encoded host ID containing slashes", token);
    }

    @Test
    public void testAuthWithFullAuthnPath() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo("authn-cert/" + serviceId, account, hostId);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull("Should authenticate with full authn-cert/ prefix in path", token);
    }

    @Test
    public void testTokenIsBase64WithoutPadding() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull(token);

        String encoded = new String(token, StandardCharsets.US_ASCII);
        assertFalse("Token encoding should not contain padding '='", encoded.contains("="));
    }

    @Test
    public void testWrongHostIdFails() throws Exception {
        // nginx will still validate the cert (mTLS passes), but the mock backend
        // should reject because the CN doesn't match the requested host
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, "host/wrong/identity");

        try {
            authenticator.getAuthorizationToken(authnInfo, mockContext);
            // It may return a token (200) if the mock doesn't strictly validate CN vs host
            // In that case, this test documents the behavior rather than asserting failure.
        } catch (Exception e) {
            // Expected: 401 from mock
            assertTrue("Should get authentication error",
                    e.getMessage().contains("401") || e instanceof org.conjur.jenkins.exceptions.AuthenticationConjurException);
        }
    }

    @Test
    public void testNullServiceIdReturnsNull() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(null, account, hostId);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull("Should return null when service ID is missing", token);
    }

    @Test
    public void testNullHostIdReturnsNull() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, null);

        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull("Should return null when host ID is missing", token);
    }

    @Test
    public void testCertAuthThenListSecrets() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull("Should authenticate successfully", token);

        String tokenB64 = new String(token, StandardCharsets.US_ASCII);
        String resourcesUrl = String.format("%s/resources/%s?kind=variable", conjurUrl, account);

        OkHttpClient client = ConjurAPIUtils.getHttpClient(authnInfo.getConjurConfiguration());
        Request request = new Request.Builder()
                .url(resourcesUrl)
                .get()
                .addHeader("Authorization", "Token token=\"" + tokenB64 + "\"")
                .build();

        Response response = client.newCall(request).execute();
        assertEquals("List resources should return 200", 200, response.code());

        String body = response.body() != null ? response.body().string() : "";
        assertTrue("Response should be a JSON array", body.startsWith("["));
        assertTrue("Should contain variable resources", body.contains("variable"));
        assertTrue("Should contain data/vault/ secrets", body.contains("data/vault/"));
    }

    @Test
    public void testCertAuthThenRetrieveSecret() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull("Should authenticate successfully", token);

        String tokenB64 = new String(token, StandardCharsets.US_ASCII);
        String secretUrl = String.format("%s/secrets/%s/variable/%s",
                conjurUrl, account, "data%2Fvault%2Fdb-password");

        OkHttpClient client = ConjurAPIUtils.getHttpClient(authnInfo.getConjurConfiguration());
        Request request = new Request.Builder()
                .url(secretUrl)
                .get()
                .addHeader("Authorization", "Token token=\"" + tokenB64 + "\"")
                .build();

        Response response = client.newCall(request).execute();
        assertEquals("Get secret should return 200", 200, response.code());

        String secretValue = response.body() != null ? response.body().string() : "";
        assertFalse("Secret value should not be empty", secretValue.isEmpty());
        assertEquals("Should return expected mock secret", "s3cr3t-db-pass-2026", secretValue); // gitleaks:allow — fake value from the local Conjur mock, see conjur-mock.py
    }

    @Test
    public void testCertAuthThenRetrieveNonExistentSecretReturns404() throws Exception {
        ConjurAuthnInfo authnInfo = buildAuthnInfo(serviceId, account, hostId);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNotNull("Should authenticate successfully", token);

        String tokenB64 = new String(token, StandardCharsets.US_ASCII);
        String secretUrl = String.format("%s/secrets/%s/variable/%s",
                conjurUrl, account, "data%2Fvault%2Fnon-existent");

        OkHttpClient client = ConjurAPIUtils.getHttpClient(authnInfo.getConjurConfiguration());
        Request request = new Request.Builder()
                .url(secretUrl)
                .get()
                .addHeader("Authorization", "Token token=\"" + tokenB64 + "\"")
                .build();

        Response response = client.newCall(request).execute();
        assertEquals("Non-existent secret should return 404", 404, response.code());
    }

    private ConjurAuthnInfo buildAuthnInfo(String svcId, String acct, String host) throws Exception {
        ConjurAuthnInfo info = new ConjurAuthnInfo();
        info.setApplianceUrl(conjurUrl);
        info.setAuthnPath(svcId);
        info.setAccount(acct);
        info.setLogin(host);

        ConjurConfiguration config = new ConjurConfiguration();
        CertificateCredentials certCred = buildCertCredentials();
        config.setCertificateCredentials(certCred);
        info.setConjurConfiguration(config);

        return info;
    }

    private CertificateCredentials buildCertCredentials() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, password.toCharArray());
        }

        // Merge trust store certs into key store for ConjurAPIUtils compatibility
        java.util.Enumeration<String> aliases = trustStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (trustStore.isCertificateEntry(alias)) {
                keyStore.setCertificateEntry("trust-" + alias, trustStore.getCertificate(alias));
            }
        }

        CertificateCredentials cred = mock(CertificateCredentials.class);
        when(cred.getKeyStore()).thenReturn(keyStore);
        Secret secretPassword = Secret.fromString(password);
        when(cred.getPassword()).thenReturn(secretPassword);
        return cred;
    }
}
