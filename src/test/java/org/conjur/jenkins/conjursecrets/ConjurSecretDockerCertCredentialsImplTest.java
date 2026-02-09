package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurSecretDockerCertCredentialsImplTest {

    @Mock
    private ModelObject mockStoreContext;

    @Test
    void testStoredInConjurStorage() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = mock(ConjurSecretDockerCertCredentialsImpl.class);
        when(conjurSecretDockerCertCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretDockerCertCredentials.storedInConjurStorage());
    }

    @Test
    void testSetStoredInConjurStorage() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretDockerCertCredentials.storedInConjurStorage());
    }

    @Test
    void testGetSecret() {
        Secret mockSecret = mock(Secret.class);
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = spy(new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid"));
        doReturn(mockSecret).when(conjurSecretDockerCertCredentials).getClientKeySecret();
        Secret result = conjurSecretDockerCertCredentials.getSecret();

        assertEquals(mockSecret, result);
    }

    @Test
    void testSetContext() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretDockerCertCredentials.getContext());
    }

    @Test
    void testSetInheritedContext() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretDockerCertCredentials.getInheritedContext());
    }

    @Test
    void testTagName() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretDockerCertCredentials.getNameTag());
    }

    @Test
    void testGetDisplayName() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        String testVariableId = "DevTeam-1";
        String result = conjurSecretDockerCertCredentials.getDisplayName();

        assertEquals("ConjurSecretDockerCert:" + testVariableId, result);
    }

    @Test
    void testGetClientKeyId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("key-id", conjurSecretDockerCertCredentials.getClientKeyId());
    }

    @Test
    void testGetClientCertificateId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("cert-id", conjurSecretDockerCertCredentials.getClientCertificateId());
    }

    @Test
    void testGetCaCertificateId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("ca-certid", conjurSecretDockerCertCredentials.getCaCertificateId());
    }

    @Test
    void testGetDisplayNameOfDescriptor() {
        ConjurSecretDockerCertCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretDockerCertCredentialsImpl.DescriptorImpl();

        assertEquals("Conjur Secret Docker Client Certificate", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretDockerCertCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretDockerCertCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", null, null);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("All certificate fields are required"));
    }

    @Test
    void testDoTestConnectionReturnsOk() {
        ConjurSecretDockerCertCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretDockerCertCredentialsImpl.DescriptorImpl();
        @SuppressWarnings("unchecked")
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        try (MockedStatic<ConjurAPIUtils> mockedStatic = mockStatic(ConjurAPIUtils.class)) {
            mockedStatic
                    .when(() -> ConjurAPIUtils.validateCredential(any(), any(ConjurSecretDockerCertCredentialsImpl.class)))
                    .thenReturn(FormValidation.ok("SUCCESS"));
            FormValidation result = descriptor.doTestConnection(mockContext, "test-id", "cert-id", "ca-cert-id");

            assertEquals(FormValidation.Kind.OK, result.kind);
            assertEquals("SUCCESS", result.getMessage());
        }
    }
}
