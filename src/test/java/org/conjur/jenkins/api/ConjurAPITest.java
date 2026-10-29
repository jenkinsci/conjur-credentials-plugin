
package org.conjur.jenkins.api;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.google.inject.internal.asm.$ClassTooLargeException;
import hudson.ExtensionList;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.util.DescribableList;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import okhttp3.*;
import org.conjur.jenkins.authenticator.AbstractAuthenticator;
import org.conjur.jenkins.authenticator.ConjurAPIKeyAuthenticator;
import org.conjur.jenkins.authenticator.ConjurJWTAuthenticator;
import org.conjur.jenkins.configuration.*;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.conjur.jenkins.CjplCode;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.Stapler;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * Incremented once per test in {@code setUp} so that {@code mockAuthnInfo.getApplianceUrl()}
     * returns a unique value each time, giving every cache-relevant test a unique cache key
     * without requiring the static token cache to be cleared between tests.
     */
    private static final AtomicInteger TEST_COUNTER = new AtomicInteger();

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

        // Give mockAuthnInfo a test-unique appliance URL so each test gets a different cache key.
        // getApplianceUrl() is part of the cache key; a unique URL means no cross-test cache hits.
        when(mockAuthnInfo.getApplianceUrl())
                .thenReturn("https://conjur-test-" + TEST_COUNTER.incrementAndGet() + ":8443");
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
            // getAuthorizationToken zeroes whatever the authenticator handed it, so the
            // authenticator must return its own array rather than the assertion's.
            byte[] authenticatorToken = "defaultToken".getBytes();

            ConjurAPIKeyAuthenticator spyAuth = spy(new ConjurAPIKeyAuthenticator());
            doReturn(authenticatorToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);

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
            byte[] authenticatorToken = "defaultToken".getBytes();

            ConjurAPIKeyAuthenticator spyAuth = spy(new ConjurAPIKeyAuthenticator());
            doReturn(authenticatorToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);

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

    /** Mirrors what Conjur returns and what the authenticators then Base64-encode. */
    private static byte[] buildConjurToken(long expEpochSeconds) {
        String claims = "{\"sub\":\"host/frontend\",\"iat\":1693000000,\"exp\":" + expEpochSeconds + "}";
        String jws = "{\"protected\":\"aGVhZGVy\",\"payload\":\""
                + Base64.getUrlEncoder().encodeToString(claims.getBytes(StandardCharsets.UTF_8))
                + "\",\"signature\":\"c2ln\"}";
        return Base64.getEncoder().withoutPadding()
                .encodeToString(jws.getBytes(StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static void installAuthenticator(AbstractAuthenticator auth) throws Exception {
        Field authField = ConjurAPI.class.getDeclaredField("authenticator");
        authField.setAccessible(true);
        authField.set(null, auth);
    }

    /** Stubs global config to select JWT auth (cache-eligible path). */
    private MockedStatic<GlobalConfiguration> mockJWTGlobalConfig() {
        MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class);
        when(globalConfig.getSelectAuthenticator()).thenReturn("JWT");
        ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
        when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(globalConfig);
        globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);
        return globalConfigStatic;
    }

    /** Stubs global config to select API Key auth (cache-bypass path). */
    private MockedStatic<GlobalConfiguration> mockApiKeyGlobalConfig() {
        MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class);
        when(globalConfig.getSelectAuthenticator()).thenReturn("APIKey");
        ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
        when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(globalConfig);
        globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);
        return globalConfigStatic;
    }

    private static ConjurAuthnInfo authnInfoFor(String login) {
        ConjurAuthnInfo authn = new ConjurAuthnInfo();
        authn.setApplianceUrl("https://conjur:8443");
        authn.setAccount("myAccount");
        authn.setLogin(login);
        return authn;
    }

    //
    // A fan-out of builds sharing one Conjur identity must authenticate only once.
    //
    @Test
    public void testSecondCallForSameIdentityIsServedFromCache() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig()) {
            byte[] conjurToken = buildConjurToken(Instant.now().getEpochSecond() + 480);
            byte[] expected = conjurToken.clone();

            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(conjurToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);
            installAuthenticator(spyAuth);

            byte[] first = ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);
            byte[] second = ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);

            assertArrayEquals(expected, first);
            assertArrayEquals(expected, second);
            verify(spyAuth, times(1)).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    //
    // A misconfigured authenticator returns null; callers saw null before the cache existed too.
    //
    @Test
    public void testNullTokenIsReturnedAsNullAndNotCached() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig()) {
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(null).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);
            installAuthenticator(spyAuth);

            assertNull(ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext));
            // Call again — if null was wrongly cached, spy would not be called a second time
            ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);
            verify(spyAuth, times(2)).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    //
    // API Key auth must bypass the cache entirely — it was not the affected path.
    //
    @Test
    public void testAPIKeyAuthBypassesCache() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockApiKeyGlobalConfig()) {
            byte[] conjurToken = buildConjurToken(Instant.now().getEpochSecond() + 480);
            ConjurAPIKeyAuthenticator spyAuth = spy(new ConjurAPIKeyAuthenticator());
            doReturn(conjurToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);
            installAuthenticator(spyAuth);

            ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);
            ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);

            // If the cache were populated, the second call would be a hit and the spy called only once.
            verify(spyAuth, times(2)).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    //
    // A rejected token must be replaced, not just retried, or every other build replays the dead one.
    //
    @Test
    public void testReauthenticateAfterUnauthorizedReplacesTheStaleEntry() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig();
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class)) {

            ConjurAuthnInfo authn = authnInfoFor("host/frontend");
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            // First call: initial load; second call (re-auth after 401): fresh replacement token
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480),
                     buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(authn, mockContext);
            installAuthenticator(spyAuth);

            // Populate the cache with the initial token (spy called once)
            ConjurAPI.getAuthorizationToken(authn, mockContext);

            // Stub the sub-calls that getSecretFromConjur needs so it doesn't hit Jenkins.get()
            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(null);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(authn);
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            // First getConjurSecret call → 401; second (after re-auth) → succeeds
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(any(), any(), any(), any()))
                    .thenThrow(new IOException("[401 - Unauthorized]"))
                    .thenReturn("secret".getBytes(StandardCharsets.US_ASCII));

            // 401 triggers reauthenticateAfterUnauthorized (runs real code):
            //   evicts the stale entry, loads a fresh token (spy called twice total)
            ConjurAPI.getSecretFromConjur(mockContext, mockContext, "db/password");

            // Fresh token is now in the cache — next getAuthorizationToken must be a cache hit
            // (spy must NOT be called a third time)
            ConjurAPI.getAuthorizationToken(authn, mockContext);
            verify(spyAuth, times(2)).getAuthorizationToken(authn, mockContext);
        }
    }

    //
    // Security property: two Jenkins contexts that resolve to different Conjur identities must
    // not share a cache entry. A collision would let one host use another host's token.
    //
    @Test
    public void testBuildCacheKeyDifferentiatesDistinctJenkinsIdentities() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig()) {
            when(globalConfig.getEnableIdentityFormatFieldsFromToken()).thenReturn(true);
            when(globalConfig.getSelectIdentityFormatToken()).thenReturn("jenkins_full_name");

            AbstractFolder<?> folderA = mock(AbstractFolder.class);
            when(folderA.getFullName()).thenReturn("team-a/deploy");
            AbstractFolder<?> folderB = mock(AbstractFolder.class);
            when(folderB.getFullName()).thenReturn("team-b/deploy");

            ConjurAuthnInfo authn = authnInfoFor("host/connector");
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(authn, (ModelObject) folderA);
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(authn, (ModelObject) folderB);
            installAuthenticator(spyAuth);

            ConjurAPI.getAuthorizationToken(authn, folderA);
            ConjurAPI.getAuthorizationToken(authn, folderB);

            // If both contexts mapped to the same cache key the second call would be a cache hit
            // and the authenticator would not be called for folderB — cross-identity token sharing.
            verify(spyAuth, times(1)).getAuthorizationToken(authn, (ModelObject) folderA);
            verify(spyAuth, times(1)).getAuthorizationToken(authn, (ModelObject) folderB);
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

        AbstractAuthenticator certAuth = ConjurAPI.getAuthenticatorByName("Certificate");
        String certAuthSelected = certAuth.getName();

        assertEquals("APIKey", tokenAuthSelected);
        assertEquals("JWT", jwtAuthSelected);
        assertEquals("Certificate", certAuthSelected);
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

            assertEquals(CjplCode.CONJUR_SECRET_FETCH_ERROR.format(501, "Internal Error", "IO Error"), exception.getMessage());
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

            try (MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class)) {
                Jenkins mockJenkinsInstance = mock(Jenkins.class);
                jenkinsMock.when(Jenkins::getInstanceOrNull).thenReturn(mockJenkinsInstance);

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

    @Test
    public void testGetSecretFromConjurConvertsSecretBytesToUtf8String() {
        var variableId = "test/password";
        var expected = "secretValue";
        var secretBytes = expected.getBytes(StandardCharsets.UTF_8);
        var authToken = "auth-token".getBytes();

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtilsStatic = mockStatic(ConjurAPIUtils.class)) {

            // Stub only the external dependencies — everything else runs real (CALLS_REAL_METHODS).
            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(context))
                    .thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(mockConfiguration, context))
                    .thenReturn(mockAuthnInfo);
            conjurApiStatic.when(() -> ConjurAPI.getAuthorizationToken(any(ConjurAuthnInfo.class), eq(context)))
                    .thenReturn(authToken);
            apiUtilsStatic.when(() -> ConjurAPIUtils.getHttpClient(mockConfiguration))
                    .thenReturn(mockClient);
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(eq(mockClient), eq(mockConfiguration), any(byte[].class), eq(variableId)))
                    .thenReturn(secretBytes);

            var actual = ConjurAPI.getSecretFromConjur(context, context, variableId);

            // Assert
            assertNotNull(actual);
            assertEquals(expected, actual.getPlainText());
        }
    }

    @Test
    public void testGetSecretFromConjurWithNullContextReturnsNull() {
        var result = ConjurAPI.getSecretFromConjur(null, null, "db/password");

        assertNull(result);
    }

    @Test
    public void testGetSecretFromConjurWithNullInheritedContextUsesMainContext() {
        var variableId = "test/password";
        var expected = "secretValue";
        var secretBytes = expected.getBytes(StandardCharsets.UTF_8);
        var authToken = "auth-token".getBytes();

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtilsStatic = mockStatic(ConjurAPIUtils.class)) {

            // Stub only the external dependencies — everything else runs real (CALLS_REAL_METHODS).
            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(context))
                    .thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(mockConfiguration, context))
                    .thenReturn(mockAuthnInfo);
            conjurApiStatic.when(() -> ConjurAPI.getAuthorizationToken(any(ConjurAuthnInfo.class), eq(context)))
                    .thenReturn(authToken);
            apiUtilsStatic.when(() -> ConjurAPIUtils.getHttpClient(mockConfiguration))
                    .thenReturn(mockClient);
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(eq(mockClient), eq(mockConfiguration), any(byte[].class), eq(variableId)))
                    .thenReturn(secretBytes);

            // Passing NULL as inheritedObjectContext to test the == null branch
            var actual = ConjurAPI.getSecretFromConjur(context, null, variableId);

            assertNotNull(actual);
            assertEquals(expected, actual.getPlainText());
            conjurApiStatic.verify(() -> ConjurAPI.getConjurAuthnInfo(mockConfiguration, context), atLeastOnce());
        }
    }

    @Test
    public void testGetSecretFromConjurThrowsInvalidConjurSecretExceptionOnIOException()  {
        var variableId = "test/password";

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class)) {

            conjurApiStatic.when(() ->
                            ConjurAPI.getSecretFromConjur(context, null, variableId))
                    .thenCallRealMethod();

            conjurApiStatic.when(() ->
                            ConjurAPI.getConfigurationFromContext(context))
                    .thenReturn(mockConfiguration);

            conjurApiStatic.when(() ->
                            ConjurAPI.getConjurAuthnInfo(mockConfiguration, context))
                    .thenReturn(mock(ConjurAuthnInfo.class));

            conjurApiStatic.when(() ->
                            ConjurAPI.getAuthorizationToken(any(ConjurAuthnInfo.class), eq(context)))
                    .thenThrow(new IOException());

            assertThrows(InvalidConjurSecretException.class,
                    () -> ConjurAPI.getSecretFromConjur(context, null, variableId));
        }
    }

    @Test
    public void testGetSecretFromConjurThrowsInvalidConjurSecretExceptionOnGeneralException() {
        var variableId = "test/password";

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class)) {

            conjurApiStatic.when(() ->
                            ConjurAPI.getSecretFromConjur(context, null, variableId))
                    .thenCallRealMethod();

            conjurApiStatic.when(() ->
                            ConjurAPI.getConfigurationFromContext(context))
                    .thenReturn(mockConfiguration);

            conjurApiStatic.when(() ->
                            ConjurAPI.getConjurAuthnInfo(mockConfiguration, context))
                    .thenThrow(new RuntimeException());

            assertThrows(InvalidConjurSecretException.class,
                    () -> ConjurAPI.getSecretFromConjur(context, null, variableId));
        }
    }

    // -------------------------------------------------------------------------
    // New tests: globalConfig null bypass, authToken zeroing
    // -------------------------------------------------------------------------

    /**
     * Concern 3: when GlobalConjurConfiguration is absent, buildCacheKey returns null and the
     * cache must be bypassed entirely. The authenticator IS still called, but no entry is stored —
     * preventing all identities from collapsing to the same empty-string key.
     */
    @Test
    public void testGetAuthorizationTokenBypassesCacheWhenGlobalConfigIsNull() throws Exception {
        try (MockedStatic<GlobalConfiguration> globalConfigStatic = mockStatic(GlobalConfiguration.class)) {
            // GlobalConfiguration is absent on every call. setAuthenticator() leaves the
            // already-installed JWT spy in place (globalConfig is null, so the authenticator
            // is not replaced). isCacheable() returns true, but buildCacheKey() returns null
            // because globalConfig is null — triggering the cache-bypass path on every call.
            ExtensionList<GlobalConfiguration> mockExtension = mock(ExtensionList.class);
            when(mockExtension.get(GlobalConjurConfiguration.class)).thenReturn(null);
            globalConfigStatic.when(GlobalConfiguration::all).thenReturn(mockExtension);

            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            installAuthenticator(spyAuth);

            byte[] conjurToken = buildConjurToken(Instant.now().getEpochSecond() + 480);
            doReturn(conjurToken).when(spyAuth).getAuthorizationToken(mockAuthnInfo, mockContext);

            byte[] result = ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);
            ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockContext);

            assertArrayEquals("token must still be returned to the caller", conjurToken, result);
            // Cache is bypassed on every call — the spy fires twice, not once.
            verify(spyAuth, times(2)).getAuthorizationToken(mockAuthnInfo, mockContext);
        }
    }

    /**
     * Concern 5: authToken bytes must be zeroed even when the retry getConjurSecret call throws.
     * This guards against token data lingering in heap after a failed retry.
     *
     * <p>Uses {@code CALLS_REAL_METHODS} as the default answer for the ConjurAPI static mock so
     * that the private helpers {@code isUnauthorized} and {@code reauthenticateAfterUnauthorized}
     * execute their real logic. {@code getAuthorizationToken} is explicitly stubbed to return a
     * known initial token; the spy captures the re-auth token that {@code reauthenticateAfterUnauthorized}
     * fetches via {@code tokenCache.getOrLoad}.
     */
    @Test
    public void testGetAuthorizationTokenZerosAuthTokenWhenRetryGetConjurSecretThrows()
            throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig();
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class)) {

            List<byte[]> capturedTokens = new ArrayList<>();

            // Initial token: created explicitly and injected via stub so getAuthorizationToken
            // does not execute its real cache path (which would require the JWT signing key).
            byte[] initialToken = buildConjurToken(Instant.now().getEpochSecond() + 480);
            capturedTokens.add(initialToken);
            conjurApiStatic.when(() -> ConjurAPI.getAuthorizationToken(any(), any())).thenReturn(initialToken);

            // Re-auth token: reauthenticateAfterUnauthorized runs real (CALLS_REAL_METHODS) and
            // calls tokenCache.getOrLoad, which invokes the authenticator — captured here.
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doAnswer(inv -> {
                byte[] t = buildConjurToken(Instant.now().getEpochSecond() + 480);
                capturedTokens.add(t);
                return t;
            }).when(spyAuth).getAuthorizationToken(any(), any());
            installAuthenticator(spyAuth);

            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(null);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(mockAuthnInfo);
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            // First call → [401] triggers re-auth; second call (retry) → [503] exercises the finally block.
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(any(), any(), any(), any()))
                    .thenThrow(new IOException("[401 - Unauthorized]"))
                    .thenThrow(new IOException("[503 - Service Unavailable]"));

            try {
                ConjurAPI.getSecretFromConjur(mockContext, mockContext, "db/password");
            } catch (Exception ignored2) {
                // Expected: getSecretFromConjur wraps the 503 IOException in InvalidConjurSecretException.
            }

            // capturedTokens[0] = initialToken (zeroed at the [401] handler before re-auth).
            // capturedTokens[1] = re-auth token (zeroed in the finally block when retry throws).
            assertEquals("must capture both the initial and the re-auth token", 2, capturedTokens.size());
            for (byte[] token : capturedTokens) {
                assertArrayEquals(
                        "Concern 5: authToken must be zeroed in finally even when retry throws",
                        new byte[token.length], token);
            }
        }
    }

    // -------------------------------------------------------------------------
    // JWT signing deferred to cache misses
    // -------------------------------------------------------------------------

    /**
     * getConjurAuthnInfo must not populate apiKey when JWT auth is active.
     * JWT signing must happen inside the getOrLoad loader, not before the cache is checked.
     */
    @Test
    public void testGetConjurAuthnInfoDoesNotSignJwt() throws Exception {
        try (MockedStatic<GlobalConfiguration> ignored = mockJWTGlobalConfig();
             MockedStatic<JwtToken> jwtTokenMock = mockStatic(JwtToken.class)) {

            ConjurConfiguration config = new ConjurConfiguration("https://conjur:8443", "myaccount");
            ConjurAuthnInfo authnInfo = ConjurAPI.getConjurAuthnInfo(config, mockContext);

            assertNull("apiKey must be null — JWT signing must not happen before cache lookup",
                    authnInfo.getApiKey());
            jwtTokenMock.verify(() -> JwtToken.getToken(any(), any()), never());
        }
    }

    /**
     * JWT signing must occur exactly once per cache miss. A second call for the same identity
     * must be served from cache without triggering another RSA sign.
     */
    @Test
    public void testJwtIsOnlySignedOnCacheMissNotOnCacheHit() throws Exception {
        // Null out the static authenticator so any spy left by a previous test is discarded.
        installAuthenticator(null);

        try (MockedStatic<GlobalConfiguration> gc = mockJWTGlobalConfig();
             MockedStatic<JwtToken> jwtTokenMock = mockStatic(JwtToken.class);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class)) {

            jwtTokenMock.when(() -> JwtToken.getToken(any(), any())).thenReturn("signed.jwt.token");
            jwtTokenMock.when(() -> JwtToken.computeSubClaim(any(), any())).thenCallRealMethod();

            when(globalConfig.getAuthWebServiceId()).thenReturn("authn-jwt/prod");
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);
            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.code()).thenReturn(200);
            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("fake-conjur-token");

            ConjurAuthnInfo authn = authnInfoFor("host/jwt-signing-test");
            authn.setApplianceUrl("https://conjur-signing-test:8443");
            authn.setAuthnPath("authn-jwt/prod"); // mirrors what fillBaseAuthnInfo sets in production

            ConjurAPI.getAuthorizationToken(authn, mockContext);  // cache miss — signs once
            ConjurAPI.getAuthorizationToken(authn, mockContext);  // cache hit — must not re-sign

            jwtTokenMock.verify(() -> JwtToken.getToken(any(), any()), times(1));
        }
    }

    /**
     * getCredentialsForContext must return a non-null collection when the
     * Conjur resource endpoint returns an empty variable list.
     */
    @Test
    public void testGetCredentialsForContext_returnsEmptyCollectionNotNull() throws Exception {
        try (MockedStatic<GlobalConfiguration> gc = mockJWTGlobalConfig();
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class)) {

            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(mockAuthnInfo);
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);

            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(any(), any());
            installAuthenticator(spyAuth);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenReturn(mockResponse);
            when(mockResponse.code()).thenReturn(200);
            when(mockResponse.body()).thenReturn(mockBody);
            when(mockBody.string()).thenReturn("[]");

            Collection<?> result = ConjurAPI.getCredentialsForContext(Object.class, mockContext);

            assertNotNull("getCredentialsForContext must return a collection, not null", result);
        }
    }

    // -------------------------------------------------------------------------
    // getSecretFromConjurWithInheritance retry and escalation behaviour
    // -------------------------------------------------------------------------

    /**
     * When the cached token is stale, fetchSecretWithRetry evicts and re-authenticates at the
     * same level. The inheritance loop must not walk up to the parent context.
     * Modelled after testReauthenticateAfterUnauthorizedReplacesTheStaleEntry.
     */
    @Test
    public void testGetSecretFromConjurWithInheritance_retriesLocally_onStaleCachedToken() throws Exception {
        String variableId = "test/password";
        String secretValue = "s3cr3t";

        try (MockedStatic<GlobalConfiguration> gc = mockJWTGlobalConfig();
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class)) {

            ConjurAuthnInfo authn = authnInfoFor("host/retry-test");
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480),
                     buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(any(), any());
            installAuthenticator(spyAuth);

            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(authn);
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(any())).thenReturn(false);
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);

            byte[] secretBytes = secretValue.getBytes(StandardCharsets.UTF_8);
            IOException unauthorized = new IOException(
                    CjplCode.CONJUR_SECRET_FETCH_ERROR.format(401, "Unauthorized", variableId));
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(any(), any(), any(), eq(variableId)))
                    .thenThrow(unauthorized)
                    .thenReturn(secretBytes);

            ConjurSecretCredentials mockCredentials = mock(ConjurSecretCredentials.class);
            Secret result = ConjurAPI.getSecretFromConjurWithInheritance(mockContext, mockCredentials, variableId);

            assertNotNull("retry must recover and return the secret", result);
            assertEquals(secretValue, result.getPlainText());
        }
    }

    /**
     * When both the initial fetch and the retry return 401, the 401 is genuine access-denied.
     * The fix must re-wrap it as AuthenticationConjurException so the inheritance loop escalates,
     * ultimately producing InvalidConjurSecretException when there is no parent context.
     */
    @Test
    public void testGetSecretFromConjurWithInheritance_escalatesToParent_onGenuineAccessDenied() throws Exception {
        String variableId = "test/password";

        try (MockedStatic<GlobalConfiguration> gc = mockJWTGlobalConfig();
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class, CALLS_REAL_METHODS);
             MockedStatic<ConjurAPIUtils> apiUtils = mockStatic(ConjurAPIUtils.class);
             MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class)) {

            // Jenkins.get() is called when inheritance is off and context is not yet Jenkins.
            // Return a mock Jenkins instance — the loop-exit guard checks instanceof Jenkins,
            // so the second iteration terminates with InvalidConjurSecretException.
            jenkinsMock.when(Jenkins::get).thenReturn(mock(Jenkins.class));

            ConjurAuthnInfo authn = authnInfoFor("host/escalation-test");
            ConjurJWTAuthenticator spyAuth = spy(new ConjurJWTAuthenticator());
            doReturn(buildConjurToken(Instant.now().getEpochSecond() + 480),
                     buildConjurToken(Instant.now().getEpochSecond() + 480))
                    .when(spyAuth).getAuthorizationToken(any(), any());
            installAuthenticator(spyAuth);

            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(any())).thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any())).thenReturn(authn);
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(any())).thenReturn(false);
            apiUtils.when(() -> ConjurAPIUtils.getHttpClient(any())).thenReturn(mockClient);

            // Both calls throw 401 — genuine access-denied, no recovery
            IOException unauthorized = new IOException(
                    CjplCode.CONJUR_SECRET_FETCH_ERROR.format(401, "Unauthorized", variableId));
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(any(), any(), any(), eq(variableId)))
                    .thenThrow(unauthorized);

            assertThrows(InvalidConjurSecretException.class,
                    () -> ConjurAPI.getSecretFromConjurWithInheritance(mockContext, null, variableId));
        }
    }

    // issue #48: on a build executor thread Stapler.getCurrentRequest() is null, so the
    // Jenkins.get() fallback must not be nested inside that check or the context stays null.
    @Test
    public void testGetSecretFromConjurWithInheritanceResolvesJenkinsContextOffHttpThread() {
        var variableId = "test/password";
        var expected = "secretValue";
        var secretBytes = expected.getBytes(StandardCharsets.UTF_8);
        var authToken = "auth-token".getBytes();

        ConjurSecretCredentials mockCredentials = mock(ConjurSecretCredentials.class);
        Jenkins mockJenkinsInstance = mock(Jenkins.class);

        try (MockedStatic<Stapler> staplerStatic = mockStatic(Stapler.class);
             MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class);
             MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class,
                     withSettings().strictness(Strictness.LENIENT));
             MockedStatic<ConjurAPIUtils> apiUtilsStatic = mockStatic(ConjurAPIUtils.class)) {

            // simulate a build executor thread: no active HTTP request
            staplerStatic.when(Stapler::getCurrentRequest).thenReturn(null);
            jenkinsStatic.when(Jenkins::get).thenReturn(mockJenkinsInstance);
            when(mockJenkinsInstance.getDisplayName()).thenReturn("Jenkins");

            // inheritance is on by default; with a null context the failed first attempt
            // would then log context.getDisplayName() and throw an NPE
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(any())).thenReturn(true);

            conjurApiStatic.when(() ->
                            ConjurAPI.getSecretFromConjurWithInheritance(null, mockCredentials, variableId))
                    .thenCallRealMethod();

            conjurApiStatic.when(() -> ConjurAPI.getConfigurationFromContext(mockJenkinsInstance))
                    .thenReturn(mockConfiguration);
            conjurApiStatic.when(() -> ConjurAPI.getConjurAuthnInfo(mockConfiguration, mockJenkinsInstance))
                    .thenReturn(mockAuthnInfo);
            conjurApiStatic.when(() -> ConjurAPI.getAuthorizationToken(mockAuthnInfo, mockJenkinsInstance))
                    .thenReturn(authToken);
            apiUtilsStatic.when(() -> ConjurAPIUtils.getHttpClient(mockConfiguration))
                    .thenReturn(mockClient);
            conjurApiStatic.when(() -> ConjurAPI.getConjurSecret(mockClient, mockConfiguration, authToken, variableId))
                    .thenReturn(secretBytes);

            var actual = ConjurAPI.getSecretFromConjurWithInheritance(null, mockCredentials, variableId);

            assertNotNull(actual);
            assertEquals(expected, actual.getPlainText());
            // the fix: context resolves to Jenkins.get() even with no Stapler request, not null
            verify(mockCredentials).setContext(mockJenkinsInstance);
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
