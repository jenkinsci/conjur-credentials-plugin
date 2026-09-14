package org.conjur.jenkins.authenticator;

import hudson.ExtensionList;
import hudson.model.ModelObject;
import jenkins.model.GlobalConfiguration;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConjurCertAuthenticatorTest {

    private ConjurCertAuthenticator authenticator;
    private ModelObject mockContext;
    private ConjurAuthnInfo authnInfo;

    @BeforeEach
    public void setup() {
        authenticator = new ConjurCertAuthenticator();
        mockContext = mock(ModelObject.class);
        authnInfo = new ConjurAuthnInfo();
        authnInfo.setApplianceUrl("https://conjur.example.com");
        authnInfo.setAuthnPath("my-cert-service");
        authnInfo.setAccount("myaccount");
        authnInfo.setLogin("host/my-jenkins-host");
        authnInfo.setConjurConfiguration(new ConjurConfiguration());
    }

    @Test
    public void testGetName() {
        assertEquals("Certificate", authenticator.getName());
    }

    @Test
    public void testGetAuthorizationTokenSuccess() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("cert-auth-token");
            when(mockResponse.code()).thenReturn(200);
            when(mockResponse.message()).thenReturn("OK");

            byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);

            assertNotNull(token);
            String decoded = new String(token, StandardCharsets.US_ASCII);
            String expectedEncoded = java.util.Base64.getEncoder().withoutPadding()
                    .encodeToString("cert-auth-token".getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedEncoded, decoded);
        }
    }

    @Test
    public void testGetAuthorizationTokenBuildsCorrectUrl() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
            when(mockClient.newCall(requestCaptor.capture())).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("token");
            when(mockResponse.code()).thenReturn(200);

            authenticator.getAuthorizationToken(authnInfo, mockContext);

            Request capturedRequest = requestCaptor.getValue();
            String url = capturedRequest.url().toString();
            assertEquals("https://conjur.example.com/authn-cert/my-cert-service/myaccount/host%2Fmy-jenkins-host/authenticate", url);
            assertEquals("POST", capturedRequest.method());
        }
    }

    @Test
    public void testGetAuthorizationTokenWithFullAuthnPath() throws Exception {
        authnInfo.setAuthnPath("authn-cert/custom-service");

        try (MockedStatic<ConjurAPIUtils> utilsMock = mockStatic(ConjurAPIUtils.class)) {
            OkHttpClient mockClient = mock(OkHttpClient.class);
            Call mockCall = mock(Call.class);
            Response mockResponse = mock(Response.class);
            ResponseBody mockBody = mock(ResponseBody.class);

            utilsMock.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
            when(mockClient.newCall(requestCaptor.capture())).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);

            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("token");
            when(mockResponse.code()).thenReturn(200);

            authenticator.getAuthorizationToken(authnInfo, mockContext);

            Request capturedRequest = requestCaptor.getValue();
            String url = capturedRequest.url().toString();
            assertTrue(url.startsWith("https://conjur.example.com/authn-cert/custom-service/"));
        }
    }

    @Test
    public void testGetAuthorizationToken401ThrowsAuthenticationConjurException() throws Exception {
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
    public void testGetAuthorizationToken500ThrowsIOException() throws Exception {
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
    public void testGetAuthorizationTokenReturnsNullWhenAuthnPathMissing() throws Exception {
        authnInfo.setAuthnPath(null);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull(token);
    }

    @Test
    public void testGetAuthorizationTokenReturnsNullWhenLoginMissing() throws Exception {
        authnInfo.setLogin(null);
        byte[] token = authenticator.getAuthorizationToken(authnInfo, mockContext);
        assertNull(token);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFillAuthnInfoSetsFieldsFromGlobalConfig() {
        try (MockedStatic<GlobalConfiguration> globalConfigMock = mockStatic(GlobalConfiguration.class)) {
            GlobalConjurConfiguration mockGlobalConfig = mock(GlobalConjurConfiguration.class);
            when(mockGlobalConfig.getCertAuthServiceId()).thenReturn("cert-service-1");
            when(mockGlobalConfig.getCertHostId()).thenReturn("host/my-cert-host");

            ExtensionList<GlobalConfiguration> mockExtList = mock(ExtensionList.class);
            globalConfigMock.when(GlobalConfiguration::all).thenReturn(mockExtList);
            when(mockExtList.get(GlobalConjurConfiguration.class)).thenReturn(mockGlobalConfig);

            authenticator.fillAuthnInfo(authnInfo, mockContext);

            assertEquals("cert-service-1", authnInfo.getAuthnPath());
            assertEquals("host/my-cert-host", authnInfo.getLogin());
            assertNull(authnInfo.getApiKey());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFillAuthnInfoNullGlobalConfig() {
        try (MockedStatic<GlobalConfiguration> globalConfigMock = mockStatic(GlobalConfiguration.class)) {
            ExtensionList<GlobalConfiguration> mockExtList = mock(ExtensionList.class);
            globalConfigMock.when(GlobalConfiguration::all).thenReturn(mockExtList);
            when(mockExtList.get(GlobalConjurConfiguration.class)).thenReturn(null);

            authnInfo.setAuthnPath("should-remain");
            authnInfo.setLogin("should-remain");

            authenticator.fillAuthnInfo(authnInfo, mockContext);

            assertEquals("should-remain", authnInfo.getAuthnPath());
            assertEquals("should-remain", authnInfo.getLogin());
            assertNull(authnInfo.getApiKey());
        }
    }

    @Test
    public void testFillAuthnInfoFolderConfigTakesPrecedenceOverGlobal() {
        ConjurConfiguration folderConfig = new ConjurConfiguration();
        folderConfig.setCertAuthServiceId("folder-service");
        folderConfig.setCertHostId("host/folder-host");
        authnInfo.setConjurConfiguration(folderConfig);

        authenticator.fillAuthnInfo(authnInfo, mockContext);

        assertEquals("folder-service", authnInfo.getAuthnPath());
        assertEquals("host/folder-host", authnInfo.getLogin());
        assertNull(authnInfo.getApiKey());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFillAuthnInfoFolderPartialFallsBackToGlobal() {
        ConjurConfiguration folderConfig = new ConjurConfiguration();
        folderConfig.setCertAuthServiceId("folder-service");
        // certHostId left blank — should fall back to global
        authnInfo.setConjurConfiguration(folderConfig);

        try (MockedStatic<GlobalConfiguration> globalConfigMock = mockStatic(GlobalConfiguration.class)) {
            GlobalConjurConfiguration mockGlobalConfig = mock(GlobalConjurConfiguration.class);
            when(mockGlobalConfig.getCertAuthServiceId()).thenReturn("global-service");
            when(mockGlobalConfig.getCertHostId()).thenReturn("host/global-host");

            ExtensionList<GlobalConfiguration> mockExtList = mock(ExtensionList.class);
            globalConfigMock.when(GlobalConfiguration::all).thenReturn(mockExtList);
            when(mockExtList.get(GlobalConjurConfiguration.class)).thenReturn(mockGlobalConfig);

            authenticator.fillAuthnInfo(authnInfo, mockContext);

            assertEquals("folder-service", authnInfo.getAuthnPath());
            assertEquals("host/global-host", authnInfo.getLogin());
            assertNull(authnInfo.getApiKey());
        }
    }
}
