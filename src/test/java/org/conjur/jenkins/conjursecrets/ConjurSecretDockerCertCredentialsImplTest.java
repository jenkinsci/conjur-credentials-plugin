package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretDockerCertCredentialsImplTest {

    @Mock
    private ModelObject mockStoreContext;

    @Test
    public void testStoredInConjurStorage() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = mock(ConjurSecretDockerCertCredentialsImpl.class);
        when(conjurSecretDockerCertCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretDockerCertCredentials.storedInConjurStorage());
    }

    @Test
    public void testSetStoredInConjurStorage() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretDockerCertCredentials.storedInConjurStorage());
    }

    @Test
    public void testGetSecret() {
        Secret mockSecret = mock(Secret.class);
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = spy(new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid"));
        doReturn(mockSecret).when(conjurSecretDockerCertCredentials).getClientKeySecret();
        Secret result = conjurSecretDockerCertCredentials.getSecret();

        assertEquals(mockSecret, result);
    }

    @Test
    public void testSetContext() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretDockerCertCredentials.getContext());
    }

    @Test
    public void testSetInheritedContext() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        conjurSecretDockerCertCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretDockerCertCredentials.getInheritedContext());
    }

    @Test
    public void testTagName() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretDockerCertCredentials.getNameTag());
    }

    @Test
    public void testGetDisplayName() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");
        String testVariableId = "DevTeam-1";
        String result = conjurSecretDockerCertCredentials.getDisplayName();

        assertEquals("ConjurSecretDockerCert:" + testVariableId, result);
    }

    @Test
    public void testGetClientKeyId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("key-id", conjurSecretDockerCertCredentials.getClientKeyId());
    }

    @Test
    public void testGetClientCertificateId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("cert-id", conjurSecretDockerCertCredentials.getClientCertificateId());
    }

    @Test
    public void testGetCaCertificateId() {
        ConjurSecretDockerCertCredentialsImpl conjurSecretDockerCertCredentials = new ConjurSecretDockerCertCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "key-id", "cert-id", "ca-certid");

        assertEquals("ca-certid", conjurSecretDockerCertCredentials.getCaCertificateId());
    }

    @Test
    public void testGetDisplayNameOfDescriptor() {
        ConjurSecretDockerCertCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretDockerCertCredentialsImpl.DescriptorImpl();

        assertEquals("Conjur Secret Docker Client Certificate", descriptor.getDisplayName());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoTestConnectionReturnsErrorIfVariableIdIsEmpty() {
        ConjurSecretDockerCertCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretDockerCertCredentialsImpl.DescriptorImpl();
        ItemGroup<Item> mockContext = mock(ItemGroup.class);
        FormValidation result = descriptor.doTestConnection(mockContext, "cred-id", null, null);

        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertTrue(result.getMessage().contains("All certificate fields are required"));
    }

    @Test
    public void testDoTestConnectionReturnsOk() {
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
