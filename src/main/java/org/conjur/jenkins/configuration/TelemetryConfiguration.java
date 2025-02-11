package org.conjur.jenkins.configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelemetryConfiguration {

    private static final Logger LOGGER = Logger.getLogger(TelemetryConfiguration.class.getName());

    private static final String DEFAULT_INTEGRATION_NAME = "Jenkins Plugin";
    private static final String DEFAULT_INTEGRATION_TYPE = "cybr-secretsmanager-jenkins";
    private static final String DEFAULT_VENDOR_NAME = "Jenkins";
    private static final String DEFAULT_VERSION = "unknown";

    /**
     * Builds the telemetry header, including encoding it to Base64.
     *  
     * @return Base64 encoded telemetry header.
     */
    public static String buildTelemetryHeader() {
        String integrationName = DEFAULT_INTEGRATION_NAME;
        String integrationType = DEFAULT_INTEGRATION_TYPE;
        String integrationVersion = getPluginVersion();  // Get version from changelog
        String vendorName = DEFAULT_VENDOR_NAME;

        String telemetryData = String.format("in=%s&it=%s&iv=%s&vn=%s", 
                                             integrationName, 
                                             integrationType,
                                             integrationVersion, 
                                             vendorName);

        return Base64.getUrlEncoder().encodeToString(telemetryData.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Fetches the plugin version from the CHANGELOG.md file.
     * 
     * @return The version string or "unknown" if the version is not found.
     */
    public static String getPluginVersion() {
        String changelogFilePath = "/CHANGELOG.md";
        
        Pattern versionPattern = Pattern.compile("## \\[([\\d]+(?:\\.[\\d]+)*)\\]");

        try (InputStream inputStream = TelemetryConfiguration.class.getResourceAsStream(changelogFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = versionPattern.matcher(line);

                if (matcher.find()) {
                    LOGGER.info("looking fro the top version from CHANGELOG.md :: " +matcher.group(1));

                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading CHANGELOG.md from the JAR.", e);
        }
        
        return DEFAULT_VERSION;  
    }
}
