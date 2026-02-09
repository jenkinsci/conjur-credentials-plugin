package org.conjur.jenkins.conjursecrets;

import org.conjur.jenkins.conjursecrets.ConjurSecretFileCredentials.NameProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConjurSecretFileCredentialsTest {

    @Test
    void testGetNameWithDescription() {
        ConjurSecretFileCredentials mockCred = mock(ConjurSecretFileCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn("used for DB connection");
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret (used for DB connection)", name);
    }

    @Test
    void testGetNameWithoutDescription() {
        ConjurSecretFileCredentials mockCred = mock(ConjurSecretFileCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn(null);
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret", name);
    }

}
