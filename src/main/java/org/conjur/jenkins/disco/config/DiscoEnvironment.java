package org.conjur.jenkins.disco.config;

/**
 * CyberArk DisCo service environments.
 *
 * The active environment is selected at Jenkins startup via the environment
 * variable {@code CYBERARK_DISCO_ENV}.  The variable value must match one of
 * the enum names (case-insensitive).  When the variable is absent or
 * unrecognised, {@link #PRODUCTION} is used.
 *
 * Example (docker-compose / k8s):
 * <pre>
 *   CYBERARK_DISCO_ENV=INTEGRATION
 * </pre>
 */
public enum DiscoEnvironment {

    // ── Standard environments ─────────────────────────────────────────────────

    DEV               ("https://service.management.cyberark-everest-dev.com/",
                       "https://platform-discovery.cyberark-everest-dev.com"),
    INTEGRATION       ("https://service.management.integration-cyberark.cloud/",
                       "https://platform-discovery.integration-cyberark.cloud"),
    INTEGRATION_DEV   ("https://service.management.cyberark-everest-integdev.cloud/",
                       "https://platform-discovery.cyberark-everest-integdev.cloud"),
    PRE_PROD          ("https://service.management.cyberark-everest-pre-prod.cloud/",
                       "https://platform-discovery.cyberark-everest-pre-prod.cloud"),
    PRODUCTION        ("https://service.management.cyberark.cloud/",
                       "https://platform-discovery.cyberark.cloud"),
    PT                ("https://service.management.pt-cyberark.cloud/",
                       "https://platform-discovery.pt-cyberark.cloud"),
    TEST              ("https://service.management.cyberark-everest-test.com/",
                       "https://platform-discovery.cyberark-everest-test.com");

    // ── Environment variable name ─────────────────────────────────────────────

    /** Set this JVM/OS environment variable to select a non-production environment. */
    public static final String ENV_VAR = "CYBERARK_DISCO_ENV";

    private final String baseUrl;
    private final String platformDiscoveryUrl;

    DiscoEnvironment(String baseUrl, String platformDiscoveryUrl) {
        this.baseUrl = baseUrl;
        this.platformDiscoveryUrl = platformDiscoveryUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPlatformDiscoveryUrl() {
        return platformDiscoveryUrl;
    }

    /**
     * Returns true for the two production environments (PRODUCTION and GOV_PROD).
     * Any other environment is considered non-production, which disables the
     * manual-trigger rate limit.
     */
    public boolean isProduction() {
        return this == PRODUCTION;
    }

    public String getTrustedDomainSuffix() {
        try {
            String host = new java.net.URI(platformDiscoveryUrl).getHost();
            int dot = host.indexOf('.');
            return dot >= 0 ? host.substring(dot + 1) : host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Resolves the active environment from {@code CYBERARK_DISCO_ENV}.
     * Checks system property first (so -DCYBERARK_DISCO_ENV=X works in Maven/tests),
     * then falls back to the OS environment variable.
     * Returns {@link #PRODUCTION} when absent or unrecognised.
     */
    public static DiscoEnvironment resolve() {
        String value = System.getProperty(ENV_VAR);
        if (value == null || value.isBlank()) value = System.getenv(ENV_VAR);
        if (value == null || value.isBlank()) return PRODUCTION;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PRODUCTION;
        }
    }
}
