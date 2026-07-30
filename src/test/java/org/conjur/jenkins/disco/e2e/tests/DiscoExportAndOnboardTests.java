package org.conjur.jenkins.disco.e2e.tests;

import org.conjur.jenkins.disco.e2e.config.JenkinsCredentialType;
import org.conjur.jenkins.disco.e2e.utils.ScreenshotExtension;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.GLOBAL_SECRET_CREDENTIAL_XML;
import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.MEDIUM_RISK_LEVEL;
import static org.conjur.jenkins.disco.e2e.config.OnboardSecretConfiguration.*;
import static org.conjur.jenkins.disco.e2e.helpers.ExpectedCredentialPathBuilder.buildExpectedGlobalSecretPath;

public class DiscoExportAndOnboardTests extends DiscoE2eTestBase {

    @Rule
    public ScreenshotExtension screenshotRule = new ScreenshotExtension(page);

    @Test
    @DisplayName("Creates a global Jenkins secret, validates DisCo export, onboards it in UI, and applies detected Conjur JWT configuration")
    public void shouldExportAndOnboardSecret() throws Exception {
        var jenkinsCredentialId = "disco-e2e-secret-text-secret-" + config.runId();
        var jenkinsCredentialSecretValue = "disco-e2e-global-secret-text-value-" + config.runId();
        var jenkinsCredentialDescriptionValue = "disco-e2e-initial-description-value-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET}}", jenkinsCredentialSecretValue,
                        "{{DISCO_GLOBAL_SECRET_DESCRIPTION}}", jenkinsCredentialDescriptionValue,
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId
                ));
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        var response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        assertExportedSecret(
                response,
                jenkinsCredentialId,
                buildExpectedGlobalSecretPath(jenkinsCredentialId),
                jenkinsCredentialDescriptionValue,
                JenkinsCredentialType.STRING,
                false,
                MEDIUM_RISK_LEVEL);

        page.navigate(TARGET_URL);
        idiraLoginPage.login(config.identityUsername(), config.identityPassword());

        discoveryPage.searchForSecret(jenkinsCredentialId);
        discoveryPage.clickRiskFindingsTab();
        discoveryPage.clickOnboardSecret();

        onboardPage.clickCreateAndOnboard();
        onboardPage.waitForAuthenticatorStatus("Created");
        onboardPage.waitForWorkloadStatus("Created");
        onboardPage.waitForSecretStatus("Created");

        var conjurAccount = onboardPage.getOnboardSecretConfiguration(CONJUR_ACCOUNT);
        var applianceUrl = onboardPage.getOnboardSecretConfiguration(CONJUR_APPLIANCE_URL);
        var serviceId = onboardPage.getOnboardSecretConfiguration(SERVICE_ID);

        jenkinsSteps.applyGlobalConjurJwtConfig(applianceUrl, conjurAccount, serviceId);
    }
}
