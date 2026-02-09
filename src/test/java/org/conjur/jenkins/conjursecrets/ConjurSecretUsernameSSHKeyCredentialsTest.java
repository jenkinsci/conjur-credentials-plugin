package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;

class ConjurSecretUsernameSSHKeyCredentialsTest {

    private ConjurSecretUsernameSSHKeyCredentials secretCredentials = null;
    private CredentialsNameProvider<StandardUsernameCredentials> provider = null;

    @BeforeEach
    void beforeEach() {
        secretCredentials = spy(ConjurSecretUsernameSSHKeyCredentials.class);
        provider = new ConjurSecretUsernameSSHKeyCredentials.NameProvider();
    }

    @Nested
    class NameProvider {

        @Test
        void testGetName() {
            assertEquals("ConjurSecretUsernameSSHKey:null/*ConjurSecretUsernameSSHKey* (null)",
                    provider.getName(secretCredentials));
        }
    }

}