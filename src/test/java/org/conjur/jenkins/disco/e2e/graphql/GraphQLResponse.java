package org.conjur.jenkins.disco.e2e.graphql;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GraphQLResponse {

    private static final Gson GSON = new Gson();

    private final JsonObject raw;
    private final List<Secret> matchingSecrets;

    private GraphQLResponse(JsonObject raw, String expectedSecretName) {
        this.raw = raw;
        List<JsonObject> matches = new ArrayList<>();
        if (expectedSecretName != null && !expectedSecretName.isBlank()) {
            findMatchingSecrets(raw.get("data"), expectedSecretName, matches);
        }
        List<Secret> secrets = new ArrayList<>();
        for (JsonObject match : matches) {
            secrets.add(new Secret(match));
        }
        this.matchingSecrets = Collections.unmodifiableList(secrets);
    }

    public static GraphQLResponse forSecret(JsonObject raw, String expectedSecretName) {
        return new GraphQLResponse(raw, expectedSecretName);
    }

    public boolean hasErrors() {
        return raw.has("errors")
                && raw.get("errors").isJsonArray()
                && !raw.getAsJsonArray("errors").isEmpty();
    }

    public JsonElement errors() {
        return raw.get("errors");
    }

    public boolean hasSecretNamed(String expectedName) {
        return secretNamed(expectedName) != null;
    }

    public Secret secretNamed(String expectedName) {
        for (Secret matchingSecret : matchingSecrets) {
            if (matchingSecret.hasName(expectedName)) {
                return matchingSecret;
            }
        }
        return null;
    }

    public int secretNameCount(String expectedName) {
        int count = 0;
        for (Secret matchingSecret : matchingSecrets) {
            if (matchingSecret.hasName(expectedName)) {
                count++;
            }
        }
        return count;
    }

    public String toJson() {
        return GSON.toJson(raw);
    }

    public List<SecretRisk> risksForFirstSecret() {
        JsonArray items = secretItems();
        if (items == null || items.size() == 0 || !items.get(0).isJsonObject()) {
            return Collections.emptyList();
        }
        JsonObject firstSecret = items.get(0).getAsJsonObject();
        if (!firstSecret.has("risks") || !firstSecret.get("risks").isJsonArray()) {
            return Collections.emptyList();
        }

        List<SecretRisk> risks = new ArrayList<>();
        for (JsonElement riskElement : firstSecret.getAsJsonArray("risks")) {
            if (riskElement != null && riskElement.isJsonObject()) {
                risks.add(new SecretRisk(riskElement.getAsJsonObject()));
            }
        }
        return Collections.unmodifiableList(risks);
    }

    public List<String> riskLevelsForFirstSecret() {
        List<String> riskLevels = new ArrayList<>();
        for (SecretRisk risk : risksForFirstSecret()) {
            if (!risk.riskLevel().isBlank()) {
                riskLevels.add(risk.riskLevel());
            }
        }
        return Collections.unmodifiableList(riskLevels);
    }

    private JsonArray secretItems() {
        if (!raw.has("data") || !raw.get("data").isJsonObject()) {
            return null;
        }
        JsonObject data = raw.getAsJsonObject("data");
        if (!data.has("secrets") || !data.get("secrets").isJsonObject()) {
            return null;
        }
        JsonObject secrets = data.getAsJsonObject("secrets");
        return secrets.has("items") && secrets.get("items").isJsonArray()
                ? secrets.getAsJsonArray("items")
                : null;
    }

    private static void findMatchingSecrets(JsonElement element, String expectedName, List<JsonObject> matches) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (fieldContains(object, "name", expectedName) || credentialIdEquals(object, expectedName)) {
                matches.add(object);
                return;
            }
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
                findMatchingSecrets(entry.getValue(), expectedName, matches);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                findMatchingSecrets(child, expectedName, matches);
            }
        }
    }

    private static boolean containsPath(JsonElement element, String expectedPath) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (fieldContains(object, "additionalData", expectedPath)
                    || fieldContains(object, "location", expectedPath)
                    || fieldContains(object, "path", expectedPath)
                    || fieldContains(object, "scopePath", expectedPath)
                    || fieldContains(object, "originId", expectedPath)) {
                return true;
            }
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (containsPath(entry.getValue(), expectedPath)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                if (containsPath(child, expectedPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean credentialIdEquals(JsonObject object, String expectedValue) {
        return object.has("credentialId")
                && object.get("credentialId").isJsonPrimitive()
                && expectedValue.equals(object.get("credentialId").getAsString());
    }

    private static boolean fieldContains(JsonObject object, String field, String expectedValue) {
        return object.has(field)
                && object.get(field).isJsonPrimitive()
                && object.get(field).getAsString().contains(expectedValue);
    }

    public static final class Secret {
        private final JsonObject raw;

        private Secret(JsonObject raw) {
            this.raw = raw;
        }

        public boolean containsPath(String expectedPath) {
            return GraphQLResponse.containsPath(raw, expectedPath);
        }

        public String id() {
            return stringField(raw, "id");
        }

        public String name() {
            return stringField(raw, "name");
        }

        public String description() {
            return stringField(raw, "description");
        }

        public String type() {
            return stringField(raw, "type");
        }

        public String managedByCyberArk() {
            return stringField(raw, "managedByCyberArk");
        }

        public boolean hasName(String expectedName) {
            return expectedName.equals(name());
        }

        public String toJson() {
            return GSON.toJson(raw);
        }
    }

    public static final class SecretRisk {
        private final JsonObject raw;

        private SecretRisk(JsonObject raw) {
            this.raw = raw;
        }

        public String id() {
            return stringField(raw, "id");
        }

        public String riskLevel() {
            return stringField(raw, "riskLevel");
        }

        public String name() {
            return stringField(raw, "name");
        }

        public String description() {
            return stringField(raw, "description");
        }

        public String lifecycleStatus() {
            return stringField(raw, "lifecycleStatus");
        }

        public String toJson() {
            return GSON.toJson(raw);
        }
    }

    private static String stringField(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsString()
                : "";
    }
}