package org.conjur.jenkins.disco.model;

import java.util.ArrayList;
import java.util.List;

public class DiscoverySnapshot {
    private String jenkinsId;
    private String originStoreId;
    private String dataSourceType;
    private String version;
    private String snapshotId;
    private String timestamp;
    private String kid;
    private OpenIdConfiguration openIdConfiguration;
    private DiscoExporterConfigurationSnapshot disCoConfig;
    private GlobalConjurConfigurationSnapshot conjurConfig;
    private List<CredentialRecord> credentials = new ArrayList<>();
    private List<JenkinsObject> folders = new ArrayList<>();
    private List<JenkinsObject> jobs = new ArrayList<>();

    public String getJenkinsId() { return jenkinsId; }
    public void setJenkinsId(String jenkinsId) { this.jenkinsId = jenkinsId; }

    public String getOriginStoreId() { return originStoreId; }
    public void setOriginStoreId(String originStoreId) { this.originStoreId = originStoreId; }

    public String getDataSourceType() { return dataSourceType; }
    public void setDataSourceType(String dataSourceType) { this.dataSourceType = dataSourceType; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public DiscoExporterConfigurationSnapshot getDisCoConfig() { return disCoConfig; }
    public void setDisCoConfig(DiscoExporterConfigurationSnapshot disCoConfig) { this.disCoConfig = disCoConfig; }

    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }

    public OpenIdConfiguration getOpenIdConfiguration() { return openIdConfiguration; }
    public void setOpenIdConfiguration(OpenIdConfiguration openIdConfiguration) { this.openIdConfiguration = openIdConfiguration; }

    public GlobalConjurConfigurationSnapshot getConjurConfig() { return conjurConfig; }
    public void setConjurConfig(GlobalConjurConfigurationSnapshot conjurConfig) { this.conjurConfig = conjurConfig; }

    public List<CredentialRecord> getCredentials() { return credentials; }
    public void setCredentials(List<CredentialRecord> credentials) { this.credentials = credentials; }

    public List<JenkinsObject> getFolders() { return folders; }
    public void setFolders(List<JenkinsObject> folders) { this.folders = folders; }

    public List<JenkinsObject> getJobs() { return jobs; }
    public void setJobs(List<JenkinsObject> jobs) { this.jobs = jobs; }
}
