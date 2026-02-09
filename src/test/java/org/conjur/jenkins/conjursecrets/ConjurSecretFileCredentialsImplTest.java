package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.model.ModelObject;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurSecretFileCredentialsImplTest {

    @Mock
    private ModelObject mockStoreContext;

    @Test
    void testStoredInConjurStorage() {
        ConjurSecretFileCredentialsImpl conjurSecretStringCredentials = mock(ConjurSecretFileCredentialsImpl.class);
        when(conjurSecretStringCredentials.storedInConjurStorage()).thenReturn(true);

        assertTrue(conjurSecretStringCredentials.storedInConjurStorage());
    }

    @Test
    void testSetStoredInConjurStorage() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        conjurSecretFileCredentials.setStoredInConjurStorage(true);

        assertTrue(conjurSecretFileCredentials.storedInConjurStorage());
    }

    @Test
    void testSetContext() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        conjurSecretFileCredentials.setContext(mockStoreContext);

        assertNotNull(conjurSecretFileCredentials.getContext());
    }

    @Test
    void testSetInheritedContext() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        conjurSecretFileCredentials.setInheritedContext(mockStoreContext);

        assertNotNull(conjurSecretFileCredentials.getInheritedContext());
    }

    @Test
    void testTagName() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        String expectedResult = "";

        assertEquals(expectedResult, conjurSecretFileCredentials.getNameTag());
    }

    @Test
    void testGetDisplayName() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        String testVariableId = "TestPipeline";
        String result = conjurSecretFileCredentials.getDisplayName();

        assertEquals("ConjurSecretFile:" + testVariableId, result);
    }

    @Test
    void testGetSecretReturnsSecret() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        conjurSecretFileCredentials.setStoredInConjurStorage(true);
        Secret secret = mock(Secret.class);
        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjur(any(), any(), any())).thenReturn(secret);

            assertNotNull(conjurSecretFileCredentials.getSecret());
            assertEquals(secret, conjurSecretFileCredentials.getSecret());
        }
    }

    @Test
    void testGetSecretReturnsSecretWithInheritance() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");
        conjurSecretFileCredentials.setStoredInConjurStorage(false);
        Secret secret = mock(Secret.class);
        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), any(), any())).thenReturn(secret);

            assertNotNull(conjurSecretFileCredentials.getSecret());
            assertEquals(secret, conjurSecretFileCredentials.getSecret());
        }
    }

    @Test
    void testGetFileName() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline");

        assertEquals("conjur-file", conjurSecretFileCredentials.getFileName());
    }

    @Test
    void testGetContentThrowsException() {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = spy(new ConjurSecretFileCredentialsImpl(
                    CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline"));
        doReturn(null).when(conjurSecretFileCredentials).getSecret();
        assertThrows(IOException.class, conjurSecretFileCredentials::getContent);
    }

    @Test
    void testGetContentReturnsSecretWithoutInheritance() throws Exception {
        ConjurSecretFileCredentialsImpl conjurSecretFileCredentials = spy(new ConjurSecretFileCredentialsImpl(
                CredentialsScope.GLOBAL, "DevTeam-1", "test Pipeline", "TestPipeline"));
        conjurSecretFileCredentials.setStoredInConjurStorage(true);
        Secret secret = mock(Secret.class);
        when(secret.getPlainText()).thenReturn("MockedSecret");
        doReturn(secret).when(conjurSecretFileCredentials).getSecret();
        InputStream result = conjurSecretFileCredentials.getContent();
        byte[] buffer = result.readAllBytes();

        assertEquals("MockedSecret", new String(buffer, StandardCharsets.UTF_8));
    }


    @Test
    void testGetDisplayNameOfDescriptor() {
        ConjurSecretFileCredentialsImpl.DescriptorImpl descriptor = new ConjurSecretFileCredentialsImpl.DescriptorImpl();
        assertEquals("Conjur Secret File", descriptor.getDisplayName());
    }

}
