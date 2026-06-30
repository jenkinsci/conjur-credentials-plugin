package org.conjur.jenkins.disco.model;

import org.conjur.jenkins.configuration.ConjurConfiguration;

public class JenkinsObject {
    private String path;
    private String description;
    private String scmUrl;
    private String type;
    private String jenkins_pronoun;
    private String lastBuildTs;
    private String inheritancePath;
    private String sub;
    private ConjurConfiguration conjurConfiguration;

    public JenkinsObject(String path, String description, String scmUrl,
                         String type, String jenkins_pronoun, String lastBuildTs) {
        this.path = path;
        this.description = description;
        this.scmUrl = scmUrl;
        this.type = type;
        this.jenkins_pronoun = jenkins_pronoun;
        this.lastBuildTs = lastBuildTs;
    }

    public String getPath() { return path; }
    public String getDescription() { return description; }
    public String getScmUrl() { return scmUrl; }
    public String getType() { return type; }
    public String getJenkins_pronoun() { return jenkins_pronoun; }
    public String getLastBuildTs() { return lastBuildTs; }

    public String getInheritancePath() { return inheritancePath; }
    public void setInheritancePath(String inheritancePath) { this.inheritancePath = inheritancePath; }

    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    public ConjurConfiguration getConjurConfiguration() { return conjurConfiguration; }
    public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) { this.conjurConfiguration = conjurConfiguration; }
}
