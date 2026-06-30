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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

        List<Map.Entry<String, String>> orderedReplacements = new ArrayList<>(replacements.entrySet());
        orderedReplacements.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
        for (Map.Entry<String, String> replacement : orderedReplacements) {
            String token = replacement.getKey();
            xml = xml.replace(token, replacement.getValue());
        }

        String output = runJenkinsCli(cliArgs, xml.getBytes(StandardCharsets.UTF_8));
        log.info("Created Jenkins object from XML template: " + xmlTemplate.toAbsolutePath() + "\n" + output);
    }

    public void runJenkinsGroovy(Path groovyScript) throws Exception {
        if (!Files.exists(groovyScript)) {
            fail("Jenkins Groovy script does not exist: " + groovyScript.toAbsolutePath());
        }

        String script = Files.readString(groovyScript, StandardCharsets.UTF_8);
        if (script.isBlank()) {
            fail("Jenkins Groovy script is empty: " + groovyScript.toAbsolutePath());
        }

        log.info("Running Jenkins Groovy script: " + groovyScript.toAbsolutePath());
        String output = runJenkinsCli(List.of("groovy", "="), script.getBytes(StandardCharsets.UTF_8));
        log.info("Jenkins CLI Groovy output:\n" + output);
    }

    public void runJenkinsBuild(String jobFullName) throws Exception {
        String output = runJenkinsCli(List.of("build", jobFullName, "-s", "-v"), new byte[0]);
        log.info("Jenkins build completed for job: " + jobFullName + "\n" + output);
    }

    private String runJenkinsCli(List<String> cliArgs, byte[] stdinBytes) throws Exception {
        Path cliJar = downloadJenkinsCliJarIfMissing();
        List<String> command = new ArrayList<>(List.of(
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

        Path outputFile = Files.createTempFile("jenkins-cli-groovy-", ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(stdinBytes);
        }

        boolean completed = process.waitFor(config.jenkinsCliTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }

        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
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
        Path cliJar = Path.of(config.jenkinsCliJarPath());
        if (Files.exists(cliJar)) {
            return cliJar;
        }

        Path parent = cliJar.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Request request = new Request.Builder()
                .url(config.joinUrl(config.jenkinsUrl(), "jnlpJars/jenkins-cli.jar"))
                .get()
                .build();
        try (Response response = config.httpClient().newCall(request).execute()) {
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            if (!response.isSuccessful()) {
                throw new IOException("Could not download Jenkins CLI from " + config.jenkinsUrl()
                        + ": HTTP " + response.code() + " " + new String(responseBody, StandardCharsets.UTF_8));
            }
            Files.write(cliJar, responseBody);
        }
        return cliJar;
    }
}