package org.conjur.jenkins.conjursecrets;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline binding that exposes a Conjur Docker TLS certificate set (client key,
 * client cert, CA cert) as environment variables, without requiring paths to be
 * pre-configured in Jenkins.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectDockerCert(
 *       credentialsId: 'path/to/docker-cert',
 *       clientKeyVariable: 'DOCKER_CLIENT_KEY',
 *       clientCertVariable: 'DOCKER_CLIENT_CERT',
 *       caCertVariable: 'DOCKER_CA_CERT')]) {
 *       sh 'docker --tls info'
 *   }
 * </pre>
 */
public class ConjurDirectDockerCertCredentialsBinding extends MultiBinding<ConjurSecretDockerCertCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectDockerCertCredentialsBinding.class.getName());

    private String clientKeyVariable;
    private String clientCertVariable;
    private String caCertVariable;

    @DataBoundConstructor
    public ConjurDirectDockerCertCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    public String getClientKeyVariable() {
        return clientKeyVariable;
    }

    @DataBoundSetter
    public void setClientKeyVariable(String clientKeyVariable) {
        LOGGER.log(Level.INFO, "Setting clientKeyVariable to {0}", clientKeyVariable);
        this.clientKeyVariable = clientKeyVariable;
    }

    public String getClientCertVariable() {
        return clientCertVariable;
    }

    @DataBoundSetter
    public void setClientCertVariable(String clientCertVariable) {
        LOGGER.log(Level.INFO, "Setting clientCertVariable to {0}", clientCertVariable);
        this.clientCertVariable = clientCertVariable;
    }

    public String getCaCertVariable() {
        return caCertVariable;
    }

    @DataBoundSetter
    public void setCaCertVariable(String caCertVariable) {
        LOGGER.log(Level.INFO, "Setting caCertVariable to {0}", caCertVariable);
        this.caCertVariable = caCertVariable;
    }

    @Override
    protected Class<ConjurSecretDockerCertCredentials> type() {
        return ConjurSecretDockerCertCredentials.class;
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        LOGGER.log(Level.FINEST, "ConjurDirectDockerCertCredentialsBinding.bind() for build: {0}", build.getDisplayName());

        ConjurSecretDockerCertCredentials credentials = getCredentials(build);
        credentials.setContext(build);

        Map<String, String> env = new HashMap<>();

        if (clientKeyVariable != null) {
            Secret clientKey = credentials.getClientKeySecret();
            if (clientKey != null) {
                env.put(clientKeyVariable, clientKey.getPlainText());
            }
        }
        if (clientCertVariable != null) {
            env.put(clientCertVariable, credentials.getClientCertificate());
        }
        if (caCertVariable != null) {
            env.put(caCertVariable, credentials.getServerCaCertificate());
        }

        return new MultiEnvironment(env);
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>();
        if (clientKeyVariable != null) vars.add(clientKeyVariable);
        if (clientCertVariable != null) vars.add(clientCertVariable);
        if (caCertVariable != null) vars.add(caCertVariable);
        return vars;
    }

    @Symbol("conjurDirectDockerCert")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretDockerCertCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct Docker Certificate Credential";
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
