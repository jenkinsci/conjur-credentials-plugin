package org.conjur.jenkins.disco.e2e.config;

import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class DiscoE2eConfig {

    private static final String TENANT_ID = "420183a1-b3bb-4125-b851-ff54d9d9bbc2";
    private static final String IDENTITY_URL = "https://ave5859.id.integration-cyberark.cloud";
    private static final String GRAPHQL_URL = "https://57s7k26zx9x54w-discoverycontext.integration-cyberark.cloud/api/graphql";
    private static final String USERNAME = "itso@cyberark.cloud.712204";
    private static final String PASSWORD = "t3stP@ss";

    private final String runId;
    private final long graphQlWaitTimeoutMs;
    private final long graphQlWaitIntervalMs;
    private final long jenkinsCliTimeoutMs;

    public DiscoE2eConfig() {
        this.runId = sanitizeForJenkinsId(getConfig("DISCO_E2E_RUN_ID", "run-" + System.currentTimeMillis()));
        this.graphQlWaitTimeoutMs = TimeUnit.SECONDS.toMillis(Long.parseLong(
                getConfig("DISCO_GRAPHQL_WAIT_TIMEOUT_SECONDS", "180")));
        this.graphQlWaitIntervalMs = TimeUnit.SECONDS.toMillis(Long.parseLong(
                getConfig("DISCO_GRAPHQL_WAIT_INTERVAL_SECONDS", "10")));
        this.jenkinsCliTimeoutMs = TimeUnit.SECONDS.toMillis(Long.parseLong(
                getConfig("JENKINS_CLI_TIMEOUT_SECONDS", "300")));
    }

    public OkHttpClient httpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String runId() {
        return runId;
    }

    public long graphQlWaitTimeoutMs() {
        return graphQlWaitTimeoutMs;
    }

    public long graphQlWaitIntervalMs() {
        return graphQlWaitIntervalMs;
    }

    public long jenkinsCliTimeoutMs() {
        return jenkinsCliTimeoutMs;
    }

    public String tenantId() {
        return getConfig("DISCO_TENANT_ID", TENANT_ID);
    }

    public String identityUrl() {
        return getConfig("DISCO_IDENTITY_URL", IDENTITY_URL);
    }

    public String graphQlUrl() {
        return getConfig("DISCO_GRAPHQL_URL", GRAPHQL_URL);
    }

    public String identityUsername() {
        return getConfig("DISCO_USERNAME", USERNAME);
    }

    public String identityPassword() {
        return getConfig("DISCO_PASSWORD", PASSWORD);
    }

    public String jenkinsUrl() {
        return getConfig("DISCO_E2E_JENKINS_URL", "http://localhost:8080");
    }

    public String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    public String triggerGroovyScriptPath() {
        String scriptPath = getConfig("DISCO_E2E_TRIGGER_GROOVY_SCRIPT");
        if (!scriptPath.isBlank()) {
            return scriptPath;
        }
        return getConfig("DISCO_E2E_TRIGGER_GROOVY", "scripts/templates/disco/run-disco-discovery.groovy");
    }

    public String jenkinsCliJarPath() {
        return getConfig("JENKINS_CLI_JAR", "target/disco-e2e/jenkins-cli.jar");
    }

    public boolean isLiveGraphQlEnabled() {
        return "true".equalsIgnoreCase(getConfig("DISCO_GRAPHQL_RUN"));
    }

    public boolean isJenkinsCliAuthConfigured() {
        return !getConfig("JENKINS_CLI_AUTH").isBlank();
    }

    public String jenkinsCliAuth() {
        return getConfig("JENKINS_CLI_AUTH");
    }

    public String joinUrl(String baseUrl, String path) {
        return baseUrl.endsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    public static String getConfig(String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(key);
        return value != null ? value : "";
    }

    public static String getConfig(String key, String defaultValue) {
        String value = getConfig(key);
        return value.isBlank() ? defaultValue : value;
    }

    private static String sanitizeForJenkinsId(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "-");
        return sanitized.isBlank() ? "run-unknown" : sanitized;
    }
}