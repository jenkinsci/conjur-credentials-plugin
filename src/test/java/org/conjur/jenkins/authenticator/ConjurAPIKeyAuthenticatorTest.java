package org.conjur.jenkins.authenticator;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.model.ModelObject;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConjurAPIKeyAuthenticatorTest {

    private ConjurAPIKeyAuthenticator authenticator;
    private ModelObject mockContext;
    private ConjurAuthnInfo authnInfo;

    @BeforeEach
    void beforeEach() {
        authenticator = new ConjurAPIKeyAuthenticator();
        mockContext = mock(ModelObject.class);
        authnInfo = new ConjurAuthnInfo();
        authnInfo.setApplianceUrl("https://conjur.example.com");
        authnInfo.setAuthnPath("authn");
        authnInfo.setAccount("myaccount");
        authnInfo.setLogin("mylogin");
        authnInfo.setApiKey("secret-api-key".getBytes(StandardCharsets.US_ASCII));
        authnInfo.setConjurConfiguration(new ConjurConfiguration());
    }

    @Test
    void testGetName() {
        assertEquals("APIKey", authenticator.getName());
    }

    @Test
    void testGetAuthorizationTokenSuccess() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("token-from-conjur");
            when(mockResponse.code()).thenReturn(200);
            when(mockResponse.message()).thenReturn("OK");

            byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);

            assertNotNull(token);
            String decoded = new String(token, StandardCharsets.US_ASCII);
            String expectedEncoded = java.util.Base64.getEncoder().withoutPadding()
                    .encodeToString("token-from-conjur".getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedEncoded, decoded);
        }
    }

    @Test
    void testGetAuthorizationToken401ThrowsAuthenticationConjurException() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("Unauthorized");
            when(mockResponse.code()).thenReturn(401);
            when(mockResponse.message()).thenReturn("Unauthorized");

            AuthenticationConjurException thrown = assertThrows(AuthenticationConjurException.class,
                    () -> authenticator.getAuthorizationToken(authnInfo, mockContext));
            assertEquals(401, thrown.getErrorCode());
        }
    }

    @Test
    void testGetAuthorizationTokenOtherErrorThrowsIOException() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("Error");
            when(mockResponse.code()).thenReturn(500);
            when(mockResponse.message()).thenReturn("Internal Server Error");

            IOException thrown = assertThrows(IOException.class,
                    () -> authenticator.getAuthorizationToken(authnInfo, mockContext));
            assertTrue(thrown.getMessage().contains("500"));
        }
    }

    @Test
    void testGetAuthorizationTokenNullRequestReturnsNull() throws Exception {
        authnInfo.setApiKey(null);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull(token);
    }

    @Test
    void testFillAuthnInfoWithMatchingCredential() {
        try (MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class);
             MockedStatic<CredentialsProvider> credentialsProviderMock = mockStatic(CredentialsProvider.class);
             MockedStatic<CredentialsMatchers> credentialsMatchersMock = mockStatic(CredentialsMatchers.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class);
             MockedStatic<DomainCredentials> mockDomainCredentials = mockStatic(DomainCredentials.class);
             MockedStatic<SystemCredentialsProvider> mockSystemCredentialProvider = mockStatic(SystemCredentialsProvider.class)) {


            UsernamePasswordCredentials mockCredential = mock(UsernamePasswordCredentials.class);
            when(mockCredential.getUsername()).thenReturn("mylogin");

            CredentialsMatcher mockCredentialsMatcher = mock(CredentialsMatcher.class);
            credentialsMatchersMock.when(() -> CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class)).thenReturn(mockCredentialsMatcher);

            SystemCredentialsProvider mockSystemProvider = mock(SystemCredentialsProvider.class);
            mockSystemCredentialProvider.when(SystemCredentialsProvider::getInstance).thenReturn(mockSystemProvider);

            Map<Domain, List<Credentials>> mockMapOfCredentials = new HashMap<>();
            when(mockSystemProvider.getDomainCredentialsMap()).thenReturn(mockMapOfCredentials);

            mockDomainCredentials.when(() -> DomainCredentials.getCredentials(mockMapOfCredentials, UsernamePasswordCredentials.class, Collections.emptyList(), mockCredentialsMatcher)).thenReturn(List.of(mockCredential));

            ConjurConfiguration mockConfig = mock(ConjurConfiguration.class);
            conjurApiMock.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(mockConfig);
            when(mockConfig.getCredentialID()).thenReturn("cred-id");

            Secret mockSecret = mock(Secret.class);
            when(mockSecret.getPlainText()).thenReturn("myapikey");
            when(mockCredential.getPassword()).thenReturn(mockSecret);

            jenkinsMock.when(Jenkins::get).thenReturn(mock(Jenkins.class));
            Jenkins jenkinsMockInstance = mock(Jenkins.class);
            jenkinsMock.when(Jenkins::get).thenReturn(jenkinsMockInstance);


            credentialsMatchersMock.when(() ->
                            CredentialsMatchers.firstOrNull(anyList(), any()))
                    .thenReturn(mockCredential);

            authenticator.fillAuthnInfo(authnInfo, mockContext);

            assertEquals("mylogin", authnInfo.getLogin());
            assertNotNull(authnInfo.getApiKey());
        }
    }

    @Test
    void testFillAuthnInfoNoCredentialFound() {
        // Reset authnInfo fields
        authnInfo.setLogin(null);
        authnInfo.setApiKey(null);

        try (MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class);
             MockedStatic<CredentialsProvider> credentialsProviderMock = mockStatic(CredentialsProvider.class);
             MockedStatic<CredentialsMatchers> credentialsMatchersMock = mockStatic(CredentialsMatchers.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class);
             MockedStatic<DomainCredentials> mockDomainCredentials = mockStatic(DomainCredentials.class);
             MockedStatic<SystemCredentialsProvider> mockSystemCredentialProvider = mockStatic(SystemCredentialsProvider.class)) {

            ConjurConfiguration mockConfig = mock(ConjurConfiguration.class);
            conjurApiMock.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(mockConfig);
            when(mockConfig.getCredentialID()).thenReturn("cred-id");

            jenkinsMock.when(Jenkins::get).thenReturn(mock(Jenkins.class));

            CredentialsMatcher mockCredentialsMatcher = mock(CredentialsMatcher.class);
            credentialsMatchersMock.when(() -> CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class)).thenReturn(mockCredentialsMatcher);

            SystemCredentialsProvider mockSystemProvider = mock(SystemCredentialsProvider.class);
            mockSystemCredentialProvider.when(SystemCredentialsProvider::getInstance).thenReturn(mockSystemProvider);

            Map<Domain, List<Credentials>> mockMapOfCredentials = new HashMap<>();
            when(mockSystemProvider.getDomainCredentialsMap()).thenReturn(mockMapOfCredentials);

            mockDomainCredentials.when(() -> DomainCredentials.getCredentials(mockMapOfCredentials, UsernamePasswordCredentials.class, Collections.emptyList(), mockCredentialsMatcher)).thenReturn(Collections.emptyList());
            credentialsMatchersMock.when(() -> CredentialsMatchers.firstOrNull(anyList(), any())).thenReturn(null);
            authenticator.fillAuthnInfo(authnInfo, mockContext);

            assertNull(authnInfo.getLogin());
            assertNull(authnInfo.getApiKey());
        }


    }

}