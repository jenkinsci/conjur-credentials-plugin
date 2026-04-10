package org.conjur.jenkins.configuration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

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

    @Test
    public void testBuildTelemetryHeaderEncodesCorrectly() {
        String encodedHeader = TelemetryConfiguration.buildTelemetryHeader();
        String decodedHeader = new String(Base64.getUrlDecoder().decode(encodedHeader));

        assertTrue(decodedHeader.contains("in=Jenkins Plugin"));
        assertTrue(decodedHeader.contains("it=cybr-secretsmanager-jenkins"));
        assertTrue(decodedHeader.contains("vn=Jenkins"));
    }

    @Test
    public void testGetTelemetryCachesHeader() {
        String header1 = TelemetryConfiguration.getTelemetryHeader();
        String header2 = TelemetryConfiguration.getTelemetryHeader();

        assertSame(header1, header2);
    }

    @Test
    public void testGetPluginVersionFindsVersion() {
        String version = TelemetryConfiguration.getPluginVersion();

        assertNotEquals("unknown", version);
    }

    @Test
    public void testGetPluginVersionReturnsUnknown() {
        try (MockedStatic<TelemetryConfiguration> mockedTelemetry = mockStatic(TelemetryConfiguration.class)) {
            mockedTelemetry.when(TelemetryConfiguration::getPluginVersion)
                    .thenReturn("unknown");
            assertEquals("unknown", TelemetryConfiguration.getPluginVersion());
        }
    }
}
