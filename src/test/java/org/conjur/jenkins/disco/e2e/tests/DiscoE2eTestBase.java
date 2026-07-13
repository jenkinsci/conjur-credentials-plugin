package org.conjur.jenkins.disco.e2e.tests;

import org.conjur.jenkins.disco.e2e.config.DiscoE2eConfig;
import org.conjur.jenkins.disco.e2e.config.JenkinsCredentialType;
import org.conjur.jenkins.disco.e2e.graphql.GraphQLResponse;
import org.conjur.jenkins.disco.e2e.steps.JenkinsSteps;
import org.conjur.jenkins.disco.e2e.steps.SecretsApiSteps;
import org.junit.*;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DiscoE2eTestBase {

    protected static final DiscoE2eConfig config = new DiscoE2eConfig();

    protected static JenkinsSteps jenkinsSteps;
    protected static SecretsApiSteps secretsApiSteps;

    protected static final Logger LOG = Logger.getLogger(DiscoE2eTestBase.class.getName());
    protected static final Path GLOBAL_SECRET_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/disco-global-secret-credential.xml");
    protected static final Path GLOBAL_CONJUR_SECRET_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/disco-global-conjur-secret-credential.xml");
    protected static final Path ROOT_FOLDER_WITH_STRING_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/config.xml");
    protected static final Path ROOT_FOLDER_WITHOUT_CREDENTIALS_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder-without-credentials/config.xml");
    protected static final Path FOLDER_WITH_CONJUR_PIPELINE_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/jobs/disco-conjur-pipeline-folder/config.xml");
    protected static final Path CONJUR_SECRET_PIPELINE_XML = Path.of(
            "scripts/templates/disco/jobs/disco-conjur-pipeline-folder/jobs/disco-conjur-secret-pipeline/config.xml");
    protected static final Path TEAM_ALPHA_FOLDER_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/config.xml");
    protected static final Path AUDIT_FOLDER_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/jobs/audit/config.xml");
    protected static final Path AUDIT_PIPELINE_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/jobs/audit/jobs/disco-alpha-audit-pipeline/config.xml");
    protected static final String HIGH_RISK_LEVEL = "HIGH";
    protected static final String MEDIUM_RISK_LEVEL = "MEDIUM";

    @BeforeClass
    public static void setUp() throws Exception {
        assumeLiveGraphQlEnabled();
        jenkinsSteps = new JenkinsSteps(config);
        secretsApiSteps = new SecretsApiSteps(config);
        secretsApiSteps.authenticate();
    }

    @AfterClass
    public static void tearDown() {
        if (secretsApiSteps != null) {
            secretsApiSteps.close();
        }
    }

    protected String buildExpectedSecretPath(String folderName, String credentialName) {
        return folderName + ":" + credentialName;
    }

    protected String buildExpectedGlobalSecretPath(String credentialName) {
        return "Global:" + credentialName;
    }

    protected GraphQLResponse.Secret assertSingleSecretNamed(GraphQLResponse response, String secretName) {
        assertThat(secretsApiSteps.countSecretsByName(response, secretName))
                .as("GraphQL should return exactly one exported DisCo secret named '%s'. Response:%n%s",
                        secretName, response.toJson())
                .isEqualTo(1);

        GraphQLResponse.Secret secret = response.secretNamed(secretName);
        assertThat(secret)
                .as("GraphQL should return an exported DisCo secret with exact name '%s'. Response:%n%s",
                        secretName, response.toJson())
                .isNotNull();

        assertThat(secret.name())
                .as("GraphQL secret name should exactly match '%s'. Result:%n%s", secretName, secret.toJson())
                .isEqualTo(secretName);

        return secret;
    }

    protected void assertSecretPath(GraphQLResponse.Secret secret, String expectedPath) {
        assertThat(secret.containsPath(expectedPath))
                .as("GraphQL result for exported DisCo secret '%s' should include Jenkins path '%s'. Result:%n%s",
                        secret.name(), expectedPath, secret.toJson())
                .isTrue();
    }

    protected void assertSecretDescription(GraphQLResponse.Secret secret, String expectedDescription) {
        assertThat(secret.description())
                .as("GraphQL result for exported DisCo secret '%s' should include credential description '%s'. Result:%n%s",
                        secret.name(), expectedDescription, secret.toJson())
                .isEqualTo(expectedDescription);
    }

    protected void assertSecretType(GraphQLResponse.Secret secret, JenkinsCredentialType expectedType) {
        assertThat(secret.type())
                .as("GraphQL result for exported DisCo secret '%s' should include Jenkins credential type '%s'. Result:%n%s",
                        secret.name(), expectedType.type(), secret.toJson())
                .isEqualTo(expectedType.type());
    }

    protected void assertSecretManagedByCyberArk(GraphQLResponse.Secret secret, boolean expectedManagedByCyberArk) {
        assertThat(secret.managedByCyberArk())
                .as("GraphQL result for exported DisCo secret '%s' should include managedByCyberArk='%s'. Result:%n%s",
                        secret.name(), expectedManagedByCyberArk, secret.toJson())
                .isEqualTo(Boolean.toString(expectedManagedByCyberArk));
    }

    protected void assertSecretMetadata(
            GraphQLResponse.Secret secret,
            String expectedDescription,
            JenkinsCredentialType expectedType,
            boolean expectedManagedByCyberArk) {

        assertSecretDescription(secret, expectedDescription);
        assertSecretType(secret, expectedType);
        assertSecretManagedByCyberArk(secret, expectedManagedByCyberArk);
    }

    protected void assertSecretRiskLevels(GraphQLResponse.Secret secret, String... expectedRiskLevels) throws Exception {
        assertThat(secret.id())
                .as("GraphQL result for exported DisCo secret should include a secret id. Result:%n%s",
                        secret.toJson())
                .isNotBlank();

        GraphQLResponse riskResponse = secretsApiSteps.fetchSecretRisksBySecretId(secret.id());
        List<String> riskLevels = riskResponse.riskLevelsForFirstSecret();

        assertThat(riskLevels)
                .as("GraphQL risk query for exported DisCo secret id '%s' should return exactly %s risk level(s) %s. Response:%n%s",
                        secret.id(), expectedRiskLevels.length, List.of(expectedRiskLevels), riskResponse.toJson())
                .containsExactlyInAnyOrder(expectedRiskLevels);
    }

    private static void assumeLiveGraphQlEnabled() {
        Assume.assumeTrue(
                "Skipping live DisCo GraphQL validation unless DISCO_GRAPHQL_RUN=true is set by the runner",
                config.isLiveGraphQlEnabled());
    }
}