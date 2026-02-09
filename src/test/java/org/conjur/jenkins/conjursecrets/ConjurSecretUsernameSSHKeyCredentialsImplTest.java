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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurSecretUsernameSSHKeyCredentialsImplTest {

    private JenkinsRule j;

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
    private final String id = "test-id";
    private final String username = "test-username";
    private final String credentialID = "test-credential-id";
    private final String description = "test-description";

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void getDisplayName() {
        assertEquals("Conjur Secret Username SSHKey Credential",
                ConjurSecretUsernameSSHKeyCredentialsImpl.getDescriptorDisplayName());
    }

    @Test
    void testDoFillCredentialIDItems() {
        ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl();
        Item item = mock(Item.class);
        ListBoxModel listBoxModel = descriptor.doFillCredentialIDItems(item, "uri");

        assertEquals(0, listBoxModel.size());
    }

    @Test
    void testStoredInConjurStorage() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = mock(ConjurSecretUsernameSSHKeyCredentialsImpl.class);
        when(conjurSecretUsernameSSHCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretUsernameSSHCredentials.storedInConjurStorage());
    }

    @Test
    void testSetStoredInConjurStorage() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretUsernameSSHCredentials.storedInConjurStorage());
    }


    @Test
    void testGetNameTag() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");

        assertTrue(conjurSecretUsernameSSHCredentials.getNameTag().isEmpty());
    }

    @Test
    void testGetSecret() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");

        assertNull(conjurSecretUsernameSSHCredentials.getSecret());
    }

    @Test
    void testGetContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = mock(ConjurSecretUsernameSSHKeyCredentialsImpl.class);
        when(conjurSecretUsernameSSHCredentials.getContext()).thenReturn(mockStoreContext);

        assertEquals(mockStoreContext, conjurSecretUsernameSSHCredentials.getContext());
    }

    @Test
    void testSetContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameSSHCredentials.getContext());
    }

    @Test
    void testSetInheritedContext() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretUsernameSSHCredentials.getInheritedContext());
    }

    @Test
    void testDisplayName() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        String result = conjurSecretUsernameSSHCredentials.getDisplayName();

        assertEquals("ConjurSecretUsernameSSHKey:DevTeam-1", result);
    }

    @Test
    void testGetPassphrase() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        Secret secretMock = mock(Secret.class);
        conjurSecretUsernameSSHCredentials.setPassphrase(secretMock);
        Secret result = conjurSecretUsernameSSHCredentials.getPassphrase();

        assertNotNull(result);
    }

    @Test
    void testGetCredentialID() {
        ConjurSecretUsernameSSHKeyCredentialsImpl conjurSecretUsernameSSHCredentials = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1", "Test pipeline", mock(Secret.class), "description");
        conjurSecretUsernameSSHCredentials.setCredentialID("mock-cred");
        String result = conjurSecretUsernameSSHCredentials.getCredentialID();

        assertNotNull(result);
    }

    @Test
    void testGetPrivateKey() {
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
    void testGetPrivateKeyWhenCredentialNotFound() {
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
    void testGetPrivateKeys() {
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
    void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretUsernameSSHKeyCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "var-id", null, null);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("FAILED username,passphrase,credentialID fields is required"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsOk() {
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