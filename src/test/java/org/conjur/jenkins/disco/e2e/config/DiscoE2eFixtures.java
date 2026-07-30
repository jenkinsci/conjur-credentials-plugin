package org.conjur.jenkins.disco.e2e.config;

import java.nio.file.Path;

public class DiscoE2eFixtures {
    public static final Path GLOBAL_SECRET_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/disco-global-secret-credential.xml");
    public static final Path GLOBAL_CONJUR_SECRET_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/disco-global-conjur-secret-credential.xml");
    public static final Path GLOBAL_CONJUR_JWT_CONFIGURATION_XML = Path.of(
            "scripts/templates/globalconjurconfiguration-jwt.xml");
    public static final Path RELOAD_GLOBAL_CONJUR_CONFIGURATION_GROOVY = Path.of(
            "scripts/templates/reload-globalconjurconfiguration.groovy");
    public static final Path APPLY_GLOBAL_CONJUR_JWT_CONFIGURATION_GROOVY = Path.of(
            "scripts/templates/disco/apply-globalconjurconfiguration-jwt.groovy");
    public static final Path ROOT_FOLDER_WITH_STRING_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/config.xml");
    public static final Path ROOT_FOLDER_WITHOUT_CREDENTIALS_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder-without-credentials/config.xml");
    public static final Path FOLDER_WITH_CONJUR_PIPELINE_CREDENTIAL_XML = Path.of(
            "scripts/templates/disco/jobs/disco-conjur-pipeline-folder/config.xml");
    public static final Path CONJUR_SECRET_PIPELINE_XML = Path.of(
            "scripts/templates/disco/jobs/disco-conjur-pipeline-folder/jobs/disco-conjur-secret-pipeline/config.xml");
    public static final Path TEAM_ALPHA_FOLDER_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/config.xml");
    public static final Path AUDIT_FOLDER_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/jobs/audit/config.xml");
    public static final Path AUDIT_PIPELINE_XML = Path.of(
            "scripts/templates/disco/jobs/disco-folder/jobs/team-alpha/jobs/audit/jobs/disco-alpha-audit-pipeline/config.xml");
    public static final String HIGH_RISK_LEVEL = "HIGH";
    public static final String MEDIUM_RISK_LEVEL = "MEDIUM";
}
