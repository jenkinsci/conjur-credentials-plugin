package org.conjur.jenkins.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Base64;

@RunWith(MockitoJUnitRunner.class)
class TelemetryConfigurationTest {

    @InjectMocks
    private TelemetryConfiguration telemetryConfiguration;

    @BeforeEach
    void setUp() {
    }

    @Test
    public void testGetPluginVersion() {
        String version = TelemetryConfiguration.getPluginVersion();

        assertNotNull(version);
        assertFalse(version.isEmpty());

        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"));
    }
    
    @Test
    public void testBuildTelemetryHeader() {
        String header = TelemetryConfiguration.buildTelemetryHeader();

        assertNotNull(header);
        assertFalse(header.isEmpty());

        assertTrue(isBase64Encoded(header));
    }

    private boolean isBase64Encoded(String str) {
        try {
            Base64.getUrlDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
