package org.conjur.jenkins.api;

import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConjurAuthnInfoTest {

    @Test
    void testConjurAuthnInfo() {
        ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
        ConjurAuthnInfo conjurAuthnInfo = new ConjurAuthnInfo();

        conjurAuthnInfo.setConjurConfiguration(conjurConfiguration);
        conjurAuthnInfo.setApplianceUrl("http://conjur_server");
        conjurAuthnInfo.setAuthnPath("authn");
        conjurAuthnInfo.setAccount("cucumber");
        conjurAuthnInfo.setLogin("admin");
        conjurAuthnInfo.setApiKey("sample-api-key".getBytes());

        assertEquals(conjurConfiguration, conjurAuthnInfo.getConjurConfiguration());
        assertEquals("http://conjur_server", conjurAuthnInfo.getApplianceUrl());
        assertEquals("authn", conjurAuthnInfo.getAuthnPath());
        assertEquals("cucumber", conjurAuthnInfo.getAccount());
        assertEquals("admin", conjurAuthnInfo.getLogin());
        assertEquals("sample-api-key", new String(conjurAuthnInfo.getApiKey()));

        // Verify toString format
        StringBuilder sb = new StringBuilder();
        sb.append("ConjurAuthnInfo{");
        sb.append("\nconjurConfiguration=").append(conjurConfiguration);
        sb.append(", \napplianceUrl='http://conjur_server'");
        sb.append(", \nauthnPath='authn'");
        sb.append(", \naccount='cucumber'");
        sb.append(", \nlogin='admin'");
        sb.append("}");

        assertEquals(sb.toString(), conjurAuthnInfo.toString());
    }
}
