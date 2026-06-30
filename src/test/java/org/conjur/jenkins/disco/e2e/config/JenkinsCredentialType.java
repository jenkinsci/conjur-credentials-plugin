package org.conjur.jenkins.disco.e2e.config;

public enum JenkinsCredentialType {
    STRING("org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl"),
    USERNAME_PASSWORD("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"),
    SSH_USERNAME_PRIVATE_KEY("com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey"),
    SECRET_FILE("org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl"),
    DOCKER_CERT("org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials"),

    CONJUR_SECRET("org.conjur.jenkins.conjursecrets.ConjurSecretCredentialsImpl"),
    CONJUR_STRING("org.conjur.jenkins.conjursecrets.ConjurSecretStringCredentialsImpl"),
    CONJUR_USERNAME_PASSWORD("org.conjur.jenkins.conjursecrets.ConjurSecretUsernameCredentialsImpl"),
    CONJUR_SSH_USERNAME_PRIVATE_KEY("org.conjur.jenkins.conjursecrets.ConjurSecretUsernameSSHKeyCredentialsImpl"),
    CONJUR_SECRET_FILE("org.conjur.jenkins.conjursecrets.ConjurSecretFileCredentialsImpl"),
    CONJUR_DOCKER_CERT("org.conjur.jenkins.conjursecrets.ConjurSecretDockerCertCredentialsImpl");

    private final String type;

    JenkinsCredentialType(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}