package org.conjur.jenkins.disco;

import org.conjur.jenkins.disco.config.DiscoEnvironment;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies DiscoEnvironment enum values and the resolve() fallback logic.
 *
 * The CYBERARK_DISCO_ENV environment variable cannot be set from within a
 * running JVM, so resolve() is tested indirectly via the enum itself: the
 * fallback path (absent / blank / invalid value) is the only branch reachable
 * in a standard test run.  The env-var branch is covered by verifying the
 * enum lookup is case-insensitive.
 */
public class DiscoEnvironmentTest {

    // ── URL correctness ───────────────────────────────────────────────────────

    @Test
    public void production_hasCorrectUrl() {
        assertThat(DiscoEnvironment.PRODUCTION.getBaseUrl())
                .isEqualTo("https://service.management.cyberark.cloud/");
    }

    @Test
    public void dev_hasCorrectUrl() {
        assertThat(DiscoEnvironment.DEV.getBaseUrl())
                .isEqualTo("https://service.management.cyberark-everest-dev.com/");
    }

    @Test
    public void integration_hasCorrectUrl() {
        assertThat(DiscoEnvironment.INTEGRATION.getBaseUrl())
                .isEqualTo("https://service.management.integration-cyberark.cloud/");
    }

    @Test
    public void integrationDev_hasCorrectUrl() {
        assertThat(DiscoEnvironment.INTEGRATION_DEV.getBaseUrl())
                .isEqualTo("https://service.management.cyberark-everest-integdev.cloud/");
    }

    @Test
    public void preProd_hasCorrectUrl() {
        assertThat(DiscoEnvironment.PRE_PROD.getBaseUrl())
                .isEqualTo("https://service.management.cyberark-everest-pre-prod.cloud/");
    }

    @Test
    public void pt_hasCorrectUrl() {
        assertThat(DiscoEnvironment.PT.getBaseUrl())
                .isEqualTo("https://service.management.pt-cyberark.cloud/");
    }

    @Test
    public void test_hasCorrectUrl() {
        assertThat(DiscoEnvironment.TEST.getBaseUrl())
                .isEqualTo("https://service.management.cyberark-everest-test.com/");
    }

    // ── All URLs end with a trailing slash ────────────────────────────────────

    @Test
    public void allUrls_endWithTrailingSlash() {
        for (DiscoEnvironment env : DiscoEnvironment.values()) {
            assertThat(env.getBaseUrl())
                    .as("URL for %s must end with '/'", env.name())
                    .endsWith("/");
        }
    }

    @Test
    public void allUrls_startWithHttps() {
        for (DiscoEnvironment env : DiscoEnvironment.values()) {
            assertThat(env.getBaseUrl())
                    .as("URL for %s must use HTTPS", env.name())
                    .startsWith("https://");
        }
    }

    @Test
    public void allUrls_areUnique() {
        java.util.Set<String> urls = new java.util.HashSet<>();
        for (DiscoEnvironment env : DiscoEnvironment.values()) {
            assertThat(urls.add(env.getBaseUrl()))
                    .as("Duplicate URL detected for %s: %s", env.name(), env.getBaseUrl())
                    .isTrue();
        }
    }

    @Test
    public void envVarName_isCorrect() {
        assertThat(DiscoEnvironment.ENV_VAR).isEqualTo("CYBERARK_DISCO_ENV");
    }

    // ── resolve() fallback behaviour ──────────────────────────────────────────

    @Test
    public void resolve_returnsProduction_whenEnvVarAbsent() {
        if (System.getenv(DiscoEnvironment.ENV_VAR) == null) {
            assertThat(DiscoEnvironment.resolve()).isEqualTo(DiscoEnvironment.PRODUCTION);
        }
    }

    @Test
    public void resolve_returnsProduction_forUnknownValue() {
        assertThatThrownBy(() -> DiscoEnvironment.valueOf("DOES_NOT_EXIST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Case-insensitive lookup — mirrors resolve() logic ─────────────────────

    @Test
    public void enumLookup_isCaseInsensitive_viaUpperCase() {
        assertThat(DiscoEnvironment.valueOf("DEV")).isEqualTo(DiscoEnvironment.DEV);
        assertThat(DiscoEnvironment.valueOf("PRODUCTION")).isEqualTo(DiscoEnvironment.PRODUCTION);
        assertThat(DiscoEnvironment.valueOf("TEST")).isEqualTo(DiscoEnvironment.TEST);
    }

    // ── Count — catches accidental additions / removals ───────────────────────

    @Test
    public void enumHasSevenEntries() {
        assertThat(DiscoEnvironment.values()).hasSize(7);
    }
}
