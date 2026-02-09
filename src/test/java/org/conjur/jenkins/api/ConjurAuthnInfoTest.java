package org.conjur.jenkins.api;

import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;


class ConjurAuthnInfoTest {

    @Mock
    private ConjurConfiguration conjurConfiguration;

    private ConjurAuthnInfo conjurAuthnInfo;

    @BeforeEach
    void beforeEach() {
        conjurAuthnInfo = new ConjurAuthnInfo();
        conjurAuthnInfo.setConjurConfiguration(conjurConfiguration);
        conjurAuthnInfo.setApplianceUrl("http://conjur_server");
        conjurAuthnInfo.setAuthnPath("authn");
        conjurAuthnInfo.setAccount("cucumber");
        conjurAuthnInfo.setLogin("admin");
        conjurAuthnInfo.setApiKey("sample-api-key".getBytes());
    }

    @Test
    void testToStringContainsconjurConfiguration() {
        String result = conjurAuthnInfo.toString();
        assertTrue(result.contains("conjurConfiguration"));
    }

    @Test
    void testToStringContainsApplianceUrl() {
        String result = conjurAuthnInfo.toString();
        assertTrue(result.contains("http://conjur_server"));
    }

    @Test
    void testToStringContainsAuthnPath() {
        String result = conjurAuthnInfo.toString();
        assertTrue(result.contains("authn"));
    }

    @Test
    void testToStringContainsAccount() {
        String result = conjurAuthnInfo.toString();
        assertTrue(result.contains("cucumber"));
    }

    @Test
    void testToStringContainsLogin() {
        String result = conjurAuthnInfo.toString();
        assertTrue(result.contains("admin"));
    }

    @Test
    void testApiKeySetCorrectly() {
        assertNotNull(conjurAuthnInfo.getApiKey());
        assertEquals("sample-api-key", new String(conjurAuthnInfo.getApiKey()));
    }
}
