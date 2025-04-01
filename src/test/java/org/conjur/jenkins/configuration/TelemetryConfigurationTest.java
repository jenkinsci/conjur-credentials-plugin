package org.conjur.jenkins.configuration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

class TelemetryConfigurationTest {

    @Test
    void testGetTelemetryHeader() {
        String header = TelemetryConfiguration.buildTelemetryHeader();

        assertNotNull(header, "Telemetry header should not be null");
        assertFalse(header.isEmpty(), "Telemetry header should not be empty");

        assertTrue(isBase64Encoded(header), "Telemetry header should be Base64 encoded");
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
