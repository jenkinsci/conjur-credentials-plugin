package org.conjur.jenkins.disco.e2e.config;

public enum OnboardSecretConfiguration {
    SERVICE_ID("Service ID:"),
    CONJUR_ACCOUNT("Secrets Manager Account:"),
    SECRETS_MANAGER_APPLIANCE_URL("Secrets Manager Appliance URL:");

    public final String label;

    OnboardSecretConfiguration(String label) {
        this.label = label;
    }
}