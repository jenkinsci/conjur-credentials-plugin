
package org.conjur.jenkins.authenticator;

import hudson.ExtensionList;
import hudson.model.ModelObject;
import jenkins.model.GlobalConfiguration;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurJWTAuthenticatorTest {

    @InjectMocks
    private ConjurJWTAuthenticator authenticator;

    @Mock
    private OkHttpClient mockHttpClient;

    @Mock
    private Call mockCall;

    @Mock
    private Response mockResponse;

    @Mock
    private ResponseBody mockResponseBody;

    @Mock
    private GlobalConjurConfiguration mockGlobalConjurConfiguration;

    @Mock
    private ModelObject mockContext;

    private ConjurAuthnInfo conjurAuthn;

    @BeforeEach
    void beforeEach() {
        conjurAuthn = new ConjurAuthnInfo();
        conjurAuthn.setApplianceUrl("https://conjur.example.com");
        conjurAuthn.setAccount("myaccount");
        conjurAuthn.setAuthnPath("test");
        conjurAuthn.setApiKey("dummy-jwt-token".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void testGetName() {
        assertEquals("JWT", authenticator.getName());
    }

    @Test
    void testGetAuthorizationTokenSuccessfulAuthentication() throws Exception {
        String responseMessage = "dummy-token";
        String expectedBase64Token = Base64.getEncoder().withoutPadding()
                .encodeToString(responseMessage.getBytes(StandardCharsets.UTF_8));

        try (MockedStatic<ConjurAPIUtils> utilsStatic = mockStatic(ConjurAPIUtils.class);
             MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            utilsStatic.when(() -> ConjurAPIUtils.getHttpClient(any()))
                    .thenReturn(mockHttpClient);

            when(mockHttpClient.newCall(any())).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.code()).thenReturn(200);
            when(mockResponse.body()).thenReturn(mockResponseBody);
            when(mockResponseBody.string()).thenReturn(responseMessage);

            ExtensionList<GlobalConfiguration> extensionList = mock(ExtensionList.class);
            when(extensionList.get(GlobalConjurConfiguration.class)).thenReturn(mockGlobalConjurConfiguration);
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(extensionList);

            byte[] result = authenticator.getAuthorizationToken(conjurAuthn, mockContext);

            assertNotNull(result);
            String decodedResult = new String(result, StandardCharsets.US_ASCII);
            assertEquals(expectedBase64Token, decodedResult);

            verify(mockHttpClient).newCall(any());
            verify(mockCall).execute();
        }
    }

    @Test
    void testGetAuthorizationTokenUnauthorizedResponse() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsStatic = mockStatic(ConjurAPIUtils.class)) {
            utilsStatic.when(() -> ConjurAPIUtils.getHttpClient(any()))
                    .thenReturn(mockHttpClient);

            when(mockHttpClient.newCall(any())).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.code()).thenReturn(401);
            when(mockResponse.message()).thenReturn("Unauthorized");

            AuthenticationConjurException exception = assertThrows(AuthenticationConjurException.class,
                    () -> authenticator.getAuthorizationToken(conjurAuthn, mockContext));
            assertEquals(401, exception.getErrorCode());

            verify(mockHttpClient).newCall(any());
            verify(mockCall).execute();
        }
    }

    @Test
    void testGetAuthorizationTokenOtherErrorResponse() throws Exception {
        try (MockedStatic<ConjurAPIUtils> utilsStatic = mockStatic(ConjurAPIUtils.class)) {
            utilsStatic.when(() -> ConjurAPIUtils.getHttpClient(any()))
                    .thenReturn(mockHttpClient);

            when(mockHttpClient.newCall(any())).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.code()).thenReturn(500);
            when(mockResponse.message()).thenReturn("Internal Server Error");

            IOException exception = assertThrows(IOException.class,
                    () -> authenticator.getAuthorizationToken(conjurAuthn, mockContext));
            assertTrue(exception.getMessage().contains("500"));

            verify(mockHttpClient).newCall(any());
            verify(mockCall).execute();
        }
    }

    @Test
    void testGetAuthorizationTokenNullRequest() throws Exception {
        conjurAuthn.setApiKey(null);

        byte[] result = authenticator.getAuthorizationToken(conjurAuthn, mockContext);

        assertNull(result);
    }

    @Test
    void testFillAuthnInfoSetsAuthnInfo() {
        GlobalConjurConfiguration conjurConfig = mock(GlobalConjurConfiguration.class);
        String jwtToken = "dummy-jwt-token";

        ExtensionList<GlobalConfiguration> extensionList = mock(ExtensionList.class);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class);
             MockedStatic<JwtToken> jwtTokenMockedStatic = mockStatic(JwtToken.class)) {

            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(extensionList);
            when(extensionList.get(GlobalConjurConfiguration.class)).thenReturn(conjurConfig);
            when(conjurConfig.getAuthWebServiceId()).thenReturn("test-web-service");

            jwtTokenMockedStatic.when(() -> JwtToken.getToken(mockContext, conjurConfig))
                    .thenReturn(jwtToken);

            authenticator.fillAuthnInfo(conjurAuthn, mockContext);

            assertEquals("test-web-service", conjurAuthn.getAuthnPath());
            assertNotNull(conjurAuthn.getApiKey());
            String apiKeyString = new String(conjurAuthn.getApiKey(), StandardCharsets.US_ASCII);
            assertTrue(apiKeyString.startsWith("jwt="));
        }
    }
}

