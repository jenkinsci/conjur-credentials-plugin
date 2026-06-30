package org.conjur.jenkins.configuration;

import org.conjur.jenkins.CjplCode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.CjplCode.*;

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
        String telemetryData = String.format("in=%s&it=%s&iv=%s&vn=%s",
                DEFAULT_INTEGRATION_NAME,
                DEFAULT_INTEGRATION_TYPE,
                getPluginVersion(),
                DEFAULT_VENDOR_NAME);

        return Base64.getUrlEncoder().encodeToString(telemetryData.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the plugin version read from the JAR manifest ({@code Plugin-Version} attribute).
     * Maven's hpi-plugin writes this from the {@code <version>} in pom.xml at build time, so
     * it is always in sync with the actual built artifact and requires no file-system access.
     *
     * Falls back to {@code "unknown"} if the manifest is unreachable.
     */
    public static String getPluginVersion() {
        if (cachedPluginVersion != null) {
            return cachedPluginVersion;
        }

        cachedPluginVersion = readVersionFromManifest();
        return cachedPluginVersion;
    }

    // -------------------------------------------------------------------------

    private static String readVersionFromManifest() {
        try {
            // Locate the manifest that belongs to this class's JAR/HPI
            String className = TelemetryConfiguration.class.getName().replace('.', '/') + ".class";
            URL classResource = TelemetryConfiguration.class.getClassLoader().getResource(className);
            if (classResource == null) {
                LOGGER.warning(MANIFEST_RESOURCE_NOT_FOUND.format());
                return DEFAULT_VERSION;
            }

            String classPath = classResource.toString();
            // classPath is e.g. "jar:file:/…/conjur-credentials.hpi!/WEB-INF/classes/…"
            // Strip to the jar root and append the manifest path
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1)
                    + "/META-INF/MANIFEST.MF";

            try (InputStream is = URI.create(manifestPath).toURL().openStream()) {
                Manifest manifest = new Manifest(is);
                Attributes attrs = manifest.getMainAttributes();
                String version = attrs.getValue("Plugin-Version");
                if (version != null && !version.isBlank()) {
                    LOGGER.log(Level.INFO, PLUGIN_VERSION_FROM_MANIFEST.format(version));
                    return version.trim();
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, MANIFEST_VERSION_READ_FAILED.format(e.getMessage()));
        }

        return DEFAULT_VERSION;
    }
}
