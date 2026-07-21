package org.conjur.jenkins.disco.e2e.config;

public enum OnboardSecretConfiguration {
    SERVICE_ID("Service ID:"),
    CONJUR_ACCOUNT("Conjur Account:"),
    CONJUR_APPLIANCE_URL("Conjur Appliance URL:");

    public final String label;

    OnboardSecretConfiguration(String label) {
        this.label = label;
    }
}