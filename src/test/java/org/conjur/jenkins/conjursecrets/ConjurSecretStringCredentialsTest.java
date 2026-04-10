package org.conjur.jenkins.conjursecrets;

import org.conjur.jenkins.conjursecrets.ConjurSecretStringCredentials.NameProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConjurSecretStringCredentialsTest {

    @Test
    public void testGetNameWithDescription() {
        ConjurSecretStringCredentials mockCred = mock(ConjurSecretStringCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn("used for DB connection");
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret (used for DB connection)", name);
    }

    @Test
    public void testGetNameWithoutDescription() {
        ConjurSecretStringCredentials mockCred = mock(ConjurSecretStringCredentials.class);
        when(mockCred.getDisplayName()).thenReturn("conjur-secret");
        when(mockCred.getDescription()).thenReturn(null); // or ""
        NameProvider nameProvider = new NameProvider();
        String name = nameProvider.getName(mockCred);

        assertEquals("conjur-secret", name);
    }

}
