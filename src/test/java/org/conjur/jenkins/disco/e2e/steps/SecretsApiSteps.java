package org.conjur.jenkins.disco.e2e.steps;

import org.conjur.jenkins.disco.e2e.config.DiscoE2eConfig;
import org.conjur.jenkins.disco.e2e.graphql.GraphQLClient;
import org.conjur.jenkins.disco.e2e.graphql.GraphQLResponse;
import org.conjur.jenkins.disco.security.CyberArkIdentityClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.junit.Assert.fail;

public final class SecretsApiSteps implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SecretsApiSteps.class.getName());

    private final DiscoE2eConfig config;
    private final GraphQLClient graphQLClient;
    private final CyberArkIdentityClient identityClient;

    private byte[] tokenBytes;

    public SecretsApiSteps(DiscoE2eConfig config) {
        this.config = config;
        this.graphQLClient = new GraphQLClient(config.httpClient(), config.graphQlUrl());
        this.identityClient = new CyberArkIdentityClient(config.httpClient());
    }

    public void authenticate() throws Exception {
        close();
        var passwordBytes = config.identityPassword().getBytes(StandardCharsets.UTF_8);
        try {
            tokenBytes = identityClient.login(
                    config.identityUrl(),
                    config.tenantId(),
                    config.identityUsername(),
                    passwordBytes);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    public GraphQLResponse waitForSecret(String secretName) throws Exception {
        ensureAuthenticated();
        LOG.info("Waiting for exported DisCo secret in GraphQL: " + secretName);
        return graphQLClient.waitForSecret(
                new String(tokenBytes, StandardCharsets.UTF_8),
                secretName,
                config.graphQlWaitTimeoutMs(),
                config.graphQlWaitIntervalMs());
    }

    public GraphQLResponse waitForSecretCount(String secretName, int expectedCount) throws Exception {
        ensureAuthenticated();
        LOG.info("Waiting for exported DisCo secret count in GraphQL: name=" + secretName
                + ", expectedCount=" + expectedCount);

        var deadline = System.currentTimeMillis() + config.graphQlWaitTimeoutMs();
        var lastResponse = "";
        while (System.currentTimeMillis() <= deadline) {
            var response = graphQLClient.querySecret(new String(tokenBytes, StandardCharsets.UTF_8), secretName);
            lastResponse = response.toJson();
            if (response.hasErrors()) {
                fail("GraphQL returned errors while waiting for secret count for '" + secretName + "':\n"
                        + response.errors());
            }
            if (countSecretsByName(response, secretName) == expectedCount) {
                return response;
            }
            Thread.sleep(config.graphQlWaitIntervalMs());
        }

        fail("Timed out waiting for exported DisCo secret '" + secretName + "' count to be " + expectedCount
                + ". Last GraphQL response:\n" + lastResponse);
        return waitForSecret(secretName);
    }

    public GraphQLResponse.Secret waitForSecretDescription(String secretName, String expectedDescription) throws Exception {
        ensureAuthenticated();
        LOG.info("Waiting for exported DisCo secret description in GraphQL: name=" + secretName
                + ", expectedDescription=" + expectedDescription);

        long deadline = System.currentTimeMillis() + config.graphQlWaitTimeoutMs();
        var lastResponse = "";
        GraphQLResponse.Secret lastSecret = null;
        while (System.currentTimeMillis() <= deadline) {
            var response = graphQLClient.querySecret(new String(tokenBytes, StandardCharsets.UTF_8), secretName);
            lastResponse = response.toJson();
            if (response.hasErrors()) {
                fail("GraphQL returned errors while waiting for secret description for '" + secretName + "':\n"
                        + response.errors());
            }

            var secret = response.secretNamed(secretName);
            if (secret != null) {
                lastSecret = secret;
                if (expectedDescription.equals(secret.description())) {
                    return secret;
                }
            }
            Thread.sleep(config.graphQlWaitIntervalMs());
        }

        fail("Timed out waiting for DisCo secret '" + secretName + "' description to be '" + expectedDescription
                + "'. Last observed description was '"
                + (lastSecret == null ? "<missing>" : lastSecret.description())
                + "'. Last GraphQL response:\n" + lastResponse);
        return lastSecret;
    }

    public GraphQLResponse fetchSecretRisksBySecretId(String secretId) throws Exception {
        ensureAuthenticated();
        LOG.info("Fetching DisCo secret risks from GraphQL for secret id: " + secretId);
        var response = graphQLClient.querySecretRisks(
                new String(tokenBytes, StandardCharsets.UTF_8),
                secretId);
        if (response.hasErrors()) {
            fail("GraphQL returned errors while fetching risks for secret id '" + secretId + "':\n" + response.errors());
        }
        return response;
    }

    public int countSecretsByName(GraphQLResponse response, String secretName) {
        return response.secretNameCount(secretName);
    }

    private void ensureAuthenticated() throws Exception {
        if (tokenBytes == null || tokenBytes.length == 0) {
            authenticate();
        }
    }

    @Override
    public void close() {
        if (tokenBytes != null) {
            Arrays.fill(tokenBytes, (byte) 0);
            tokenBytes = null;
        }
    }
}