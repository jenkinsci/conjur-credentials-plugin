package org.conjur.jenkins.disco.e2e.tests;

import org.conjur.jenkins.disco.e2e.config.JenkinsCredentialType;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DiscoExportSecretsTest extends DiscoE2eTestBase {

    @Test
    @DisplayName("Creates a folder in Jenkins and a credential scoped to it, " +
            "then validates the credential is exported to DisCo")
    public void shouldSeeFolderCredentialInDisco() throws Exception {
        var jenkinsFolderName = "disco-e2e-config-folder-" + config.runId();
        var jenkinsCredentialId = "disco-e2e-config-secret-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                ROOT_FOLDER_WITH_STRING_CREDENTIAL_XML,
                List.of("create-job", jenkinsFolderName),
                Map.of(
                        "{{DISCO-STRING-CREDENTIAL-SECRET}}", jenkinsCredentialId,
                        "{{DISCO-STRING-CREDENTIAL-SECRET-VALUE}}", config.runId()
                ));
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        var response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        var secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedDescription = "String secret text credential in a folder";
        var expectedType = JenkinsCredentialType.STRING;
        var expectedManagedByCyberArk = false;
        var expectedPath = buildExpectedSecretPath(jenkinsFolderName, jenkinsCredentialId);
        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret);
    }

    @Test
    @DisplayName("Creates a global system secret text credential in Jenkins then validates the " +
            "credential is exported to DisCo")
    public void shouldSeeGlobalSecretTextCredentialInDisco() throws Exception {
        var jenkinsCredentialId = "disco-e2e-secret-text-secret-" + config.runId();
        var jenkinsCredentialSecretValue = "disco-e2e-global-secret-text-value-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId,
                        "{{DISCO_GLOBAL_SECRET}}", jenkinsCredentialSecretValue
                ));
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        var response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        var secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedPath = buildExpectedGlobalSecretPath(jenkinsCredentialId);
        var expectedDescription = "Global secret text credential";
        var expectedType = JenkinsCredentialType.STRING;
        var expectedManagedByCyberArk = false;
        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret);
    }

    @Test
    @DisplayName("Creates a global Conjur secret credential in Jenkins then validates it is managed by CyberArk in DisCo")
    public void shouldSeeGlobalConjurSecretCredentialManagedByCyberArkInDisco() throws Exception {
        var jenkinsCredentialId = "disco-e2e-conjur-secret-" + config.runId();
        var jenkinsConjurVariableId = "conjur-secret-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                GLOBAL_CONJUR_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_CONJUR_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId,
                        "{{DISCO_GLOBAL_CONJUR_SECRET_VARIABLE_ID}}", jenkinsConjurVariableId
                ));
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        var response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        var secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedPath = buildExpectedGlobalSecretPath(jenkinsCredentialId);
        var expectedDescription = "Global Conjur secret credential";
        var expectedType = JenkinsCredentialType.CONJUR_SECRET;
        var expectedManagedByCyberArk = true;

        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret);
    }

    @Test
    @DisplayName("Creates a folder credential, removes it from Jenkins by loading updated folder XML, exports again, " +
            "and validates the credential is removed from DisCo")
    public void shouldRemoveDeletedFolderSecretTextCredentialFromDisco() throws Exception {
        var jenkinsFolderName = "disco-e2e-deleted-folder-" + config.runId();
        var jenkinsCredentialId = "disco-e2e-deleted-secret-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                ROOT_FOLDER_WITH_STRING_CREDENTIAL_XML,
                List.of("create-job", jenkinsFolderName),
                Map.of(
                        "{{DISCO-STRING-CREDENTIAL-SECRET}}", jenkinsCredentialId,
                        "{{DISCO-STRING-CREDENTIAL-SECRET-VALUE}}", "disco-e2e-deleted-secret-value-" + config.runId()
                ));
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        var createdResponse = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        var createdSecret = assertSingleSecretNamed(createdResponse, jenkinsCredentialId);
        assertSecretPath(createdSecret, buildExpectedSecretPath(jenkinsFolderName, jenkinsCredentialId));

        jenkinsSteps.createFromXml(
                ROOT_FOLDER_WITHOUT_CREDENTIALS_XML,
                List.of("update-job", jenkinsFolderName),
                Map.of());
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        secretsApiSteps.waitForSecretCount(jenkinsCredentialId, 0);
    }

    @Test
    @DisplayName("Creates a global secret text credential, updates its description and value in Jenkins, " +
            "exports again, and validates the updated metadata in DisCo")
    public void shouldUpdateGlobalSecretTextCredentialMetadataInDisco() throws Exception {
        var jenkinsCredentialId = "disco-e2e-updated-secret-" + config.runId();
        var initialSecretValue = "disco-e2e-initial-secret-value-" + config.runId();
        var initialDescriptionValue = "disco-e2e-initial-descibtion-value-" + config.runId();
        var updatedSecretValue = "disco-e2e-updated-secret-value-" + config.runId();
        var updatedDescription = "Updated global secret text credential for DisCo E2E-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        // Create the original global Jenkins secret text credential.
        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET}}", initialSecretValue,
                        "{{DISCO_GLOBAL_SECRET_DESCRIPTION}}", initialDescriptionValue,
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId
                ));

        // Export the original credential to DisCo.
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        // Confirm the original credential exists in DisCo before updating it.
        var initialResponse = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        var initialSecret = assertSingleSecretNamed(initialResponse, jenkinsCredentialId);
        assertSecretMetadata(initialSecret, initialDescriptionValue, JenkinsCredentialType.STRING, false);

        // Update the same Jenkins credential. The ID must stay the same because Jenkins rejects
        // update-credentials-by-xml when the path ID and XML ID do not match.
        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("update-credentials-by-xml", "system::system::jenkins", "_", jenkinsCredentialId),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET}}", updatedSecretValue,
                        "{{DISCO_GLOBAL_SECRET_DESCRIPTION}}", updatedDescription,
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId
                ));

        // Export again so DisCo receives the updated credential snapshot.
        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);

        // Wait for the existing DisCo secret to receive the updated metadata. Waiting only by
        // secret name is not enough here because the secret already exists from the first export.
        var updatedSecret = secretsApiSteps.waitForSecretDescription(jenkinsCredentialId, updatedDescription);
        assertSecretPath(updatedSecret, buildExpectedGlobalSecretPath(jenkinsCredentialId));
        assertSecretMetadata(updatedSecret, updatedDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(updatedSecret);
    }

    @Test
    @DisplayName("Exports the same global secret text credential twice and validates DisCo does not create duplicates")
    public void shouldNotCreateDuplicateDiscoSecretsWhenExportRunsTwice() throws Exception {
        var jenkinsCredentialId = "disco-e2e-duplicate-sync-secret-" + config.runId();
        var jenkinsCredentialSecretValue = "disco-e2e-duplicate-sync-secret-value-" + config.runId();
        var discoExportGroovyScript = Path.of(config.triggerGroovyScriptPath());

        jenkinsSteps.createFromXml(
                GLOBAL_SECRET_CREDENTIAL_XML,
                List.of("create-credentials-by-xml", "system::system::jenkins", "_"),
                Map.of(
                        "{{DISCO_GLOBAL_SECRET_CREDENTIAL_ID}}", jenkinsCredentialId,
                        "{{DISCO_GLOBAL_SECRET}}", jenkinsCredentialSecretValue
                ));

        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);
        var firstExportResponse = secretsApiSteps.waitForSecretCount(jenkinsCredentialId, 1);
        var firstExportSecret = assertSingleSecretNamed(firstExportResponse, jenkinsCredentialId);
        assertSecretPath(firstExportSecret, buildExpectedGlobalSecretPath(jenkinsCredentialId));

        jenkinsSteps.runJenkinsGroovy(discoExportGroovyScript);
        var secondExportResponse = secretsApiSteps.waitForSecretCount(jenkinsCredentialId, 1);
        var secondExportSecret = assertSingleSecretNamed(secondExportResponse, jenkinsCredentialId);

        assertSecretPath(secondExportSecret, buildExpectedGlobalSecretPath(jenkinsCredentialId));
        assertSecretMetadata(secondExportSecret, "Global secret text credential", JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(secondExportSecret);
    }

    @Test
    @DisplayName("Creates nested Jenkins folders with string credentials, runs a pipeline using them, exports to DisCo, and validates metadata and risks")
    public void shouldSeeJenkinsStringFolderCredentialsInDiscoAfterPipelineUsesThem() throws Exception {
        var rootFolderName = "disco-e2e-pipeline-root-" + config.runId();
        var teamAlphaFolderName = "team-alpha";
        var auditFolderName = "audit";
        var auditPipelineName = "disco-alpha-audit-pipeline";

        var teamAlphaFolderPath = rootFolderName + "/" + teamAlphaFolderName;
        var auditFolderPath = teamAlphaFolderPath + "/" + auditFolderName;
        var auditPipelinePath = auditFolderPath + "/" + auditPipelineName;

        var rootCredentialId = "disco-e2e-root-secret-" + config.runId();
        var teamAlphaCredentialId = "disco-e2e-team-alpha-secret-" + config.runId();
        var auditCredentialId = "disco-e2e-audit-secret-" + config.runId();
        var rootCredentialDescription = "String secret text credential in a folder";
        var teamAlphaCredentialDescription = "CyberArk DisCo E2E team alpha credential loaded from config.xml";
        var auditCredentialDescription = "CyberArk DisCo E2E audit credential loaded from config.xml";

        jenkinsSteps.createFromXml(
                ROOT_FOLDER_WITH_STRING_CREDENTIAL_XML,
                List.of("create-job", rootFolderName),
                Map.of(
                        "{{DISCO-STRING-CREDENTIAL-SECRET}}", rootCredentialId,
                        "{{DISCO-STRING-CREDENTIAL-SECRET-VALUE}}", "disco-e2e-root-secret-value-" + config.runId()
                ));

        jenkinsSteps.createFromXml(
                TEAM_ALPHA_FOLDER_XML,
                List.of("create-job", teamAlphaFolderPath),
                Map.of(
                        "disco-team-alpha-secret", teamAlphaCredentialId,
                        "disco-team-alpha-secret-value", "disco-e2e-team-alpha-secret-value-" + config.runId(),
                        "CyberArk DisCo team alpha secret text credential",
                        teamAlphaCredentialDescription
                ));

        jenkinsSteps.createFromXml(
                AUDIT_FOLDER_XML,
                List.of("create-job", auditFolderPath),
                Map.of(
                        "disco-audit-secret", auditCredentialId,
                        "disco-audit-secret-value", "disco-e2e-audit-secret-value-" + config.runId(),
                        "CyberArk DisCo audit secret text credential",
                        auditCredentialDescription
                ));

        jenkinsSteps.createFromXml(
                AUDIT_PIPELINE_XML,
                List.of("create-job", auditPipelinePath),
                Map.of(
                        "disco-team-alpha-secret", teamAlphaCredentialId,
                        "disco-audit-secret", auditCredentialId,
                        "CyberArk DisCo Discovery pipeline using audit folder credentials.",
                        "CyberArk DisCo E2E pipeline using folder credentials loaded from config.xml."
                ));

        jenkinsSteps.runJenkinsBuild(auditPipelinePath);
        jenkinsSteps.runJenkinsGroovy(Path.of(config.triggerGroovyScriptPath()));

        var rootResponse = secretsApiSteps.waitForSecret(rootCredentialId);
        var rootSecret = assertSingleSecretNamed(rootResponse, rootCredentialId);
        assertSecretPath(rootSecret, buildExpectedSecretPath(rootFolderName, rootCredentialId));
        assertSecretMetadata(rootSecret, rootCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(rootSecret);

        var teamAlphaResponse = secretsApiSteps.waitForSecret(teamAlphaCredentialId);
        var teamAlphaSecret = assertSingleSecretNamed(teamAlphaResponse, teamAlphaCredentialId);
        assertSecretPath(teamAlphaSecret, buildExpectedSecretPath(teamAlphaFolderPath, teamAlphaCredentialId));
        assertSecretMetadata(teamAlphaSecret, teamAlphaCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(teamAlphaSecret);

        var auditResponse = secretsApiSteps.waitForSecret(auditCredentialId);
        var auditSecret = assertSingleSecretNamed(auditResponse, auditCredentialId);
        assertSecretPath(auditSecret, buildExpectedSecretPath(auditFolderPath, auditCredentialId));
        assertSecretMetadata(auditSecret, auditCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(auditSecret);
    }
}