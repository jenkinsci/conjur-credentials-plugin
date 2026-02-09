
package org.conjur.jenkins.conjursecrets;

import ch.qos.logback.core.Context;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurSecretCredentialImplTest {

    private JenkinsRule j;

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
    private final ConjurConfiguration config = null;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
    }

    @Test
    void testConstructor() {
        Object expectedObj = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
                "Test pipeline");
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);

        assertNotEquals(expectedObj, conjurSecretCredentials);
    }

    @Test
    void testTagName() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretCredentials.getNameTag());
    }

    @Test
    void testUsernameVariable() {
        final ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setContext(Jenkins.get());

        assertEquals(Jenkins.get(), conjurSecretCredentials.getContext());
    }

    @Nested
    class DescriptorImpl {

        @Test
        void testGetDisplayName() {
            assertEquals("Conjur Secret Credential", descriptor.getDisplayName());
        }
    }

    @Test
    void testGetDisplayName() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String testVariablePath = "DevTeam-1";
        String result = conjurSecretCredentials.getDisplayName();

        assertEquals("ConjurSecret:" + testVariablePath, result);
    }

    @Test
    void testSecretFromStringValidSecret() {
        String SecretString = "valid_secret_string";
        Secret result = ConjurSecretCredentialsImpl.secretFromString(SecretString);

        assertNotNull(result);
        assertEquals(Secret.fromString(SecretString), result);
    }

    @Test
    void testVariablePath() {
        final ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedPath = "DevTeam-1";
        conjurSecretCredentials.setVariableId(expectedPath);

        assertEquals("DevTeam-1", conjurSecretCredentials.getVariableId());
    }

    @Test
    void mockGetSecret() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        Secret secret = mock(Secret.class);
        when(conjurSecretCredentials.getSecret()).thenReturn(secret);
        Secret returnedSecret = conjurSecretCredentials.getSecret();

        verify(conjurSecretCredentials).getSecret();
        assertEquals(secret, returnedSecret);

    }

    @Test
    void testStoredInConjurStorage() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        when(conjurSecretCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretCredentials.storedInConjurStorage());
    }

    @Test
    void testSetStoredInConjurStorage() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretCredentials.storedInConjurStorage());
    }

    @Test
    void testGetContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
        when(conjurSecretCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretCredentials.getContext());
    }

    @Test
    void testSetContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretCredentials.getContext());
    }

    @Test
    void testSetInheritedContext() {
        ConjurSecretCredentialsImpl conjurSecretCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretCredentials.getInheritedContext());
    }

    @Test
    void testGetSecretReturnsSecret() {
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
    void testGetSecretReturnsSecretWithInheritance() {
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
    void testGetDisplayNameOfDescriptor() {
        ConjurSecretCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        assertEquals("Conjur Secret Credential", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", "");

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("variableId field is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsOk() {
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
    void testSelfContainedReturnsClonedSecret() {
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
    void testTypeReturnsCorrectClass() {
        ConjurSecretCredentialsImpl.SnapshotTaker taker = new ConjurSecretCredentialsImpl.SnapshotTaker();
        assertEquals(ConjurSecretCredentialsImpl.class, taker.type());
    }

    @Test
    void testSnapshotReturnsNewInstance() {
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