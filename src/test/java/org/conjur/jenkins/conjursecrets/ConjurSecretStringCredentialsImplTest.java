package org.conjur.jenkins.conjursecrets;

import ch.qos.logback.core.Context;
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
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
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
class ConjurSecretStringCredentialsImplTest {

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

    private ModelObject context;


    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        context = Jenkins.get();
    }

    @Test
    void testConstructor() {
        Object expectedObj = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
                "Test pipeline");
        ConjurSecretStringCredentialsImpl conjurSecretCredentials = mock(ConjurSecretStringCredentialsImpl.class);

        assertNotEquals(expectedObj, conjurSecretCredentials);
    }

    @Test
    void testTagName() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretStringCredentials.getNameTag());
    }

    @Test
    void testUsernameVariable() {
        final ConjurSecretStringCredentialsImpl conjurSecretCredentials = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setContext(context);

        assertEquals(context, conjurSecretCredentials.getContext());
    }

    @Test
    void testSecretFromStringValidSecret() {
        String SecretString = "valid_secret_string";
        Secret result = ConjurSecretStringCredentialsImpl.secretFromString(SecretString);

        assertNotNull(result);
        assertEquals(Secret.fromString(SecretString), result);
    }

    @Test
    void testVariablePath() {
        final ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        String expectedPath = "DevTeam-1";
        conjurSecretStringCredentials.setVariableId(expectedPath);

        assertEquals("DevTeam-1", conjurSecretStringCredentials.getVariableId());
    }

    @Test
    void mockGetSecret() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = mock(ConjurSecretStringCredentialsImpl.class);
        Secret secret = mock(Secret.class);
        when(conjurSecretStringCredentials.getSecret()).thenReturn(secret);
        Secret returnedSecret = conjurSecretStringCredentials.getSecret();

        verify(conjurSecretStringCredentials).getSecret();
        assertEquals(secret, returnedSecret);
    }

    @Test
    void testGetDisplayName() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL,
                "testPipeline", "DevTeam-1", "Test pipeline");
        String testVariablePath = "DevTeam-1";
        String result = conjurSecretStringCredentials.getDisplayName();

        assertEquals("ConjurSecretString:" + testVariablePath, result);
    }

    @Test
    void testStoredInConjurStorage() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = mock(ConjurSecretStringCredentialsImpl.class);
        when(conjurSecretStringCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretStringCredentials.storedInConjurStorage());
    }

    @Test
    void testSetStoredInConjurStorage() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretStringCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretStringCredentials.storedInConjurStorage());
    }

    @Test
    void testGetContext() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = mock(ConjurSecretStringCredentialsImpl.class);
        when(conjurSecretStringCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretStringCredentials.getContext());
    }

    @Test
    void testSetContext() {
        ConjurSecretStringCredentialsImpl conjurSecretStringCredentials = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretStringCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretStringCredentials.getContext());
    }

    @Test
    void testSetInheritedContext() {
        ConjurSecretStringCredentialsImpl conjurSecretCredentials = new ConjurSecretStringCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline");
        conjurSecretCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretCredentials.getInheritedContext());
    }

    @Test
    void testGetSecretReturnsSecret() {
        ConjurSecretStringCredentialsImpl conjurSecretCredentials = new ConjurSecretStringCredentialsImpl(
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
        ConjurSecretStringCredentialsImpl conjurSecretCredentials = new ConjurSecretStringCredentialsImpl(
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
        ConjurSecretStringCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretStringCredentialsImpl.DescriptorImpl();
        assertEquals("Conjur Secret String Credential", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretStringCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretStringCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", "");

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("variableId field is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsOk() {
        ConjurSecretStringCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretStringCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        try (MockedStatic<ConjurAPIUtils> mockedStatic = mockStatic(ConjurAPIUtils.class)) {
            mockedStatic
                    .when(() -> ConjurAPIUtils.validateCredential(any(), any(ConjurSecretStringCredentialsImpl.class)))
                    .thenReturn(FormValidation.ok("SUCCESS"));
            FormValidation result = descriptor.doTestConnection(mockContext, "test-id", "test/var");

            assertEquals(FormValidation.Kind.OK, result.kind);
            assertEquals("SUCCESS", result.getMessage());
        }
    }

    @Test
    void testSelfContainedReturnsClonedSecret() {
        ConjurSecretStringCredentialsImpl base = mock(ConjurSecretStringCredentialsImpl.class);
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("mocked-secret");

        when(base.getScope()).thenReturn(null);
        when(base.getId()).thenReturn("cred-id");
        when(base.getVariableId()).thenReturn("var-id");
        when(base.getDescription()).thenReturn("desc");
        when(base.getSecret()).thenReturn(mockSecret);
        ConjurSecretStringCredentialsImpl.SelfContained selfContained = new ConjurSecretStringCredentialsImpl.SelfContained(base);

        assertEquals("mocked-secret", selfContained.getSecret().getPlainText());
        assertEquals("var-id", selfContained.getVariableId());
    }

    @Test
    void testTypeReturnsCorrectClass() {
        ConjurSecretStringCredentialsImpl.SnapshotTaker taker = new ConjurSecretStringCredentialsImpl.SnapshotTaker();
        assertEquals(ConjurSecretStringCredentialsImpl.class, taker.type());
    }

    @Test
    void testSnapshotReturnsNewInstance() {
        ConjurSecretStringCredentialsImpl mockCred = mock(ConjurSecretStringCredentialsImpl.class);
        when(mockCred.getSecret()).thenReturn(Secret.fromString("secret"));
        when(mockCred.getId()).thenReturn("id");
        when(mockCred.getScope()).thenReturn(null);
        when(mockCred.getVariableId()).thenReturn("var");
        when(mockCred.getDescription()).thenReturn("desc");
        ConjurSecretStringCredentialsImpl.SnapshotTaker taker = new ConjurSecretStringCredentialsImpl.SnapshotTaker();
        ConjurSecretStringCredentialsImpl snapshot = taker.snapshot(mockCred);

        assertNotNull(snapshot);
        assertEquals("ConjurSecretString:var", snapshot.getDisplayName());
        assertEquals("var", snapshot.getVariableId());
        assertEquals("secret", snapshot.getSecret().getPlainText());
    }

}
