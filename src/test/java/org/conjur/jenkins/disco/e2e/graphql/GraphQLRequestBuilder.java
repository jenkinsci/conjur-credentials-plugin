package org.conjur.jenkins.disco.e2e.graphql;

import com.google.gson.JsonObject;
import org.conjur.jenkins.disco.e2e.queries.SecretsQueries;

public final class GraphQLRequestBuilder {

    private GraphQLRequestBuilder() {
    }

    public static GraphQLRequest secretByName(String secretName) {
        JsonObject variables = new JsonObject();
        variables.addProperty("name", secretName);
        return new GraphQLRequest(SecretsQueries.FETCH_SECRET_BY_NAME, variables);
    }

    public static GraphQLRequest secretRisks(String secretId) {
        JsonObject variables = new JsonObject();
        variables.addProperty("secretId", secretId);
        return new GraphQLRequest(SecretsQueries.FETCH_SECRET_RISKS, variables);
    }

    public static final class GraphQLRequest {
        private final String query;
        private final JsonObject variables;

        private GraphQLRequest(String query, JsonObject variables) {
            this.query = query;
            this.variables = variables;
        }

        public String getQuery() {
            return query;
        }

        public JsonObject getVariables() {
            return variables;
        }
    }
}