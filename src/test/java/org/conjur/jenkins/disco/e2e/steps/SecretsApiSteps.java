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
        byte[] passwordBytes = config.identityPassword().getBytes(StandardCharsets.UTF_8);
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

    public GraphQLResponse fetchSecretRisksBySecretId(String secretId) throws Exception {
        ensureAuthenticated();
        LOG.info("Fetching DisCo secret risks from GraphQL for secret id: " + secretId);
        GraphQLResponse response = graphQLClient.querySecretRisks(
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