package org.conjur.jenkins.disco.discovery;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a Jenkins credential instance into a conjurization block and
 * determines the jenkins_credential_type annotation.
 * <p>
 * Double-mapping rule: every non-stringcredential type also generates a
 * stringcredential entry (per the LLD contract).
 */
public class AnnotationMapper {

    public static final String TYPE_USERNAME = "usernamecredential";
    public static final String TYPE_SSH_KEY = "usernamesshkeycredential";
    public static final String TYPE_SECRET_FILE = "filecredential";
    public static final String TYPE_DOCKER_CERT = "dockercertcredential";
    public static final String TYPE_STRING = "stringcredential";

    private AnnotationMapper() {
    }

    public static Map<String, String> map(StandardCredentials cred) {
        Map<String, String> conjurization = new LinkedHashMap<>();

        if (cred.getClass().equals(UsernamePasswordCredentialsImpl.class)) {
            conjurization.put("variable:annotation:jenkins_credential_type", TYPE_USERNAME);
            conjurization.put("variable:annotation:jenkins_credential_username", "{{username}}");
            conjurization.put("variable:value", "{{password}}");
            conjurization.put("variable:annotation:jenkins_credential_type_alt", TYPE_STRING);
        } else if (cred.getClass().equals(BasicSSHUserPrivateKey.class)) {
            conjurization.put("variable:annotation:jenkins_credential_type", TYPE_SSH_KEY);
            conjurization.put("variable:annotation:jenkins_credential_username", "{{username}}");
            conjurization.put("variable:value", "{{passphrase}}");
            conjurization.put("variable:annotation:jenkins_credential_type_alt", TYPE_STRING);
        } else if (cred.getClass().equals(FileCredentialsImpl.class)) {
            conjurization.put("variable:annotation:jenkins_credential_type", TYPE_SECRET_FILE);
            conjurization.put("variable:value", "{{content}}");
            conjurization.put("variable:annotation:jenkins_credential_type_alt", TYPE_STRING);
        } else if (cred.getClass().equals(DockerServerCredentials.class)) {
            String id = cred.getId();
            conjurization.put("variable:annotation:jenkins_credential_type", TYPE_DOCKER_CERT);
            conjurization.put("variable:value:key", id + "/key");
            conjurization.put("variable:value:cert", id + "/cert");
            conjurization.put("variable:value:ca", id + "/ca");
            conjurization.put("variable:annotation:jenkins_credential_type_alt", TYPE_STRING);
        } else if (cred.getClass().equals(StringCredentialsImpl.class)) {
            conjurization.put("variable:annotation:jenkins_credential_type", TYPE_STRING);
            conjurization.put("variable:value", "{{secret}}");
        }

        return conjurization;
    }

    /**
     * Returns the primary jenkins_credential_type string for a given credential.
     */
    public static String getCredentialType(StandardCredentials cred) {
        if (cred.getClass().equals(UsernamePasswordCredentialsImpl.class)) return TYPE_USERNAME;
        if (cred.getClass().equals(BasicSSHUserPrivateKey.class)) return TYPE_SSH_KEY;
        if (cred.getClass().equals(FileCredentialsImpl.class)) return TYPE_SECRET_FILE;
        if (cred.getClass().equals(DockerServerCredentials.class)) return TYPE_DOCKER_CERT;
        if (cred.getClass().equals(StringCredentialsImpl.class)) return TYPE_STRING;
        return TYPE_STRING;
    }
}