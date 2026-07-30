package org.conjur.jenkins.disco.e2e.steps;
import okhttp3.Request;
import okhttp3.Response;
import org.conjur.jenkins.disco.e2e.config.DiscoE2eConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.APPLY_GLOBAL_CONJUR_JWT_CONFIGURATION_GROOVY;
import static org.conjur.jenkins.disco.e2e.config.DiscoE2eFixtures.RELOAD_GLOBAL_CONJUR_CONFIGURATION_GROOVY;
import static org.junit.Assert.fail;

public final class JenkinsSteps {

    private final DiscoE2eConfig config;
    private final Logger log = Logger.getLogger(getClass().getName());

    public JenkinsSteps(DiscoE2eConfig config) {
        this.config = config;
    }

    public void createFromXml(
            Path xmlTemplate,
            List<String> cliArgs,
            Map<String, String> replacements) throws Exception {

        if (!Files.exists(xmlTemplate)) {
            fail("Jenkins XML template does not exist: " + xmlTemplate.toAbsolutePath());
        }

        String xml = Files.readString(xmlTemplate, StandardCharsets.UTF_8);
        if (xml.isBlank()) {
            fail("Jenkins XML template is empty: " + xmlTemplate.toAbsolutePath());
        }

        for (String token : replacements.keySet()) {
            if (!xml.contains(token)) {
                fail("Jenkins XML template " + xmlTemplate.toAbsolutePath()
                        + " does not contain replacement token: " + token);
            }
        }

        var orderedReplacements = new ArrayList<>(replacements.entrySet());
        orderedReplacements.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
        for (Map.Entry<String, String> replacement : orderedReplacements) {
            String token = replacement.getKey();
            xml = xml.replace(token, replacement.getValue());
        }

        var output = runJenkinsCli(cliArgs, xml.getBytes(StandardCharsets.UTF_8));
        log.info("Created Jenkins object from XML template: " + xmlTemplate.toAbsolutePath() + "\n" + output);
    }

    public void runJenkinsGroovy(Path groovyScript) throws Exception {
        if (!Files.exists(groovyScript)) {
            fail("Jenkins Groovy script does not exist: " + groovyScript.toAbsolutePath());
        }

        var script = Files.readString(groovyScript, StandardCharsets.UTF_8);
        if (script.isBlank()) {
            fail("Jenkins Groovy script is empty: " + groovyScript.toAbsolutePath());
        }

        log.info("Running Jenkins Groovy script: " + groovyScript.toAbsolutePath());
        var output = runJenkinsCli(List.of("groovy", "="), script.getBytes(StandardCharsets.UTF_8));
        log.info("Jenkins CLI Groovy output:\n" + output);
    }

    public void runJenkinsBuild(String jobFullName) throws Exception {
        var output = runJenkinsCli(List.of("build", jobFullName, "-s", "-v"), new byte[0]);
        log.info("Jenkins build completed for job: " + jobFullName + "\n" + output);
    }

    public void runJenkinsBuildWithRetries(String jobFullName, int maxAttempts, long waitBetweenAttemptsMs) throws Exception {
        Throwable lastFailure = null;
        /*
         When a secret is onboarded from an external source (in our case Conjur), there is a propagation delay
         before the secret becomes available in Jenkins. We cannot directly query Jenkins CLI to verify
         the secret presence due to Jenkins CLI limitation (credentials from external sources are not listed).
         Instead, we use the build itself as a confirmation mechanism: if the build succeeds, it means the pipelines
         withCredentials binding worked, which proves the secret is available in Jenkins.
         */
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                runJenkinsBuild(jobFullName);
                if (attempt > 1) {
                    log.info("Jenkins build succeeded on retry attempt " + attempt + " for job: " + jobFullName);
                }
                return;
            } catch (Throwable t) {
                lastFailure = t;
                if (attempt == maxAttempts) {
                    break;
                }
                log.warning("Jenkins build attempt " + attempt + " failed for job '" + jobFullName
                        + "'. Retrying in " + waitBetweenAttemptsMs + " ms. Error: " + t.getMessage());
                Thread.sleep(waitBetweenAttemptsMs);
            }
        }

        fail("Jenkins build did not succeed after " + maxAttempts + " attempts for job '" + jobFullName
                + "'. Last error: " + (lastFailure == null ? "unknown" : lastFailure.getMessage()));
    }

    public String getLastBuildArtifact(String jobFullName, String artifactFileName) throws Exception {
        // Use Jenkins REST API to fetch the archived artifact from the last build
        var url = config.joinUrl(config.jenkinsUrl(),
                "job/" + jobFullName.replace("/", "/job/") + "/lastBuild/artifact/" + artifactFileName);
        var requestBuilder = new Request.Builder().url(url).get();
        if (config.isJenkinsCliAuthConfigured()) {
            var parts = config.jenkinsCliAuth().split(":", 2);
            requestBuilder.header("Authorization", okhttp3.Credentials.basic(parts[0], parts[1]));
        }
        try (var response = config.httpClient().newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch artifact '" + artifactFileName + "' for job '"
                        + jobFullName + "': HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public void applyGlobalConjurJwtConfig(String applianceUrl, String conjurAccount, String serviceId) throws Exception {
        createFromXml(
                APPLY_GLOBAL_CONJUR_JWT_CONFIGURATION_GROOVY,
                List.of("groovy", "="),
                Map.of(
                        "{{CONJUR_APPLIANCE_URL}}", applianceUrl,
                        "{{CONJUR_ACCOUNT}}", conjurAccount,
                        "{{CONJUR_SERVICE_ID}}", serviceId
                ));
        runJenkinsGroovy(RELOAD_GLOBAL_CONJUR_CONFIGURATION_GROOVY);
    }

    private String runJenkinsCli(List<String> cliArgs, byte[] stdinBytes) throws Exception {
        var cliJar = downloadJenkinsCliJarIfMissing();
        var command = new ArrayList<>(List.of(
                config.javaExecutable(),
                "-jar",
                cliJar.toString(),
                "-s",
                config.jenkinsUrl()));
        if (config.isJenkinsCliAuthConfigured()) {
            command.add("-auth");
            command.add(config.jenkinsCliAuth());
        }
        command.addAll(cliArgs);

        var outputFile = Files.createTempFile("jenkins-cli-groovy-", ".log");
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(stdinBytes);
        }

        var completed = process.waitFor(DiscoE2eConfig.JENKINS_CLI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }

        var output = Files.readString(outputFile, StandardCharsets.UTF_8);
        Files.deleteIfExists(outputFile);
        if (!completed) {
            fail("Timed out running Jenkins CLI command. Output:\n" + output);
        }
        if (process.exitValue() != 0) {
            fail("Jenkins CLI command failed with exit " + process.exitValue()
                    + "\nCLI args: " + cliArgs
                    + "\nJenkins URL: " + config.jenkinsUrl()
                    + "\nPossible causes: invalid JENKINS_CLI_AUTH, missing Overall/Read or Job/Create permission, "
                    + "or create-job was called for an item that already exists."
                    + "\nOutput:\n" + output);
        }
        return output;
    }

    private Path downloadJenkinsCliJarIfMissing() throws IOException {
        var cliJar = Path.of(config.jenkinsCliJarPath());
        if (Files.exists(cliJar)) {
            return cliJar;
        }

        var parent = cliJar.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var request = new Request.Builder()
                .url(config.joinUrl(config.jenkinsUrl(), "jnlpJars/jenkins-cli.jar"))
                .get()
                .build();
        try (Response response = config.httpClient().newCall(request).execute()) {
            var responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            if (!response.isSuccessful()) {
                throw new IOException("Could not download Jenkins CLI from " + config.jenkinsUrl()
                        + ": HTTP " + response.code() + " " + new String(responseBody, StandardCharsets.UTF_8));
            }
            Files.write(cliJar, responseBody);
        }
        return cliJar;
    }
}
