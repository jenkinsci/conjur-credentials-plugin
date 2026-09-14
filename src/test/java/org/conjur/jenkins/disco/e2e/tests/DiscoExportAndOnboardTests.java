package org.conjur.jenkins.disco.e2e.tests;

import org.conjur.jenkins.disco.e2e.utils.ScreenshotExtension;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.GLOBAL_SECRET_CREDENTIAL_XML;
import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.CONJUR_SECRET_PIPELINE_XML;
import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.STRING_CREDENTIAL_PIPELINE_XML;
import static org.conjur.jenkins.disco.e2e.config.OnboardSecretConfiguration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DiscoExportAndOnboardTests extends DiscoE2eTestBase {

    @Rule
    public ScreenshotExtension screenshotRule = new ScreenshotExtension(page);

    private static String artifactFileName = "retrieved-secret.txt";

    @Test
    @DisplayName("Creates a global Jenkins secret, validates the DisCo export, onboards it in the UI" +
            "and verifies the exported secret in a Jenkins Pipeline")
    public void shouldExportAndOnboardSecret() throws Exception {
        var jenkinsCredentialId = "disco-e2e-secret-text-secret-" + config.runId();
        var jenkinsCredentialExpectedSecretValue = "disco-e2e-global-secret-text-value-" + config.runId();
        var jenkinsCredentialDescriptionValue = "disco-e2e-initial-description-value-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());
        var pipelineJobName = "disco-e2e-onboard-pipeline-" + config.runId();

        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET}}", jenkinsCredentialExpectedSecretValue,
                        "{{DISCO_GLOBAL_SECRET_DESCRIPTION}}", jenkinsCredentialDescriptionValue,
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId
                ));

        // Create pipeline that uses the native Jenkins credentials retrieval
        jenkinsSteps.createFromXml(
                STRING_CREDENTIAL_PIPELINE_XML,
                List.of("create-job", pipelineJobName),
                Map.of(
                        "{{DISCO-STRING-CREDENTIAL-ID}}", jenkinsCredentialId
                ));

        // Run the pipeline with the string credential
        jenkinsSteps.runJenkinsBuild(pipelineJobName);

        // Export to DisCo
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        // Validate export
        secretsApiSteps.waitForSecret(jenkinsCredentialId);

        // Onboard the secret via UI and extract Conjur configuration
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
        var applianceUrl = onboardPage.getOnboardSecretConfiguration(SECRETS_MANAGER_APPLIANCE_URL);
        var serviceId = onboardPage.getOnboardSecretConfiguration(SERVICE_ID);
        var secretPath = onboardPage.getSecretPath();

        // Apply JWT config to Jenkins
        jenkinsSteps.applyGlobalConjurJwtConfig(applianceUrl, conjurAccount, serviceId);

        // Update the existing pipeline with the onboarded Conjur secret credential
        jenkinsSteps.createFromXml(
                CONJUR_SECRET_PIPELINE_XML,
                List.of("update-job", pipelineJobName),
                Map.of(
                        "{{DISCO-CONJUR-PIPELINE-CREDENTIAL-ID}}", secretPath
                ));

        jenkinsSteps.runJenkinsBuildWithRetries(pipelineJobName, 15, 10_000L);

        // Fetch the artifact and compare retrieved secret with the expected value
        var actualSecret = jenkinsSteps.getLastBuildArtifact(pipelineJobName, artifactFileName);
        assertEquals(actualSecret, jenkinsCredentialExpectedSecretValue);
    }
}
