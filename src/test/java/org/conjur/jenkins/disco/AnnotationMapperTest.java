package org.conjur.jenkins.disco;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretDockerCertCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretFileCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretStringCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretUsernameCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretUsernameSSHKeyCredentials;
import org.conjur.jenkins.disco.discovery.AnnotationMapper;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationMapperTest {

    // ── ConjurSecretStringCredentials ─────────────────────────────────────────

    @Test
    public void stringCredential_mapsToStringcredentialType() {
        ConjurSecretStringCredentials cred = Mockito.mock(ConjurSecretStringCredentials.class);
        Mockito.when(cred.getId()).thenReturn("my-secret");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void stringCredential_hasValuePlaceholder() {
        ConjurSecretStringCredentials cred = Mockito.mock(ConjurSecretStringCredentials.class);
        Mockito.when(cred.getId()).thenReturn("my-secret");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:value", "{{secret}}");
    }

    @Test
    public void stringCredential_noDoubleMapping() {
        ConjurSecretStringCredentials cred = Mockito.mock(ConjurSecretStringCredentials.class);
        Mockito.when(cred.getId()).thenReturn("my-secret");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).doesNotContainKey("variable:annotation:jenkins_credential_type_alt");
    }

    // ── ConjurSecretUsernameCredentials ───────────────────────────────────────

    @Test
    public void usernamePassword_mapsToUsernamecredentialType() {
        ConjurSecretUsernameCredentials cred = Mockito.mock(ConjurSecretUsernameCredentials.class);
        Mockito.when(cred.getId()).thenReturn("up-cred");
        Mockito.when(cred.getUsername()).thenReturn("alice");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_USERNAME);
    }

    @Test
    public void usernamePassword_includesUsernameAnnotation() {
        ConjurSecretUsernameCredentials cred = Mockito.mock(ConjurSecretUsernameCredentials.class);
        Mockito.when(cred.getId()).thenReturn("up-cred");
        Mockito.when(cred.getUsername()).thenReturn("alice");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_username", "{{username}}");
    }

    @Test
    public void usernamePassword_hasDoubleMapping() {
        ConjurSecretUsernameCredentials cred = Mockito.mock(ConjurSecretUsernameCredentials.class);
        Mockito.when(cred.getId()).thenReturn("up-cred");
        Mockito.when(cred.getUsername()).thenReturn("alice");

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
    public void unknownCredential_mapReturnsEmptyMap() {
        StandardCredentials cred = Mockito.mock(StandardCredentials.class);
        Mockito.when(cred.getId()).thenReturn("generic-cred");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).isEmpty();
    }

    // ── getCredentialType ─────────────────────────────────────────────────────

    @Test
    public void getCredentialType_usernameCredential() {
        ConjurSecretUsernameCredentials cred = Mockito.mock(ConjurSecretUsernameCredentials.class);
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_USERNAME);
    }

    @Test
    public void getCredentialType_stringCredential() {
        ConjurSecretStringCredentials cred = Mockito.mock(ConjurSecretStringCredentials.class);
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_STRING);
    }

    // ── ConjurSecretUsernameSSHKeyCredentials ─────────────────────────────────

    @Test
    public void sshKey_mapsToUsernamesshkeycredentialType() {
        ConjurSecretUsernameSSHKeyCredentials cred = Mockito.mock(ConjurSecretUsernameSSHKeyCredentials.class);
        Mockito.when(cred.getId()).thenReturn("ssh-key");
        Mockito.when(cred.getUsername()).thenReturn("git");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_SSH_KEY);
    }

    @Test
    public void sshKey_includesUsernameAnnotation() {
        ConjurSecretUsernameSSHKeyCredentials cred = Mockito.mock(ConjurSecretUsernameSSHKeyCredentials.class);
        Mockito.when(cred.getId()).thenReturn("ssh-key");
        Mockito.when(cred.getUsername()).thenReturn("git");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_username", "{{username}}");
    }

    @Test
    public void sshKey_hasDoubleMapping() {
        ConjurSecretUsernameSSHKeyCredentials cred = Mockito.mock(ConjurSecretUsernameSSHKeyCredentials.class);
        Mockito.when(cred.getId()).thenReturn("ssh-key");
        Mockito.when(cred.getUsername()).thenReturn("git");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void sshKey_valueKeyIsPassphrase() {
        ConjurSecretUsernameSSHKeyCredentials cred = Mockito.mock(ConjurSecretUsernameSSHKeyCredentials.class);
        Mockito.when(cred.getId()).thenReturn("ssh-key");
        Mockito.when(cred.getUsername()).thenReturn("git");

        Map<String, String> result = AnnotationMapper.map(cred);

        assertThat(result).containsEntry("variable:value", "{{passphrase}}");
    }

    @Test
    public void getCredentialType_sshKey() {
        ConjurSecretUsernameSSHKeyCredentials cred = Mockito.mock(ConjurSecretUsernameSSHKeyCredentials.class);
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_SSH_KEY);
    }

    // ── ConjurSecretFileCredentials ───────────────────────────────────────────

    @Test
    public void fileCredential_mapsToFilecredentialType() {
        ConjurSecretFileCredentials fileCred = Mockito.mock(ConjurSecretFileCredentials.class);
        Mockito.when(fileCred.getId()).thenReturn("file-cred");

        Map<String, String> result = AnnotationMapper.map(fileCred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_SECRET_FILE);
        assertThat(result).containsEntry("variable:value", "{{content}}");
        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void getCredentialType_fileCredential() {
        ConjurSecretFileCredentials cred = Mockito.mock(ConjurSecretFileCredentials.class);
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_SECRET_FILE);
    }

    // ── ConjurSecretDockerCertCredentials ─────────────────────────────────────

    @Test
    public void dockerCert_mapsToDockerCertCredentialType() {
        ConjurSecretDockerCertCredentials dockerCred = Mockito.mock(ConjurSecretDockerCertCredentials.class);
        Mockito.when(dockerCred.getId()).thenReturn("docker-cert-id");

        Map<String, String> result = AnnotationMapper.map(dockerCred);

        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type", AnnotationMapper.TYPE_DOCKER_CERT);
        assertThat(result).containsKey("variable:value:key");
        assertThat(result).containsKey("variable:value:cert");
        assertThat(result).containsKey("variable:value:ca");
        assertThat(result).containsEntry("variable:annotation:jenkins_credential_type_alt", AnnotationMapper.TYPE_STRING);
    }

    @Test
    public void dockerCert_keyValueContainsCredentialId() {
        ConjurSecretDockerCertCredentials dockerCred = Mockito.mock(ConjurSecretDockerCertCredentials.class);
        Mockito.when(dockerCred.getId()).thenReturn("docker-cert-id");

        Map<String, String> result = AnnotationMapper.map(dockerCred);

        assertThat(result.get("variable:value:key")).isEqualTo("docker-cert-id/key");
        assertThat(result.get("variable:value:cert")).isEqualTo("docker-cert-id/cert");
        assertThat(result.get("variable:value:ca")).isEqualTo("docker-cert-id/ca");
    }

    @Test
    public void getCredentialType_dockerCert() {
        ConjurSecretDockerCertCredentials cred = Mockito.mock(ConjurSecretDockerCertCredentials.class);
        assertThat(AnnotationMapper.getCredentialType(cred)).isEqualTo(AnnotationMapper.TYPE_DOCKER_CERT);
    }
}
