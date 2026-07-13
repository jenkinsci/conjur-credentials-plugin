package org.conjur.jenkins.disco;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.conjur.jenkins.disco.discovery.AnnotationMapper;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationMapperTest {

    // ── StringCredentialsImpl ─────────────────────────────────────────────────

    @Test
    public void stringCredential_mapsToStringcredentialType() {
        StringCredentialsImpl cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, "my-secret", "desc", Secret.fromString("val"));

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void stringCredential_hasValuePlaceholder() {
        StringCredentialsImpl cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, "my-secret", "desc", Secret.fromString("val"));

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:value", "{{secret}}");
    }

    @Test
    public void stringCredential_noDoubleMapping() {
        StringCredentialsImpl cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, "my-secret", "desc", Secret.fromString("val"));

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).doesNotContainKey("variable:annotation:jenkins_credential_type_alt");
    }

    // ── UsernamePasswordCredentialsImpl ───────────────────────────────────────

    @Test
    public void usernamePassword_mapsToUsernamecredentialType() throws Descriptor.FormException {
        UsernamePasswordCredentialsImpl cred = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "up-cred", "desc", "alice", "pass");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_USERNAME);
    }

    @Test
    public void usernamePassword_includesUsernameAnnotation() throws Descriptor.FormException {
        UsernamePasswordCredentialsImpl cred = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "up-cred", "desc", "alice", "pass");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_username", "{{username}}");
    }

    @Test
    public void usernamePassword_hasDoubleMapping() throws Descriptor.FormException {
        UsernamePasswordCredentialsImpl cred = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "up-cred", "desc", "alice", "pass");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    // ── Unknown / generic credential ──────────────────────────────────────────

    @Test
    public void unknownCredential_defaultsToStringcredentialType() {
        StandardCredentials cred = Mockito.mock(StandardCredentials.class);
        Mockito.when(cred.getId()).thenReturn("generic-cred");

        String type = AnnotationMapper.getCredentialType(cred);

        assertThat(type).isEqualTo(AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void unknownCredential_mapReturnsStringcredentialFallback() {
        StringCredentialsImpl cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, "generic-cred", "desc", Secret.fromString("x"));

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_STRING);
        assertThat(result).containsEntry("variable:value", "{{secret}}");
    }

    // ── getCredentialType ─────────────────────────────────────────────────────

    @Test
    public void getCredentialType_usernameCredential() throws Descriptor.FormException {
        UsernamePasswordCredentialsImpl cred = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "up", "desc", "user", "pass");
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_USERNAME);
    }

    @Test
    public void getCredentialType_stringCredential() {
        StringCredentialsImpl cred = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, "str", "desc", Secret.fromString("v"));
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_STRING);
    }

    // ── BasicSSHUserPrivateKey ────────────────────────────────────────────────

    @Test
    public void sshKey_mapsToUsernamesshkeycredentialType() {
        BasicSSHUserPrivateKey cred = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "ssh-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("key"), "", "desc");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_SSH_KEY);
    }

    @Test
    public void sshKey_includesUsernameAnnotation() {
        BasicSSHUserPrivateKey cred = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "ssh-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("key"), "", "desc");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_username", "{{username}}");
    }

    @Test
    public void sshKey_hasDoubleMapping() {
        BasicSSHUserPrivateKey cred = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "ssh-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("key"), "", "desc");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void sshKey_valueKeyIsPassphrase() {
        BasicSSHUserPrivateKey cred = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "ssh-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("key"), "", "desc");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:value", "{{passphrase}}");
    }

    @Test
    public void getCredentialType_sshKey() {
        BasicSSHUserPrivateKey cred = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL, "ssh-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("key"), "", "desc");
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_SSH_KEY);
    }

    // ── FileCredentialsImpl ───────────────────────────────────────────────────

    @Test
    public void fileCredential_mapsToFilecredentialType() {
        FileCredentialsImpl fileCred = new FileCredentialsImpl(
                CredentialsScope.GLOBAL, "file-cred", "desc", "file.txt",
                SecretBytes.fromBytes(new byte[0]));

        Map<String, String> result = AnnotationMapper.map(fileCred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_SECRET_FILE);
        assertThat(result).containsEntry("variable:value", "{{content}}");
        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void getCredentialType_fileCredential() {
        FileCredentialsImpl cred = new FileCredentialsImpl(
                CredentialsScope.GLOBAL, "file-cred", "desc", "file.txt",
                SecretBytes.fromBytes(new byte[0]));
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_SECRET_FILE);
    }

    // ── DockerServerCredentials ───────────────────────────────────────────────

    @Test
    public void dockerCert_mapsToDockerCertCredentialType() {
        DockerServerCredentials dockerCred = new DockerServerCredentials(
                CredentialsScope.GLOBAL, "docker-cert-id", "desc", (Secret) null, "", "");

        Map<String, String> result = AnnotationMapper.map(dockerCred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_DOCKER_CERT);
        assertThat(result).containsKey("variable:value:key");
        assertThat(result).containsKey("variable:value:cert");
        assertThat(result).containsKey("variable:value:ca");
        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void dockerCert_keyValueContainsCredentialId() {
        DockerServerCredentials dockerCred = new DockerServerCredentials(
                CredentialsScope.GLOBAL, "docker-cert-id", "desc", (Secret) null, "", "");

        Map<String, String> result = AnnotationMapper.map(dockerCred);

        assertThat(result.get("variable:value:key")).isEqualTo("docker-cert-id/key");
        assertThat(result.get("variable:value:cert")).isEqualTo("docker-cert-id/cert");
        assertThat(result.get("variable:value:ca")).isEqualTo("docker-cert-id/ca");
    }

    @Test
    public void getCredentialType_dockerCert() {
        DockerServerCredentials cred = new DockerServerCredentials(
                CredentialsScope.GLOBAL, "docker-cert-id", "desc", (Secret) null, "", "");
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_DOCKER_CERT);
    }
}
