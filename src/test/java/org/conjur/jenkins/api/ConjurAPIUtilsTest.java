package org.conjur.jenkins.api;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProviderManager.Configuration;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.security.auth.x500.X500Principal;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurAPIUtilsTest {

    @Mock
    private ACL aclSystem;

    @Mock
    private CredentialsProvider credentialsProvider;

    @Mock
    private ConjurAPIUtils conjurAPIUtils;

    @Mock
    private Configuration configuration;

    @Mock
    private CertificateCredentials certificate;

    @Mock
    private ConjurSecretCredentials secretCredentials;

    @Mock
    private Item item;

    @Mock
    private ItemGroup<Item> context;

    @Mock
    private Jenkins jenkinsMock;

    @Mock
    private StaplerRequest mockedStaplerRequest;

    private MockedStatic<Jenkins> jenkinsStaticMock;

    private MockedStatic<Stapler> staplerMock;


    @BeforeEach
    void beforeEach() {
        when(jenkinsMock.hasPermission(Jenkins.ADMINISTER)).thenReturn(true);
        jenkinsStaticMock = mockStatic(Jenkins.class);
        when(Jenkins.get()).thenReturn(jenkinsMock);
        staplerMock = mockStatic(Stapler.class);
    }

    @AfterEach
    void afterEach() {
        if (jenkinsStaticMock != null) {
            jenkinsStaticMock.close();
        }
        if (staplerMock != null) {
            staplerMock.close();
        }
    }

    @SuppressWarnings("static-access")
    @Test
    void testGetHttpClient() {
        ConjurConfiguration configuration = mock(ConjurConfiguration.class);
        ConjurAPIUtils conjurAPIUtilsSpy = spy(new ConjurAPIUtils());
        when(conjurAPIUtilsSpy.getHttpClient(configuration)).thenReturn(null);
        OkHttpClient client = conjurAPIUtilsSpy.getHttpClient(configuration);

        assertNotNull(client);
    }

    @Test
    void testCertificateFromConfigurationNegative() {
        ConjurConfiguration configuration = new ConjurConfiguration();
        configuration.setCertificateCredentialID(null);

        CertificateCredentials result = ConjurAPIUtils.certificateFromConfiguration(configuration);

        assertNull(result);
    }

    @Test
    void testHttpClientWithCertificateNegative() throws Exception {
        CertificateCredentials certificate = mock(CertificateCredentials.class);
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        when(certificate.getKeyStore()).thenReturn(keyStore);
        assertThrows(IllegalArgumentException.class, () ->

            ConjurAPIUtils.httpClientWithCertificate(certificate));
    }

    @Test
    void testHttpClientWithCertificateSuccess() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        when(certificate.getKeyStore()).thenReturn(keyStore);
        when(certificate.getPassword()).thenReturn(mock(Secret.class));
        when(certificate.getPassword().getPlainText()).thenReturn("password");
        X509Certificate mockCertificate = mock(X509Certificate.class);
        String alias = "testAlias";
        keyStore.setCertificateEntry(alias, mockCertificate);
        X500Principal principal = new X500Principal("CN=Test");
        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(certificate.getKeyStore()).thenReturn(keyStore);
        Secret password = mock(Secret.class);
        when(password.getPlainText()).thenReturn("password");
        when(certificate.getPassword()).thenReturn(password);
        OkHttpClient client = ConjurAPIUtils.httpClientWithCertificate(certificate);

        assertNotNull(client);
    }


    @Test
    void testHttpClientWithCertificateKeyManagerFailure() {
        KeyStore keyStore = mock(KeyStore.class);
        when(certificate.getKeyStore()).thenReturn(keyStore);
        when(certificate.getPassword()).thenReturn(mock(Secret.class));
        when(certificate.getPassword().getPlainText()).thenReturn("password");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> ConjurAPIUtils.httpClientWithCertificate(certificate));

        assertTrue(exception.getMessage().contains("Error configuring server certificates."));
    }

    @Test
    void testDefaultIFBlankReturnsDefaultValue() {
        String value = ConjurAPIUtils.defaultIfBlank(null, "default");
        assertEquals("default", value);
    }

    @Test
    void testDefaultIFBlankReturnsValue() {
        String value = ConjurAPIUtils.defaultIfBlank("sample-value", "default");
        assertEquals("sample-value", value);
    }

    @Test
    void testDefaultIFBlankValueReturnsDefaultValue() {
        String value = ConjurAPIUtils.defaultIfBlank("", "default");
        assertEquals("default", value);
    }

    @Test
    void testGetStringFromException() {
        Exception exception = new Exception("Test Exception");
        StringBuffer result = ConjurAPIUtils.getStringFromException(exception);
        System.out.println(result);
        assertTrue(result.toString().contains("Test Exception"));


    }

    @Test
    void testGetItemFromReferer() throws Exception {
        String referer = "http://localhost:8080/folder-a/job/folder-b/job/pipeline-job/";
        mockStaticStaplerWithReferer(referer);

        Item resultItem = ConjurAPIUtils.getItemFromReferer();

        assertNotNull(resultItem);
        assertEquals(item, resultItem);
    }

    @Test
    void testExtractJobPathFromUrl() {
        String urlPath = "/job/folder1/job/myJob/";
        String result = ConjurAPIUtils.extractJobPathFromUrl(urlPath);

        assertEquals("folder1/myJob", result);
    }

    @Test
    void testExtractJobPathFromUrlThrowsException() {
        String urlPath = "folder1/myJob/";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ConjurAPIUtils.extractJobPathFromUrl(urlPath));

        assertEquals("Invalid job path: folder1/myJob/", exception.getMessage());
    }

    @Test
    void testValidateCredentialWithSecret() {
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("mocked-secret");
        when(secretCredentials.getContext()).thenReturn(context);
        when(secretCredentials.getSecret()).thenReturn(mockSecret);

        FormValidation result = ConjurAPIUtils.validateCredential(context, secretCredentials);

        assertEquals(FormValidation.Kind.OK, result.kind);
        assertTrue(result.getMessage().contains("Successfully retrieved secret string"));
    }

    @Test
    void testValidateCredentialWithInvalidConjurException() {
        when(secretCredentials.getSecret())
                .thenThrow(new InvalidConjurSecretException("Secret Error"))
                .thenThrow(new InvalidConjurSecretException("Secret Error"));
        String referUrl = "/job/folder1/job/myJob/";
        mockStaticStaplerWithReferer(referUrl);
        when(secretCredentials.getContext()).thenReturn(context);

        FormValidation result = ConjurAPIUtils.validateCredential(context, secretCredentials);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("FAILED to retrieve secret"));
    }

    @Test
    void testValidateCredentialWithInvalidConjurExceptionReturnsSecretFromParent() {
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("mocked-secret");
        when(secretCredentials.getSecret())
                .thenThrow(new InvalidConjurSecretException("Secret Error"))
                .thenReturn(mockSecret);

        String referUrl = "/job/folder1/job/myJob/";
        mockStaticStaplerWithReferer(referUrl);
        when(secretCredentials.getContext()).thenReturn(context);
        FormValidation result = ConjurAPIUtils.validateCredential(context, secretCredentials);

        assertEquals(FormValidation.Kind.OK, result.kind);
        assertTrue(result.getMessage().contains("Successfully retrieved secret string"));

    }

    private void mockStaticStaplerWithReferer(String refererUrl) {
        when(mockedStaplerRequest.getReferer()).thenReturn(refererUrl);
        when(Stapler.getCurrentRequest()).thenReturn(mockedStaplerRequest);
        when(jenkinsMock.getItemByFullName(any())).thenReturn(item);

    }

}