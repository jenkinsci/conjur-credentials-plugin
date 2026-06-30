package org.conjur.jenkins.disco.model;

import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;

public class DiscoExporterConfigurationSnapshot {

    private String subdomain;
    private String authMode;
    private String conjurCredentialId;
    private String discoUsernameCredentialId;
    private String discoPasswordCredentialId;
    private int exportIntervalHours;
    private boolean exportSecretValues;
    private String discoveryBaseUrl;
    private boolean testEnvironment;

    public DiscoExporterConfigurationSnapshot() {}

    public static DiscoExporterConfigurationSnapshot from(DiscoExporterConfiguration cfg) {
        if (cfg == null) return null;
        DiscoExporterConfigurationSnapshot s = new DiscoExporterConfigurationSnapshot();
        s.subdomain = cfg.getSubdomain();
        s.authMode = cfg.getAuthMode() != null ? cfg.getAuthMode().name() : null;
        s.conjurCredentialId = cfg.getConjurCredentialId();
        s.discoUsernameCredentialId = cfg.getDiscoUsernameCredentialId();
        s.discoPasswordCredentialId = cfg.getDiscoPasswordCredentialId();
        s.exportIntervalHours = cfg.getExportIntervalHours();
        s.exportSecretValues = cfg.isExportSecretValues();
        s.discoveryBaseUrl = cfg.getPlatformDiscoveryUrl();
        s.testEnvironment = cfg.isTestEnvironment();
        return s;
    }

    public String getSubdomain() { return subdomain; }
    public String getAuthMode() { return authMode; }
    public String getConjurCredentialId() { return conjurCredentialId; }
    public String getDiscoUsernameCredentialId() { return discoUsernameCredentialId; }
    public String getDiscoPasswordCredentialId() { return discoPasswordCredentialId; }
    public int getExportIntervalHours() { return exportIntervalHours; }
    public boolean isExportSecretValues() { return exportSecretValues; }
    public String getDiscoveryBaseUrl() { return discoveryBaseUrl; }
    public boolean isTestEnvironment() { return testEnvironment; }
}
