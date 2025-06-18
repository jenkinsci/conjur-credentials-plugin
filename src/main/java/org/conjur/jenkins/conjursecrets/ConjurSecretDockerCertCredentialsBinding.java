package org.conjur.jenkins.conjursecrets;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.*;

public class ConjurSecretDockerCertCredentialsBinding extends MultiBinding<ConjurSecretDockerCertCredentials> {

    private String clientKeyVariable;
    private String clientCertVariable;
    private String caCertificateVariable;

    @DataBoundConstructor
    public ConjurSecretDockerCertCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        Map<String, String> m = new HashMap<>();
        ConjurSecretDockerCertCredentials credentials = getCredentials(build);

        if (credentials != null) {
            credentials.setContext(build);
            if (credentials.getClientKeySecret() != null) {
                m.put(clientKeyVariable, credentials.getClientKeySecret().getPlainText());
            }
            m.put(clientCertVariable, credentials.getClientCertificate());
            m.put(caCertificateVariable, credentials.getServerCaCertificate());
        }

        return new MultiEnvironment(m);
    }

    @Override
    protected Class<ConjurSecretDockerCertCredentials> type() {
        return ConjurSecretDockerCertCredentials.class;
    }

    public String getClientKeyVariable() {
        return clientKeyVariable;
    }

    @DataBoundSetter
    public void setClientKeyVariable(String clientKeyVariable) {
        this.clientKeyVariable = clientKeyVariable;
    }

    public String getClientCertVariable() {
        return clientCertVariable;
    }

    @DataBoundSetter
    public void setClientCertVariable(String clientCertVariable) {
        this.clientCertVariable = clientCertVariable;
    }

    public String getCaCertificateVariable() {
        return caCertificateVariable;
    }

    @DataBoundSetter
    public void setCaCertificateVariable(String caCertificateVariable) {
        this.caCertificateVariable = caCertificateVariable;
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(clientKeyVariable, clientCertVariable, caCertificateVariable));
    }

    @Symbol("conjurSecretDockerClientCert")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretDockerCertCredentials> {
        private static final String DISPLAY_NAME = "Conjur Secret Docker Certificate credentials";

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        protected Class<ConjurSecretDockerCertCredentials> type() {
            return ConjurSecretDockerCertCredentials.class;
        }
    }
}