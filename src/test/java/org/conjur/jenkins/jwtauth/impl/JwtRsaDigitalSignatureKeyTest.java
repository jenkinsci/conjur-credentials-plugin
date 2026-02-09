
package org.conjur.jenkins.jwtauth.impl;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPrivateKey;

import static org.junit.jupiter.api.Assertions.*;


class JwtRsaDigitalSignatureKeyTest {

    private static final String TEST_ID = "test123";
    private JwtRsaDigitalSignatureKey key;

    @BeforeEach
    void beforeEach() {
        key = new JwtRsaDigitalSignatureKey(TEST_ID);
    }

    @Test
    void testGetId() {
        assertEquals(TEST_ID, key.getId());
    }

    @Test
    void testGetCreationTimeIsRecent() {
        long now = System.currentTimeMillis() / 1000;
        long creationTime = key.getCreationTime();

        assertTrue(creationTime > 0, "creationTime should be positive");
        assertTrue(Math.abs(now - creationTime) < 10, "creationTime should be recent");
    }

    @Test
    void testToSigningKeyReturnsPrivateKey() {
        RSAPrivateKey privatekey = key.toSigningKey();
        assertNotNull(privatekey, "toSigningKey() should return a private key (or null if it is not initialized");
    }

}

