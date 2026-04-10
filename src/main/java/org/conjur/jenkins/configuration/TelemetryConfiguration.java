package org.conjur.jenkins.configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static String finalHeader = null;
    private static String cachedPluginVersion = null;


    public static String getTelemetryHeader() {
        if (finalHeader == null) {
            finalHeader = buildTelemetryHeader();
        }
        return finalHeader;
    }

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
        if (cachedPluginVersion != null) {
            return cachedPluginVersion;
        }

        Path changelogPath = Paths.get("CHANGELOG.md");
        Pattern versionPattern = Pattern.compile("## \\[([\\d]+(?:\\.[\\d]+)*)\\]");

        try (InputStream inputStream = Files.newInputStream(changelogPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = versionPattern.matcher(line);

                if (matcher.find()) {
                    cachedPluginVersion = matcher.group(1);
                    LOGGER.info("Found version in CHANGELOG.md: " + cachedPluginVersion);
                    return cachedPluginVersion;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading CHANGELOG.md from the JAR.", e);
        }

        cachedPluginVersion = DEFAULT_VERSION;
        return cachedPluginVersion;
    }
}
