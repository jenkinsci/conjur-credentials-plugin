package org.conjur.jenkins.authenticator;

import okhttp3.*;
import org.junit.Assume;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Integration test against live Conjur Cloud using certificate authentication.
 * Authenticates via authn-cert (v2 API), lists secrets, retrieves a secret.
 *
 * Run:
 *   mvn test -Dtest=ConjurCertAuthenticatorConjurCloudIT \
 *     -Dconjur.cloud.url=https://57s7k26zx9x54w.secretsmgr.integration-cyberark.cloud \
 *     -Dconjur.cloud.account=conjur \
 *     -Dconjur.cloud.certauth.serviceId=certauth \
 *     -Dconjur.cloud.certauth.hostId=host/data/certtest \
 *     -Dconjur.cloud.keystore=/Users/pstefanski/development/temp-cert/client.p12 \
 *     -Dconjur.cloud.keystore.password=changeit
 */
public class ConjurCertAuthenticatorConjurCloudIT {

    private String conjurUrl;
    private String account;
    private String serviceId;
    private String hostId;
    private String keystorePath;
    private String keystorePassword;
    private OkHttpClient client;

    @Before
    public void setup() throws Exception {
        conjurUrl = System.getProperty("conjur.cloud.url");
        account = System.getProperty("conjur.cloud.account", "conjur");
        serviceId = System.getProperty("conjur.cloud.certauth.serviceId", "certauth");
        hostId = System.getProperty("conjur.cloud.certauth.hostId", "host/data/certtest");
        keystorePath = System.getProperty("conjur.cloud.keystore");
        keystorePassword = System.getProperty("conjur.cloud.keystore.password", "changeit");

        Assume.assumeTrue(
                "Skipped: set -Dconjur.cloud.url and -Dconjur.cloud.keystore to run",
                conjurUrl != null && keystorePath != null);

        client = buildMtlsClient();
    }

    @Test
    public void testCertAuthentication() throws Exception {
        System.out.println("=== authn-cert authentication (v2 API) ===");

        String encodedHost = URLEncoder.encode(hostId, "utf-8");
        String url = String.format("%s/api/authn-cert/%s/%s/%s/authenticate",
                conjurUrl, serviceId, account, encodedHost);

        System.out.println("  POST " + url);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("text/plain"), ""))
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("  HTTP " + response.code());
            String body = response.body() != null ? response.body().string() : "";

            if (response.code() == 200) {
                System.out.println("  SUCCESS — got Conjur token (" + body.length() + " chars)");
                assertFalse("Token should not be empty", body.isEmpty());
            } else {
                System.out.println("  RESULT: authn-cert returned HTTP " + response.code());
                System.out.println("  Body: " + body.substring(0, Math.min(body.length(), 300)));
                Assume.assumeTrue("authn-cert not enabled on tenant (HTTP " + response.code() + ")",
                        response.code() != 404);
                fail("authn-cert returned " + response.code() + ": " + body);
            }
        }
    }

    @Test
    public void testCertAuthAndListSecrets() throws Exception {
        System.out.println("=== authn-cert → list secrets (v2 API) ===");

        byte[] token = authenticate();
        assertNotNull("Should get auth token", token);

        String tokenB64 = new String(token, StandardCharsets.US_ASCII);
        String resourcesUrl = String.format("%s/api/resources/%s?kind=variable&limit=20",
                conjurUrl, account);

        System.out.println("  GET " + resourcesUrl);

        Request request = new Request.Builder()
                .url(resourcesUrl)
                .get()
                .addHeader("Authorization", "Token token=\"" + tokenB64 + "\"")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("  HTTP " + response.code());

            if (response.code() == 200) {
                System.out.println("  Variables found:");
                // Simple JSON array parsing — each variable has "id" field
                int count = 0;
                for (String line : body.split("\"id\"\\s*:\\s*\"")) {
                    if (count == 0) { count++; continue; }
                    String id = line.substring(0, line.indexOf('"'));
                    System.out.println("    " + id);
                    count++;
                }
                System.out.println("  Total: " + (count - 1) + " variables");
                assertTrue("Should find at least one variable", count > 1);
            } else {
                System.out.println("  Body: " + body.substring(0, Math.min(body.length(), 300)));
                fail("List resources returned " + response.code());
            }
        }
    }

    @Test
    public void testCertAuthAndRetrieveSecret() throws Exception {
        System.out.println("=== authn-cert → retrieve secret (v2 API) ===");

        byte[] token = authenticate();
        assertNotNull("Should get auth token", token);

        String tokenB64 = new String(token, StandardCharsets.US_ASCII);
        String secretId = System.getProperty("conjur.cloud.secretId", "data/vault/test-cert-secret");
        String encodedSecret = URLEncoder.encode(secretId, "utf-8");
        String secretUrl = String.format("%s/api/secrets/%s/variable/%s",
                conjurUrl, account, encodedSecret);

        System.out.println("  GET " + secretUrl);

        Request request = new Request.Builder()
                .url(secretUrl)
                .get()
                .addHeader("Authorization", "Token token=\"" + tokenB64 + "\"")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("  HTTP " + response.code());

            if (response.code() == 200) {
                System.out.println("  SECRET VALUE: " + body);
                assertFalse("Secret should not be empty", body.isEmpty());
            } else {
                System.out.println("  Body: " + body.substring(0, Math.min(body.length(), 300)));
                fail("Get secret returned " + response.code());
            }
        }
    }

    private byte[] authenticate() throws IOException {
        String encodedHost = URLEncoder.encode(hostId, "utf-8");
        String url = String.format("%s/api/authn-cert/%s/%s/%s/authenticate",
                conjurUrl, serviceId, account, encodedHost);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("text/plain"), ""))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() != 200) {
                String body = response.body() != null ? response.body().string() : "";
                System.out.println("  authn-cert HTTP " + response.code() + ": " + body);
                Assume.assumeTrue("authn-cert not enabled on tenant (HTTP " + response.code() + ")",
                        response.code() != 404);
                fail("authn-cert failed: " + response.code() + " " + body);
                return null;
            }
            String rawToken = response.body().string();
            return Base64.getEncoder().withoutPadding()
                    .encodeToString(rawToken.getBytes(StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.US_ASCII);
        }
    }

    private OkHttpClient buildMtlsClient() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        // Trust all server certs (Conjur Cloud uses public CA — equivalent to curl -k)
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String type) {}
                    public void checkServerTrusted(X509Certificate[] certs, String type) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), trustAll, new SecureRandom());

        return new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAll[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }
}
