package org.conjur.jenkins.disco.e2e.graphql;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.Assert.fail;

public final class GraphQLClient {

    private static final Logger LOG = Logger.getLogger(GraphQLClient.class.getName());
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String graphQlUrl;

    public GraphQLClient(OkHttpClient httpClient, String graphQlUrl) {
        this.httpClient = httpClient;
        this.graphQlUrl = graphQlUrl;
    }

    public GraphQLResponse querySecret(String bearerToken, String secretName) throws IOException {
        return execute(bearerToken, GraphQLRequestBuilder.secretByName(secretName), secretName);
    }

    public GraphQLResponse querySecretRisks(String bearerToken, String secretId) throws IOException {
        return execute(bearerToken, GraphQLRequestBuilder.secretRisks(secretId), "");
    }

    public GraphQLResponse waitForSecret(
            String bearerToken,
            String secretName,
            long timeoutMs,
            long intervalMs) throws Exception {

        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastResponse = "";

        while (System.currentTimeMillis() <= deadline) {
            GraphQLResponse response = querySecret(bearerToken, secretName);
            lastResponse = response.toJson();

            if (response.hasErrors()) {
                fail("GraphQL returned errors while looking for secret '" + secretName + "':\n" + response.errors());
            }

            if (response.hasSecretNamed(secretName)) {
                LOG.info("Found exported DisCo secret in GraphQL: " + secretName);
                return response;
            }

            Thread.sleep(intervalMs);
        }

        fail("Timed out waiting for exported DisCo secret '" + secretName + "'. Last GraphQL response:\n" + lastResponse);
        return GraphQLResponse.forSecret(new JsonObject(), secretName);
    }

    private GraphQLResponse execute(
            String bearerToken,
            GraphQLRequestBuilder.GraphQLRequest graphQlRequest,
            String expectedSecretName) throws IOException {

        Request request = new Request.Builder()
                .url(graphQlUrl)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(GSON.toJson(graphQlRequest), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("GraphQL HTTP " + response.code() + ": " + responseBody);
            }
            return GraphQLResponse.forSecret(JsonParser.parseString(responseBody).getAsJsonObject(), expectedSecretName);
        }
    }
}