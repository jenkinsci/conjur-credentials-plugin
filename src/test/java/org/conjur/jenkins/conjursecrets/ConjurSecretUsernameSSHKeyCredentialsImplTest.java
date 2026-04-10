package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretUsernameSSHKeyCredentialsImplTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Mock
    private ConjurSecretUsernameSSHKeyCredentialsImpl secretcredentials;
    @Mock
    private ModelObject mockStoreContext;
    @Mock
    private ConjurAPI conjurAPI;
    @Mock
    private ConjurSecretCredentials conjurSecretCredentials;
    @Mock
    private Secret mockSecret;

    private ModelObject context;
    private ModelObject storeContext;
    private String id = "test-id";
    private String username = "test-username";
    private String credentialID = "test-credential-id";
    private String description = "test-description";

    @SuppressWarnings("deprecation")
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getDisplayName() {
        assertEquals("Conjur Secret Username SSHKey Credential",
                ConjurSecretUsernameSSHKeyCredentialsImpl.getDescriptorDisplayName());
    }

    @Test
    public void testDoFillCredentialIDItems() {
        ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl();
        Item item = mock(Item.class);
        ListBoxModel listBoxModel = descriptor.doFillCredentialIDItems(item, "uri");

        assertEquals(0, listBoxModel.size());
    }

    @Test
    public void testStoredInConjurStorage() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = mock(ConjurSecretUsernameSSHKeyCredentialsImpl.class);
        when(conjurSecretUsernameSSHCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretUsernameSSHCredentials.storedInConjurStorage());
    }

    @Test
    public void testSetStoredInConjurStorage() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretUsernameSSHCredentials.storedInConjurStorage());
    }


    @Test
    public void testGetNameTag() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");

        assertTrue(conjurSecretUsernameSSHCredentials.getNameTag().isEmpty());
    }

    @Test
    public void testGetSecret() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");

        assertNull(conjurSecretUsernameSSHCredentials.getSecret());
    }

    @Test
    public void testGetContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = mock(ConjurSecretUsernameSSHKeyCredentialsImpl.class);
        when(conjurSecretUsernameSSHCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretUsernameSSHCredentials.getContext());
    }

    @Test
    public void testSetContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameSSHCredentials.getContext());
    }

    @Test
    public void testSetInheritedContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameSSHCredentials.getInheritedContext());
    }

    @Test
    public void testDisplayName() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        String result = conjurSecretUsernameSSHCredentials.getDisplayName();

        assertEquals("ConjurSecretUsernameSSHKey:DevTeam-1", result);
    }

    @Test
    public void testGetPassphrase() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        Secret secretMock = mock(Secret.class);
        conjurSecretUsernameSSHCredentials.setPassphrase(secretMock);
        Secret result = conjurSecretUsernameSSHCredentials.getPassphrase();

        assertNotNull(result);
    }

    @Test
    public void testGetCredentialID() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setCredentialID("mock-cred");
        String result = conjurSecretUsernameSSHCredentials.getCredentialID();

        assertNotNull(result);
    }

    @Test
    public void testGetPrivateKey() {
        Jenkins jenkinsMock = mock(Jenkins.class);
        String credentialId = "test-cred-id";
        ConjurSecretCredentials mockCredentials = mock(ConjurSecretCredentials.class);
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("private-key");
        when(mockCredentials.getId()).thenReturn(credentialId);
        when(mockCredentials.getSecret()).thenReturn(mockSecret);
        try (MockedStatic<Jenkins> staticMockJenkins = mockStatic(Jenkins.class);
             MockedStatic<CredentialsProvider> mockProvider = mockStatic(CredentialsProvider.class)) {
            staticMockJenkins.when(Jenkins::get).thenReturn(jenkinsMock);
            mockProvider.when(() -> CredentialsProvider.lookupCredentials(eq(ConjurSecretCredentials.class), eq(jenkinsMock), any(), anyList())).thenReturn(Collections.singletonList(mockCredentials));
            ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                    CredentialsScope.GLOBAL, "testPipeline", "Test pipeline", credentialId, mock(Secret.class), "description");
            String privateKey = conjurSecretUsernameSSHCredentials.getPrivateKey();

            assertEquals("private-key", privateKey);
        }
    }

    @Test
    public void testGetPrivateKeyWhenCredentialNotFound() {
        Jenkins jenkinsMock = mock(Jenkins.class);
        String credentialId = "test-cred-id";
        try (MockedStatic<Jenkins> staticMockJenkins = mockStatic(Jenkins.class);
             MockedStatic<CredentialsProvider> mockProvider = mockStatic(CredentialsProvider.class)) {
            staticMockJenkins.when(Jenkins::get).thenReturn(jenkinsMock);
            mockProvider.when(() -> CredentialsProvider.lookupCredentials(eq(ConjurSecretCredentials.class), eq(jenkinsMock), any(), anyList())).thenReturn(Collections.emptyList());
            ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                    CredentialsScope.GLOBAL, "testPipeline", "Test pipeline", credentialId, mock(Secret.class), "description");
            String privateKey = conjurSecretUsernameSSHCredentials.getPrivateKey();

            assertEquals("", privateKey);
        }
    }

    @Test
    public void testGetPrivateKeys() {
        Jenkins jenkinsMock = mock(Jenkins.class);
        String credentialId = "test-cred-id";
        ConjurSecretCredentials mockCredentials = mock(ConjurSecretCredentials.class);
        when(mockCredentials.getId()).thenReturn(credentialId);
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("private-key");
        when(mockCredentials.getSecret()).thenReturn(mockSecret);
        try (MockedStatic<Jenkins> staticMockJenkins = mockStatic(Jenkins.class);
             MockedStatic<CredentialsProvider> mockProvider = mockStatic(CredentialsProvider.class)) {
            staticMockJenkins.when(Jenkins::get).thenReturn(jenkinsMock);
            mockProvider.when(() -> CredentialsProvider.lookupCredentials(eq(ConjurSecretCredentials.class), eq(jenkinsMock), any(), anyList())).thenReturn(Collections.singletonList(mockCredentials));
            ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                    CredentialsScope.GLOBAL, "testPipeline", "Test pipeline", credentialId, mock(Secret.class), "description");
            List<String> keys = conjurSecretUsernameSSHCredentials.getPrivateKeys();

            assertEquals(1, keys.size());
            assertEquals("private-key", keys.get(0));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "var-id", null, null);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("FAILED username,passphrase,credentialID fields is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsOk() {
        ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl();
        try (MockedStatic<ConjurAPIUtils> mockedStatic = mockStatic(ConjurAPIUtils.class)) {
            ItemGroup<Item> mockContext = mock(ItemGroup.class);
            mockedStatic
                    .when(() -> ConjurAPIUtils.validateCredential(any(), any(ConjurSecretUsernameSSHKeyCredentialsImpl.class)))
                    .thenReturn(FormValidation.ok("SUCCESS"));
            FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", Secret.fromString("secret"), "username");

            assertEquals(FormValidation.Kind.OK, result.kind);
            assertEquals("SUCCESS", result.getMessage());
        }
    }
}