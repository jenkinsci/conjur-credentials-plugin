package org.conjur.jenkins.conjursecrets;

import hudson.model.ModelObject;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.conjursecrets.ConjurSecretDockerCertCredentials.NameProvider;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ConjurSecretDockerCertCredentialsTest {

    public static class DummyConjurSecretDockerCertCredentials extends ConjurSecretDockerCertCredentials {
        DummyConjurSecretDockerCertCredentials() {
            super(null, "id", "desc"); // pass null scope for test
        }

        @Override
        public String getClientKeyId() {
            return "key-id";
        }

        @Override
        public String getClientCertificateId() {
            return "cert-id";
        }

        @Override
        public String getCaCertificateId() {
            return "ca-id";
        }

        @Override
        public String getDisplayName() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getNameTag() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Secret getSecret() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setContext(ModelObject context) {
            // TODO Auto-generated method stub

        }

        @Override
        public ModelObject getContext() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setInheritedContext(ModelObject context) {
            // TODO Auto-generated method stub

        }

        @Override
        public ModelObject getInheritedContext() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setStoredInConjurStorage(boolean storedInConjurStorage) {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean storedInConjurStorage() {
            // TODO Auto-generated method stub
            return false;
        }
    }

    @Test
    public void testGetNameWithDescription() {
        ConjurSecretDockerCertCredentials mockCred = mock(ConjurSecretDockerCertCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn("used for DB connection");
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret (used for DB connection)", name);
    }

    @Test
    public void testGetNameWithoutDescription() {
        ConjurSecretDockerCertCredentials mockCred = mock(ConjurSecretDockerCertCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn(null); // or ""
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret", name);
    }

    @Test
    public void testGetClientKeySecret() {
        DummyConjurSecretDockerCertCredentials creds = new DummyConjurSecretDockerCertCredentials();
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("some-key");
        try (MockedStatic<ConjurAPI> apiMock = mockStatic(ConjurAPI.class)) {
            apiMock.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), eq(creds), eq("key-id")))
                    .thenReturn(mockSecret);

            assertEquals(mockSecret, creds.getClientKeySecret());
        }
    }

    @Test
    public void testGetClientCertificateReturnsPlainText() {
        DummyConjurSecretDockerCertCredentials creds = new DummyConjurSecretDockerCertCredentials();
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("cert-plain-text");
        try (MockedStatic<ConjurAPI> apiMock = mockStatic(ConjurAPI.class)) {
            apiMock.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), eq(creds), eq("cert-id")))
                    .thenReturn(mockSecret);

            assertEquals("cert-plain-text", creds.getClientCertificate());
        }
    }

    @Test
    public void testGetClientCertificateReturnsNullIfNoSecret() {
        DummyConjurSecretDockerCertCredentials creds = new DummyConjurSecretDockerCertCredentials();
        try (MockedStatic<ConjurAPI> apiMock = mockStatic(ConjurAPI.class)) {
            apiMock.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), eq(creds), eq("cert-id")))
                    .thenReturn(null);

            assertNull(creds.getClientCertificate());
        }
    }

    @Test
    public void testGetServerCaCertificateReturnsPlainText() {
        DummyConjurSecretDockerCertCredentials creds = new DummyConjurSecretDockerCertCredentials();
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn("ca-cert-plain-text");
        try (MockedStatic<ConjurAPI> apiMock = mockStatic(ConjurAPI.class)) {
            apiMock.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), eq(creds), eq("ca-id")))
                    .thenReturn(mockSecret);

            assertEquals("ca-cert-plain-text", creds.getServerCaCertificate());
        }
    }

    @Test
    public void testGetServerCaCertificateReturnsNullIfNoSecret() {
        DummyConjurSecretDockerCertCredentials creds = new DummyConjurSecretDockerCertCredentials();
        try (MockedStatic<ConjurAPI> apiMock = mockStatic(ConjurAPI.class)) {
            apiMock.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), eq(creds), eq("ca-id")))
                    .thenReturn(null);

            assertNull(creds.getServerCaCertificate());
        }
    }
}
