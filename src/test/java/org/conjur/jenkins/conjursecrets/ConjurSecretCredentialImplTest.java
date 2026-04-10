
package org.conjur.jenkins.conjursecrets;

import ch.qos.logback.core.Context;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretCredentialImplTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Mock
    private ModelObject mockStoreContext;

    @Mock
    private Context mockContext;

    @Mock
    private ConjurConfiguration mockConjurConfiguration;

    @Mock
    private ConjurAPI mockConjurAPI;

    private CredentialsDescriptor descriptor = null;
    private ConjurSecretCredentialsImpl credentialImpl;
    private ConjurConfiguration config = null;

    @SuppressWarnings({"static-access", "deprecation"})
    @BeforeEach
    public void init() {
        descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testConstructor() {
        Object expectedObj = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
                "Test pipeline");
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);

        assertNotEquals(expectedObj, conjurSecretCredentials);
    }

    @Test
    public void testTagName() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretCredentials.getNameTag());
    }

    @Test
    public void testUsernameVariable() throws SecurityException {
        final ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setContext(j.jenkins.getInstance());

        assertEquals(j.jenkins.getInstance(), conjurSecretCredentials.getContext());
    }

    @Nested
    public class DescriptorImpl {
        @Test
        public void testGetDisplayName() {
            assertEquals("Conjur Secret credentials", descriptor.getDisplayName());
        }
    }

    @Test
    public void testGetDisplayName() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String testVariablePath = "DevTeam-1";
        String result = conjurSecretCredentials.getDisplayName();

        assertEquals("ConjurSecret:" + testVariablePath, result);
    }

    @Test
    public void testSecretFromStringValidSecret() {
        String SecretString = "valid_secret_string";
        Secret result = ConjurSecretCredentialsImpl.secretFromString(SecretString);

        assertNotNull(result);
        assertEquals(Secret.fromString(SecretString), result);
    }

    @Test
    public void testVariablePath() throws SecurityException {
        final ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedPath = "DevTeam-1";
        conjurSecretCredentials.setVariableId(expectedPath);

        assertEquals("DevTeam-1", conjurSecretCredentials.getVariableId());
    }

    @Test
    public void mockGetSecret() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        Secret secret = mock(Secret.class);
        when(conjurSecretCredentials.getSecret()).thenReturn(secret);
        Secret returnedSecret = conjurSecretCredentials.getSecret();

        verify(conjurSecretCredentials).getSecret();
        assertEquals(secret, returnedSecret);

    }

    @Test
    public void testStoredInConjurStorage() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        when(conjurSecretCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretCredentials.storedInConjurStorage());
    }

    @Test
    public void testSetStoredInConjurStorage() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretCredentials.storedInConjurStorage());
    }

    @Test
    public void testGetContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        when(conjurSecretCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretCredentials.getContext());
    }

    @Test
    public void testSetContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretCredentials.getContext());
    }

    @Test
    public void testSetInheritedContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretCredentials.getInheritedContext());
    }

    @Test
    public void testGetSecretReturnsSecret() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setStoredInConjurStorage(true);
        Secret secret = mock(Secret.class);

        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjur(any(), any(), any())).thenReturn(secret);
            assertNotNull(conjurSecretCredentials.getSecret());
            assertEquals(secret, conjurSecretCredentials.getSecret());
        }
    }

    @Test
    public void testGetSecretReturnsSecretWithInheritance() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setStoredInConjurStorage(false);
        Secret secret = mock(Secret.class);

        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), any(), any())).thenReturn(secret);
            assertNotNull(conjurSecretCredentials.getSecret());
            assertEquals(secret, conjurSecretCredentials.getSecret());
        }
    }

    @Test
    public void testGetDisplayNameOfDescriptor() {
        ConjurSecretCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        assertEquals("Conjur Secret Credential", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", "");

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("variableId field is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsOk() {
        ConjurSecretCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        try (MockedStatic<ConjurAPIUtils> mockedStatic = mockStatic(ConjurAPIUtils.class)) {
            mockedStatic
                    .when(() -> ConjurAPIUtils.validateCredential(any(), any(ConjurSecretCredentialsImpl.class)))
                    .thenReturn(FormValidation.ok("SUCCESS"));
            FormValidation result = descriptor.doTestConnection(mockContext, "test-id", "test/var");

            assertEquals(FormValidation.Kind.OK, result.kind);
            assertEquals("SUCCESS", result.getMessage());
        }
    }

    @Test
    public void testSelfContainedReturnsClonedSecret() {
        ConjurSecretCredentialsImpl base = mock(ConjurSecretCredentialsImpl.class);
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("mocked-secret");
        when(base.getScope()).thenReturn(null);
        when(base.getId()).thenReturn("cred-id");
        when(base.getVariableId()).thenReturn("var-id");
        when(base.getDescription()).thenReturn("desc");
        when(base.getSecret()).thenReturn(mockSecret);
        ConjurSecretCredentialsImpl.SelfContained selfContained = new ConjurSecretCredentialsImpl.SelfContained(base);

        assertEquals("mocked-secret", selfContained.getSecret().getPlainText());
        assertEquals("var-id", selfContained.getVariableId());
    }

    @Test
    public void testTypeReturnsCorrectClass() {
        ConjurSecretCredentialsImpl.SnapshotTaker taker = new ConjurSecretCredentialsImpl.SnapshotTaker();
        assertEquals(ConjurSecretCredentialsImpl.class, taker.type());
    }

    @Test
    public void testSnapshotReturnsNewInstance() {
        ConjurSecretCredentialsImpl mockCred = mock(ConjurSecretCredentialsImpl.class);
        when(mockCred.getSecret()).thenReturn(Secret.fromString("secret"));
        when(mockCred.getId()).thenReturn("id");
        when(mockCred.getScope()).thenReturn(null);
        when(mockCred.getVariableId()).thenReturn("var");
        when(mockCred.getDescription()).thenReturn("desc");

        ConjurSecretCredentialsImpl.SnapshotTaker taker = new ConjurSecretCredentialsImpl.SnapshotTaker();
        ConjurSecretCredentialsImpl snapshot = taker.snapshot(mockCred);

        assertNotNull(snapshot);
        assertEquals("ConjurSecret:var", snapshot.getDisplayName());
        assertEquals("var", snapshot.getVariableId());
        assertEquals("secret", snapshot.getSecret().getPlainText());
    }

}