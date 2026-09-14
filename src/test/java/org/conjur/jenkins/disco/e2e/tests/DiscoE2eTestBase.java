package org.conjur.jenkins.disco.e2e.tests;

import org.conjur.jenkins.disco.e2e.config.JenkinsCredentialType;
import org.conjur.jenkins.disco.e2e.graphql.GraphQLResponse;
import org.conjur.jenkins.disco.e2e.steps.JenkinsSteps;
import org.conjur.jenkins.disco.e2e.steps.SecretsApiSteps;
import org.junit.*;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public abstract class DiscoE2eTestBase extends PlaywrightTestBase {


    protected static JenkinsSteps jenkinsSteps;
    protected static SecretsApiSteps secretsApiSteps;

    protected static final Logger LOG = Logger.getLogger(DiscoE2eTestBase.class.getName());

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


    private static void assumeLiveGraphQlEnabled() {
        Assume.assumeTrue(
                "Skipping live DisCo GraphQL validation unless DISCO_GRAPHQL_RUN=true is set by the runner",
                config.isLiveGraphQlEnabled());
    }

    protected void assertExportedSecret(
            GraphQLResponse response,
            String expectedSecretName,
            String expectedPath,
            String expectedDescription,
            JenkinsCredentialType expectedType,
            boolean expectedManagedByCyberArk,
            String... expectedRiskLevels) throws Exception {

        assertEquals(1, secretsApiSteps.countSecretsByName(response, expectedSecretName));
        var secret = secretsApiSteps.secretNamed(response, expectedSecretName);
        assertNotNull(secret);
        assertEquals(expectedSecretName, secret.name());
        assertTrue(secret.containsPath(expectedPath));
        assertEquals(expectedDescription, secret.description());
        assertEquals(expectedType.type(), secret.type());
        assertEquals(Boolean.toString(expectedManagedByCyberArk), secret.managedByCyberArk());
        assertFalse(secret.id().isBlank());

        var actualRiskLevels = secretsApiSteps.waitForRiskLevels(secret.id(), expectedRiskLevels);
        assertThat(actualRiskLevels).containsExactlyInAnyOrder(expectedRiskLevels);
    }
}