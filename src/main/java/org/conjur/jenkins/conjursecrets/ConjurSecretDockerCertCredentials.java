package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.NameWith;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

@NameWith(value = ConjurSecretDockerCertCredentials.NameProvider.class, priority = 32)
public abstract class ConjurSecretDockerCertCredentials extends DockerServerCredentials implements ConjurSecretCredentials {

    protected ConjurSecretDockerCertCredentials(CredentialsScope scope, String id, String description) {
        super(scope, id, description, (Secret) null, null, null);
    }

    public abstract String getClientKeyId();

    public abstract String getClientCertificateId();

    public abstract String getCaCertificateId();

    @Override
    @CheckForNull
    public Secret getClientKeySecret() {
        return ConjurAPI.getSecretFromConjurWithInheritance(getContext(), this, getClientKeyId());
    }

    @Override
    @CheckForNull
    public String getClientCertificate() {
        Secret cert = ConjurAPI.getSecretFromConjurWithInheritance(getContext(), this, getClientCertificateId());
        return cert != null ? cert.getPlainText() : null;
    }

    @Override
    @CheckForNull
    public String getServerCaCertificate() {
        Secret cert = ConjurAPI.getSecretFromConjurWithInheritance(getContext(), this, getCaCertificateId());
        return cert != null ? cert.getPlainText() : null;
    }

    static class NameProvider extends CredentialsNameProvider<ConjurSecretDockerCertCredentials> {
        @NonNull
        @Override
        public String getName(ConjurSecretDockerCertCredentials credentials) {
            String description = Util.fixEmpty(credentials.getDescription());
            return credentials.getDisplayName() + (description == null ? "" : " (" + description + ")");
        }
    }
}