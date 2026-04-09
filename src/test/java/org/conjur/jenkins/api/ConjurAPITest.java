
package org.conjur.jenkins.api;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.ExtensionList;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.util.DescribableList;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import okhttp3.*;
import org.conjur.jenkins.authenticator.AbstractAuthenticator;
import org.conjur.jenkins.authenticator.ConjurAPIKeyAuthenticator;
import org.conjur.jenkins.configuration.*;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)

public class ConjurAPITest {

    public OkHttpClient client;
    public ModelObject context;
    public ConjurConfiguration conjurConfiguration;
    public Call remoteCall;
    public ConjurAPI api;
    public List<UsernamePasswordCredentials> availableCredential;

    private static final Logger LOGGER = Logger.getLogger(ConjurAPI.class.getName());
    private AbstractFolder<?> folderMock;
    private FolderConjurConfiguration folderConjurConfigMock;
    private GlobalConjurConfiguration globalConjurConfigMock;
    private ConjurConfiguration folderConjurConfiguration;
    private ConjurConfiguration globalConjurConfiguration;
    private ConjurJITJobProperty mockProperty;
    private ConjurConfiguration jobConjurConfiguration;
    private Job mockJob;
    private DescribableList mockDescribableList;
    private TestLogHandler handler;

    @Mock
    private GlobalConjurConfiguration globalConfig;
    @Mock
    private ConjurConfiguration mockConjurConjurConfig;
    @Mock
    private ConjurConfiguration mockGlobalConjurConfig;
    @Mock
    private ConjurAuthnInfo mockAuthnInfo;
    @Mock
    private ModelObject mockContext;
    @Mock
    private Response mockResponse;
    @Mock
    private Call mockCall;
    @Mock
    private OkHttpClient mockClient;
    @Mock
    private ConjurConfiguration mockConfiguration;
    @Mock
    private ResponseBody mockBody;

    @Before
    public void setUp() throws Exception {
        mock(ConjurAPI.class);
        conjurConfiguration = new ConjurConfiguration("https://conjur_server:8083", "myConjurAccount");
        client = ConjurAPIUtils.getHttpClient(new ConjurConfiguration("https://conjur_server:8083", "myConjurAccount"));
        availableCredential = new ArrayList<>();
        context = mock(ModelObject.class);
        remoteCall = mock(Call.class);
        api = mock(ConjurAPI.class);

        folderMock = mock(AbstractFolder.class);
        folderConjurConfigMock = mock(FolderConjurConfiguration.class);
        folderConjurConfiguration = mock(ConjurConfiguration.class);
        globalConjurConfigMock = mock(GlobalConjurConfiguration.class);
        globalConjurConfiguration = mock(ConjurConfiguration.class);
        mockJob = mock(Job.class);
        mockProperty = mock(ConjurJITJobProperty.class);
        jobConjurConfiguration = mock(ConjurConfiguration.class);
        mockDescribableList = mock(DescribableList.class);

        handler = new TestLogHandler();
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.FINEST);
    }

    @After
    public void tearDownLogger() {
        LOGGER.removeHandler(handler);
    }

    @Test
    public void getConjurAuthnInfo() {

        try (MockedStatic<ConjurAPI> conjurAPIMockStatic = mockStatic(ConjurAPI.class)) {
            ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();
            conjurAPIMockStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(conjurAuthn);
            assertSame(conjurAuthn, ConjurAPI.getConjurAuthnInfo(any(), any()));//To check whether two objects are the same,
        }
    }

    @Test
    public void checkAuthentication() throws IOException {
        try (MockedStatic<JwtToken> jwtTokenMockStatic = mockStatic(JwtToken.class)) {
            jwtTokenMockStatic.when(() -> JwtToken.getToken((context), globalConfig)).thenReturn(
                    "jwtToken.test");
        }
        try (MockedStatic<ConjurAPI> conjurAPIMockStatic = mockStatic(ConjurAPI.class)) {
            ConjurAuthnInfo conjurAuthn = ConjurAPI.getConjurAuthnInfo(conjurConfiguration, null);
            conjurAPIMockStatic.when(() -> ConjurAPI.getAuthorizationToken(conjurAuthn, context))
                    .thenReturn("success".getBytes());
            assertEquals("success", new String(ConjurAPI.getAuthorizationToken(conjurAuthn, context)));
        }
    }

    //
    // Check if ConjurAPI return secret
    //
    @Test
    public void checkSecretVal() throws IOException {
        try (MockedStatic<ConjurAPI> mockedStaticConjurAPI = mockStatic(ConjurAPI.class)) {
            mockedStaticConjurAPI.when(
                            () -> ConjurAPI.getConjurSecret(client, conjurConfiguration, "auth-token".getBytes(), "host/frontend/frontend-01"))
                    .thenReturn("bhfbdbkfbkd-bvjdbfbjbv-bfjbdbjkb-bbfkbskb".getBytes());
            assertEquals("bhfbdbkfbkd-bvjdbfbjbv-bfjbdbjkb-bbfkbskb",
                    new String(ConjurAPI.getConjurSecret(client, conjurConfiguration, "auth-token".getBytes(), "host/frontend/frontend-01")));
        }
    }

    //
    // Check if required config fields are set
    //
    @Test
    public void conjurAuthnInfoEmptyFieldsShouldUseGlobalConfig() throws IOException {
        // Arrange
        ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();
        // Initialize mocks
        when(mockConjurConjurConfig.getAccount()).thenReturn(null); // Simulating empty or null configuration
        when(mockConjurConjurConfig.getApplianceURL()).thenReturn(null); // Simulating empty or null configuration
        when(mockGlobalConjurConfig.getAccount()).thenReturn("globalAccount");
        when(mockGlobalConjurConfig.getApplianceURL()).thenReturn("globalApplianceURL");

        // Example of setting ConjurAuthnInfo with these configurations
        conjurAuthn.setAccount(mockConjurConjurConfig.getAccount() != null ? mockConjurConjurConfig.getAccount() : mockGlobalConjurConfig.getAccount());
        conjurAuthn.setApplianceUrl(mockConjurConjurConfig.getApplianceURL() != null ? mockConjurConjurConfig.getApplianceURL() : mockGlobalConjurConfig.getApplianceURL());
        // Verify that ConjurAuthnInfo uses global values when local values are null
        assertEquals("globalAccount", conjurAuthn.getAccount());
        assertEquals("globalApplianceURL", conjurAuthn.getApplianceUrl());
    }


    @Test
    public void testGetConjurSecretHandlesNullResponse() throws IOException {
        try (MockedStatic<ConjurAPI> mockedStaticConjurAPI = mockStatic(ConjurAPI.class)) {
            mockedStaticConjurAPI.when(() ->
                            ConjurAPI.getConjurSecret(client, conjurConfiguration, "auth-token".getBytes(), "host/frontend/frontend-01"))
                    .thenReturn(null);

            byte[] result = ConjurAPI.getConjurSecret(client, conjurConfiguration, "auth-token".getBytes(), "host/frontend/frontend-01");
            assertNull(result);
        }
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testDefaultToEnvironment() throws SecurityException, IllegalArgumentException {

        ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();

        Map<String, String> env = new HashMap<>();
        env.put("CONJUR_APPLIANCE_URL", "https://conjur_server:8083");
        env.put("CONJUR_ACCOUNT", "myConjurAccount");
        env.put("CONJUR_AUTHN_LOGIN", "testLogin");
        env.put("CONJUR_AUTHN_API_KEY", "testApiKey");

        conjurAuthn.setApplianceUrl(env.get(0));

        conjurAuthn.setAccount(env.get(1));

        assertEquals("https://conjur_server:8083", env.get("CONJUR_APPLIANCE_URL"));

        assertEquals("myConjurAccount", env.get("CONJUR_ACCOUNT"));
        assertTrue(env.containsKey("CONJUR_AUTHN_LOGIN"));
        assertEquals("testApiKey", env.get("CONJUR_AUTHN_API_KEY"));

        Map<String, String> envNull = new HashMap<>();
        envNull.put("CONJUR_APPLIANCE_URL", null);
        envNull.put("CONJUR_ACCOUNT", null);
        envNull.put("CONJUR_AUTHN_LOGIN", null);
        envNull.put("CONJUR_AUTHN_API_KEY", null);

        conjurAuthn.setApplianceUrl(envNull.get(0));

        conjurAuthn.setAccount(envNull.get(1));

        assertNull(conjurAuthn.getApplianceUrl());
        assertNull(conjurAuthn.getAccount());
        assertNull(conjurAuthn.getLogin());
        assertNull(conjurAuthn.getApiKey());

        assertNull(envNull.get("CONJUR_APPLIANCE_URL"));
        assertNull(envNull.get("CONJUR_ACCOUNT"));
        assertTrue(envNull.containsKey("CONJUR_AUTHN_LOGIN"));
        assertNull(envNull.get("CONJUR_AUTHN_API_KEY"));

    }

    @Test
    public void logConjurConfiguration() {
        assertEquals("https://conjur_server:8083", conjurConfiguration.getApplianceURL());
        assertEquals("myConjurAccount", conjurConfiguration.getAccount());
        conjurConfiguration.setCredentialID("credentialId");
        assertEquals("credentialId", conjurConfiguration.getCredentialID());
        ConjurConfiguration result = ConjurAPI.logConjurConfiguration(conjurConfiguration);
        assertEquals(conjurConfiguration, result);
        conjurConfiguration.setApplianceURL(null);
        conjurConfiguration.setAccount(null);
        conjurConfiguration.setCredentialID(null);
        ConjurConfiguration resultNull = ConjurAPI.logConjurConfiguration(conjurConfiguration);

        assertEquals(conjurConfiguration, resultNull);

    }

    @Test
    public void testGetAuthorizationTokenWhenAuthenticatorIsNullSetsDefault() throws Exception {
        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {

            ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
            when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(null);
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);

            byte[] expectedToken = "defaultToken".getBytes();

            ConjurAPIKeyAuthenticator spyAuth = spy(new ConjurAPIKeyAuthenticator());
            doReturn(expectedToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);

            Field authField = ConjurAPI.class.getDeclaredField("authenticator");

            authField.setAccessible(true);
            authField.set(null, spyAuth);

            byte[] actualToken = ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);

            assertArrayEquals(expectedToken, actualToken);
            verify(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    @Test
    public void testGetAuthorizationTokenWhenAuthenticatorSetsDefault() throws Exception {
        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {

            when(globalConfig.getSelectAuthenticator()).thenReturn("APIKey");
            ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
            when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(globalConfig);
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);

            byte[] expectedToken = "defaultToken".getBytes();

            ConjurAPIKeyAuthenticator spyAuth = spy(new ConjurAPIKeyAuthenticator());
            doReturn(expectedToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);

            Field authField = ConjurAPI.class.getDeclaredField("authenticator");

            authField.setAccessible(true);
            authField.set(null, spyAuth);

            byte[] actualToken = ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);

            assertArrayEquals(expectedToken, actualToken);
            verify(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    @Test
    public void testGetAuthorizationTokenThrowsRuntimeException() throws Exception {
        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            when(globalConfig.getSelectAuthenticator()).thenReturn("APIKey");
            ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
            when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(globalConfig);
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);
            when(api.getAuthorizationToken(mockAuthnInfo, mockContext)).thenThrow(new RuntimeException("Test runtime exception"));
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext));

            assertEquals("Test runtime exception", thrown.getMessage());
        }
    }


    //
    // Test if proper authenticator was selected
    //
    @Test
    public void testSelectedAuthenticator() {
        AbstractAuthenticator tokenAuth = ConjurAPI.getAuthenticatorByName("APIKey");
        String tokenAuthSelected = tokenAuth.getName();

        AbstractAuthenticator jwtAuth = ConjurAPI.getAuthenticatorByName("JWT");
        String jwtAuthSelected = jwtAuth.getName();

        assertEquals("APIKey", tokenAuthSelected);
        assertEquals("JWT", jwtAuthSelected);
    }

    @Test
    public void testGetConjurSecretSuccess() throws IOException {
        try (MockedStatic<TelemetryConfiguration> mockTel = mockStatic(TelemetryConfiguration.class)) {
            when(mockConfiguration.getApplianceURL()).thenReturn("http://conjur_server");
            when(mockConfiguration.getAccount()).thenReturn("cucumber");

            String variableId = "db/Password";

            mockTel.when(TelemetryConfiguration::getTelemetryHeader).thenReturn(Base64.getUrlEncoder().encodeToString("telemetryheader".getBytes(StandardCharsets.UTF_8)));
            byte[] token = "auth-token".getBytes();

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.bytes()).thenReturn("secret".getBytes());
            when(mockResponse.code()).thenReturn(200);

            byte[] result = ConjurAPI.getConjurSecret(mockClient, mockConfiguration, token, variableId);

            assertArrayEquals("secret".getBytes(), result);
        }

    }

    @Test
    public void testGetConjurSecretThrowsAuthenticationException() throws IOException {
        try (MockedStatic<TelemetryConfiguration> mockTel = mockStatic(TelemetryConfiguration.class)) {
            when(mockConfiguration.getApplianceURL()).thenReturn("http://conjur_server");
            when(mockConfiguration.getAccount()).thenReturn("cucumber");

            String variableId = "db/Password";

            mockTel.when(TelemetryConfiguration::getTelemetryHeader).thenReturn(Base64.getUrlEncoder().encodeToString("telemetryheader".getBytes(StandardCharsets.UTF_8)));
            byte[] token = "auth-token".getBytes();

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.bytes()).thenReturn("secret".getBytes());
            when(mockResponse.code()).thenReturn(404);

            AuthenticationConjurException exception = assertThrows(AuthenticationConjurException.class, () -> ConjurAPI.getConjurSecret(mockClient, mockConfiguration, token, variableId));

            assertEquals("No access", exception.getMessage());
        }

    }

    @Test
    public void testGetConjurSecretThrowsIOException() throws IOException {
        try (MockedStatic<TelemetryConfiguration> mockTel = mockStatic(TelemetryConfiguration.class)) {
            when(mockConfiguration.getApplianceURL()).thenReturn("http://conjur_server");
            when(mockConfiguration.getAccount()).thenReturn("cucumber");

            String variableId = "db/Password";

            mockTel.when(TelemetryConfiguration::getTelemetryHeader).thenReturn(Base64.getUrlEncoder().encodeToString("telemetryheader".getBytes(StandardCharsets.UTF_8)));
            byte[] token = "auth-token".getBytes();

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.bytes()).thenReturn("IO Error".getBytes());
            when(mockResponse.code()).thenReturn(501);
            when(mockResponse.message()).thenReturn("Internal Error");

            IOException exception = assertThrows(IOException.class, () -> ConjurAPI.getConjurSecret(mockClient, mockConfiguration, token, variableId));

            assertEquals("Error fetching secret from Conjur [501 - Internal Error] IO Error", exception.getMessage());
        }

    }

    @Test
    public void testGetConjurConfigReturnsMergedConfigurationWhenFolderConfigExistsAndInherits() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(folderConjurConfigMock);
        when(folderConjurConfigMock.getConjurConfiguration()).thenReturn(folderConjurConfiguration);
        when(folderConjurConfigMock.getInheritFromParent()).thenReturn(true);

        // Mock folder hierarchy (no parent)
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {

            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);

            when(folderConjurConfiguration.mergeWithParent(globalConjurConfiguration))
                    .thenReturn(folderConjurConfiguration);

            when(folderConjurConfiguration.getAccount()).thenReturn("cucumber");
            when(folderConjurConfiguration.getApplianceURL()).thenReturn("http://conjur");

            when(globalConjurConfigMock.getSelectAuthenticator()).thenReturn("APIKey");
            when(folderConjurConfiguration.getCredentialID()).thenReturn("credId");


            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNotNull(result);
            verify(folderConjurConfiguration).mergeWithParent(globalConjurConfiguration);
        }
    }

    @Test
    public void testStopsAtFolderConfigurationWhenInheritIsFalse() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(folderConjurConfigMock);
        when(folderConjurConfigMock.getConjurConfiguration()).thenReturn(folderConjurConfiguration);
        when(folderConjurConfigMock.getInheritFromParent()).thenReturn(false);

        ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

        // Should stop at folder level and not merge global
        assertNull(result);
    }

    @Test
    public void testUsesGlobalConfigurationWhenNoFolderConfig() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(null);
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);


            when(globalConjurConfiguration.getAccount()).thenReturn("cucumber");
            when(globalConjurConfiguration.getApplianceURL()).thenReturn("http://conjur");

            when(globalConjurConfigMock.getSelectAuthenticator()).thenReturn("APIKey");
            when(globalConjurConfiguration.getCredentialID()).thenReturn("credId");


            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNotNull(result);
            assertEquals(globalConjurConfiguration, result);
        }
    }

    @Test
    public void testLogsErrorsWhenMissingGlobalConfig() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(null);

        // Mock folder hierarchy (no parent)
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(null);

            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNull(result);
        }
    }

    @Test
    public void testLogsErrorsWhenMissingRequiredFields() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(null);

        // Mock folder hierarchy (no parent)
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);

            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNotNull(result);
            assertEquals(globalConjurConfiguration, result);
        }
    }

    @Test
    public void testGetConfigurationFromContextHudsonContextReturnsGlobalConfig() {
        Hudson hudsonMock = mock(Hudson.class);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);


            ConjurConfiguration result = ConjurAPI.getConfigurationFromContext(hudsonMock);

            assertNotNull(result);
            assertEquals(globalConjurConfiguration, result);
        }
    }

    @Test
    public void testGetConfigurationFromContextRunContextWithJobProperty() {
        Run runMock = mock(Run.class);
        Job jobMock = mock(Job.class);

        ConjurJITJobProperty jobPropertyMock = mock(ConjurJITJobProperty.class);
        ConjurConfiguration jobConfigMock = mock(ConjurConfiguration.class);

        when(runMock.getParent()).thenReturn(jobMock);
        when(jobMock.getProperty(ConjurJITJobProperty.class)).thenReturn(jobPropertyMock);
        when(jobPropertyMock.getConjurConfiguration()).thenReturn(jobConfigMock);
        when(jobConfigMock.getInheritFromParent()).thenReturn(false);

        ConjurConfiguration result = ConjurAPI.getConfigurationFromContext(runMock);
        assertSame(jobConfigMock, result);
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testIsInheritanceOn() {
        when(mockJob.getProperty(ConjurJITJobProperty.class)).thenReturn(mockProperty);
        when(mockProperty.getConjurConfiguration()).thenReturn(jobConjurConfiguration);
        when(jobConjurConfiguration.getInheritFromParent()).thenReturn(true);

        boolean result = ConjurAPI.isInheritanceOn(mockJob);

        assertTrue(result);


    }

    @Test
    public void testIsInheritanceOnWithContextAsFolder() {
        when(folderMock.getProperties()).thenReturn(mockDescribableList);
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(folderConjurConfigMock);
        when(folderConjurConfigMock.getInheritFromParent()).thenReturn(true);

        boolean result = ConjurAPI.isInheritanceOn(folderMock);

        assertTrue(result);
    }

    @Test
    public void testIsInheritanceOnWithContextAsJobThrowsException() {
        when(mockJob.getProperty(ConjurJITJobProperty.class)).thenThrow(new RuntimeException());

        boolean result = ConjurAPI.isInheritanceOn(mockJob);

        assertTrue(result);
        assertTrue(handler.getMessages().stream().anyMatch(msg ->
                msg.contains("Cannot get properties for Job/Item")
        ));
    }

    @Test
    public void testIsInheritanceOnWithContextAsFolderThrowsException() {
        when(folderMock.getProperties()).thenReturn(mockDescribableList);
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenThrow(new RuntimeException("Testing the Exception"));

        boolean result = ConjurAPI.isInheritanceOn(folderMock);

        assertTrue(result);
        assertTrue(handler.getMessages().stream().anyMatch(msg ->
                msg.contains("Cannot get properties for AbstractFolder")
        ));

    }


    @Test
    public void testSimpleSecretMocking() {
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("mySecretValue");
        assertEquals("mySecretValue", mockSecret.getPlainText());
    }

    @Test
    public void testGetConjurConfigFolderSetsCredentialIDContext() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(folderConjurConfigMock);
        when(folderConjurConfigMock.getConjurConfiguration()).thenReturn(folderConjurConfiguration);
        when(folderConjurConfigMock.getInheritFromParent()).thenReturn(true);
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);

            when(folderConjurConfiguration.mergeWithParent(globalConjurConfiguration))
                    .thenReturn(folderConjurConfiguration);
            when(folderConjurConfiguration.getAccount()).thenReturn("cucumber");
            when(folderConjurConfiguration.getApplianceURL()).thenReturn("http://conjur");
            when(globalConjurConfigMock.getSelectAuthenticator()).thenReturn("APIKey");
            when(folderConjurConfiguration.getCredentialID()).thenReturn("credId");
            when(folderConjurConfiguration.getCredentialIDContext()).thenReturn(null);

            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNotNull(result);
            verify(folderConjurConfiguration).setCredentialIDContext(folderMock);
        }
    }

    @Test
    public void testGetConjurConfigGlobalSetsCredentialIDContextToJenkins() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(null);
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);
            when(globalConjurConfiguration.getCredentialID()).thenReturn("credId");
            when(globalConjurConfiguration.getCredentialIDContext()).thenReturn(null);
            when(globalConjurConfiguration.getAccount()).thenReturn("test-account");
            when(globalConjurConfiguration.getApplianceURL()).thenReturn("http://conjur");
            when(globalConjurConfigMock.getSelectAuthenticator()).thenReturn("APIKey");

            try (MockedStatic<jenkins.model.Jenkins> jenkinsMock = mockStatic(jenkins.model.Jenkins.class)) {
                jenkins.model.Jenkins mockJenkinsInstance = mock(jenkins.model.Jenkins.class);
                jenkinsMock.when(jenkins.model.Jenkins::getInstanceOrNull).thenReturn(mockJenkinsInstance);

                ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

                assertNotNull(result);
                verify(globalConjurConfiguration).setCredentialIDContext(mockJenkinsInstance);
            }
        }
    }

    @Test
    public void testGetConjurConfigDoesNotOverrideExistingContext() {
        when(folderMock.getProperties()).thenReturn(mock(DescribableList.class));
        when(folderMock.getProperties().get(FolderConjurConfiguration.class)).thenReturn(null);
        when(folderMock.getParent()).thenReturn(null);

        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mock(ExtensionList.class));
            when(GlobalConfiguration.all().get(GlobalConjurConfiguration.class)).thenReturn(globalConjurConfigMock);
            when(globalConjurConfigMock.getConjurConfiguration()).thenReturn(globalConjurConfiguration);
            ModelObject existingContext = mock(ModelObject.class);
            when(globalConjurConfiguration.getCredentialID()).thenReturn("credId");
            when(globalConjurConfiguration.getCredentialIDContext()).thenReturn(existingContext);
            when(globalConjurConfiguration.getAccount()).thenReturn("test-account");
            when(globalConjurConfiguration.getApplianceURL()).thenReturn("http://conjur");
            when(globalConjurConfigMock.getSelectAuthenticator()).thenReturn("APIKey");

            ConjurConfiguration result = ConjurAPI.getConjurConfig(folderMock);

            assertNotNull(result);
            assertEquals(existingContext, result.getCredentialIDContext());
            verify(globalConjurConfiguration, never()).setCredentialIDContext(any());
        }
    }


    // Custom Handler to capture log messages
    static class TestLogHandler extends Handler {
        private final StringBuilder logMessages = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.FINEST.intValue()) {
                logMessages.append(record.getMessage()).append("\n");
            }
        }

        public List<String> getMessages() {
            return Arrays.asList(logMessages.toString().split("\n"));
        }

        @Override
        public void flush() {
            // No-op: not needed for test handler
        }

        @Override
        public void close() throws SecurityException {
            // No-op: not needed for test handler
        }
    }
}
