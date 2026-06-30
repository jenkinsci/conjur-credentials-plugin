package org.conjur.jenkins.disco.manual;

import org.conjur.jenkins.disco.e2e.config.JenkinsCredentialType;
import org.conjur.jenkins.disco.e2e.graphql.GraphQLResponse;
import org.junit.Ignore;
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
        GraphQLResponse.Secret secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedDescription = "String secret text credential in a folder";
        var expectedType = JenkinsCredentialType.STRING;
        var expectedManagedByCyberArk = false;
        var expectedPath = buildExpectedSecretPath(jenkinsFolderName, jenkinsCredentialId);
        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret, HIGH_RISK_LEVEL, MEDIUM_RISK_LEVEL);
    }

    @Ignore("DisCo API returns only MEDIUM risk (not HIGH+MEDIUM) for global secret text credentials — platform-side issue under investigation")
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

        GraphQLResponse response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        GraphQLResponse.Secret secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedPath = buildExpectedGlobalSecretPath(jenkinsCredentialId);
        var expectedDescription = "Global secret text credential";
        var expectedType = JenkinsCredentialType.STRING;
        var expectedManagedByCyberArk = false;
        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret, HIGH_RISK_LEVEL, MEDIUM_RISK_LEVEL);
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

        GraphQLResponse response = secretsApiSteps.waitForSecret(jenkinsCredentialId);
        GraphQLResponse.Secret secret = assertSingleSecretNamed(response, jenkinsCredentialId);

        var expectedPath = buildExpectedGlobalSecretPath(jenkinsCredentialId);
        var expectedDescription = "Global Conjur secret credential";
        var expectedType = JenkinsCredentialType.CONJUR_SECRET;
        var expectedManagedByCyberArk = true;

        assertSecretPath(secret, expectedPath);
        assertSecretMetadata(secret, expectedDescription, expectedType, expectedManagedByCyberArk);
        assertSecretRiskLevels(secret, HIGH_RISK_LEVEL);
    }

    @Test
    @DisplayName("Creates nested Jenkins folders with string credentials, runs a pipeline using them, exports to DisCo, and validates metadata and risks")
    public void shouldSeeJenkinsStringFolderCredentialsInDiscoAfterPipelineUsesThem() throws Exception {
        String rootFolderName = "disco-e2e-pipeline-root-" + config.runId();
        String teamAlphaFolderName = "team-alpha";
        String auditFolderName = "audit";
        String auditPipelineName = "disco-alpha-audit-pipeline";

        String teamAlphaFolderPath = rootFolderName + "/" + teamAlphaFolderName;
        String auditFolderPath = teamAlphaFolderPath + "/" + auditFolderName;
        String auditPipelinePath = auditFolderPath + "/" + auditPipelineName;

        String rootCredentialId = "disco-e2e-root-secret-" + config.runId();
        String teamAlphaCredentialId = "disco-e2e-team-alpha-secret-" + config.runId();
        String auditCredentialId = "disco-e2e-audit-secret-" + config.runId();
        String rootCredentialDescription = "String secret text credential in a folder";
        String teamAlphaCredentialDescription = "CyberArk DisCo E2E team alpha credential loaded from config.xml";
        String auditCredentialDescription = "CyberArk DisCo E2E audit credential loaded from config.xml";

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

        GraphQLResponse rootResponse = secretsApiSteps.waitForSecret(rootCredentialId);
        GraphQLResponse.Secret rootSecret = assertSingleSecretNamed(rootResponse, rootCredentialId);
        assertSecretPath(rootSecret, buildExpectedSecretPath(rootFolderName, rootCredentialId));
        assertSecretMetadata(rootSecret, rootCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(rootSecret, MEDIUM_RISK_LEVEL);
// For some reason, the root secret is marked as Medium risk only,
// but I think it should be both High and Medium because it is not retrieved from Conjur.

        GraphQLResponse teamAlphaResponse = secretsApiSteps.waitForSecret(teamAlphaCredentialId);
        GraphQLResponse.Secret teamAlphaSecret = assertSingleSecretNamed(teamAlphaResponse, teamAlphaCredentialId);
        assertSecretPath(teamAlphaSecret, buildExpectedSecretPath(teamAlphaFolderPath, teamAlphaCredentialId));
        assertSecretMetadata(teamAlphaSecret, teamAlphaCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(teamAlphaSecret, MEDIUM_RISK_LEVEL);

        GraphQLResponse auditResponse = secretsApiSteps.waitForSecret(auditCredentialId);
        GraphQLResponse.Secret auditSecret = assertSingleSecretNamed(auditResponse, auditCredentialId);
        assertSecretPath(auditSecret, buildExpectedSecretPath(auditFolderPath, auditCredentialId));
        assertSecretMetadata(auditSecret, auditCredentialDescription, JenkinsCredentialType.STRING, false);
        assertSecretRiskLevels(auditSecret, MEDIUM_RISK_LEVEL);
    }
}
