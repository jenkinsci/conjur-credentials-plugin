package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;

class ConjurSecretCredentialsTest {

    @Mock
    private Jenkins jenkins = null;

    private ConjurSecretCredentials secretCredentials = null;
    private CredentialsNameProvider<ConjurSecretCredentials> provider = null;


    @BeforeEach
    void beforeEach() {
        secretCredentials = spy(ConjurSecretCredentials.class);
        provider = new ConjurSecretCredentials.NameProvider();
    }

    @Nested
    class NameProvider {

        @Test
        void testGetName() {
            assertEquals("nullnull (null)", provider.getName(secretCredentials));
        }
    }

}