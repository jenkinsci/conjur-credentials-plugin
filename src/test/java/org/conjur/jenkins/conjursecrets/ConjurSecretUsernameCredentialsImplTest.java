package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("serial")
public class ConjurSecretUsernameCredentialsImplTest extends StandardListBoxModel {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Mock
    private ConjurConfiguration conjurConfiguration;

    @Mock
    private Jenkins jenkins;

    @Mock
    private Item item;

    @Mock
    private ModelObject context;

    @Mock
    private ModelObject storeContext;

    @Mock
    private ModelObject mockStoreContext;

    private ConjurConfiguration config = new ConjurConfiguration();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        doNothing().when(jenkins).checkPermission(Jenkins.ADMINISTER);
    }

    @Test
    public void testGetDisplayName() {
        ConjurSecretUsernameCredentialsImpl.DescriptorImpl descriptorImpl = new ConjurSecretUsernameCredentialsImpl.DescriptorImpl();
        String name = ConjurSecretUsernameCredentialsImpl.getDescriptorDisplayName();

        assertEquals(name, descriptorImpl.getDisplayName());
    }

    @Test
    public void testGetDescriptorDisplayName() {
        assertEquals("Conjur Secret Username Credential",
                ConjurSecretUsernameCredentialsImpl.getDescriptorDisplayName());
    }

    @Test
    public void testGetNameTag() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUserNameCredentials = new ConjurSecretUsernameCredentialsImpl(CredentialsScope.GLOBAL, "var-id", "username", "var/id", "description");

        assertTrue(conjurSecretUserNameCredentials.getNameTag().isEmpty());
    }

    @Test
    public void testGetSecret() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUserNameCredentials = spy(new ConjurSecretUsernameCredentialsImpl(CredentialsScope.GLOBAL, "var-id", "username", "var/id", "description"));
        doReturn(mock(Secret.class)).when(conjurSecretUserNameCredentials).getPassword();

        assertNotNull(conjurSecretUserNameCredentials.getSecret());
    }

    @Test
    public void testStoredInConjurStorage() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = mock(ConjurSecretUsernameCredentialsImpl.class);
        when(conjurSecretUsernameCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretUsernameCredentials.storedInConjurStorage());
    }

    @Test
    public void testSetStoredInConjurStorage() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretUsernameCredentials.storedInConjurStorage());
    }

    @Test
    public void testGetContext() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = mock(ConjurSecretUsernameCredentialsImpl.class);
        when(conjurSecretUsernameCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretUsernameCredentials.getContext());
    }

    @Test
    public void testSetContext() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameCredentials.getContext());
    }

    @Test
    public void testSetInheritedContext() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameCredentials.getInheritedContext());
    }

    @Test
    public void testDisplayName() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");

        assertEquals("ConjurSecretUsername:Test pipeline", conjurSecretUsernameCredentials.getDisplayName());
    }

    @Test
    public void testGetUsername() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");

        assertEquals("DevTeam-1", conjurSecretUsernameCredentials.getUsername());
    }

    @Test
    public void testSetUsername() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setUserName("conjur-username");

        assertEquals("conjur-username", conjurSecretUsernameCredentials.getUsername());
    }

    @Test
    public void testGetVariableId() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setVariableId("var-id");

        assertEquals("var-id", conjurSecretUsernameCredentials.getVariableId());
    }

    @Test
    public void testGetPasswordReturnsSecret() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");

        conjurSecretUsernameCredentials.setStoredInConjurStorage(true);
        Secret secret = mock(Secret.class);
        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjur(any(), any(), any())).thenReturn(secret);

            assertNotNull(conjurSecretUsernameCredentials.getPassword());
            assertEquals(secret, conjurSecretUsernameCredentials.getPassword());
        }
    }

    @Test
    public void testGetSecretReturnsSecretWithInheritance() {
        ConjurSecretUsernameCredentialsImpl conjurSecretUsernameCredentials = new ConjurSecretUsernameCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", "description");
        conjurSecretUsernameCredentials.setStoredInConjurStorage(false);
        Secret secret = mock(Secret.class);
        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), any(), any())).thenReturn(secret);

            assertNotNull(conjurSecretUsernameCredentials.getPassword());
            assertEquals(secret, conjurSecretUsernameCredentials.getPassword());
        }
    }

    @Test
    public void testGetDisplayNameOfDescriptor() {
        ConjurSecretStringCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretStringCredentialsImpl.DescriptorImpl();
        assertEquals("Conjur Secret String Credential", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretUsernameCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "var-id", null);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("FAILED username,credentialID fields is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsOk() {
        ConjurSecretUsernameCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        try (MockedStatic<ConjurAPIUtils> mockedStatic = mockStatic(ConjurAPIUtils.class)) {
            mockedStatic
                    .when(() -> ConjurAPIUtils.validateCredential(any(), any(ConjurSecretUsernameCredentialsImpl.class)))
                    .thenReturn(FormValidation.ok("SUCCESS"));
            FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", "test/var");

            assertEquals(FormValidation.Kind.OK, result.kind);
            assertEquals("SUCCESS", result.getMessage());
        }
    }

}