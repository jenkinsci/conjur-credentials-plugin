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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline binding that exposes a Conjur SSH Username+PrivateKey credential as two
 * environment variables, without requiring the path to be pre-configured.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectSSHKey(
 *       credentialsId: 'path/to/ssh-key',
 *       usernameVariable: 'SSH_USER',
 *       privateKeyVariable: 'SSH_KEY')]) {
 *       sh 'echo $SSH_USER'
 *   }
 * </pre>
 */
public class ConjurDirectSSHKeyCredentialsBinding extends MultiBinding<ConjurSecretUsernameSSHKeyCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectSSHKeyCredentialsBinding.class.getName());

    private String usernameVariable;
    private String privateKeyVariable;

    @DataBoundConstructor
    public ConjurDirectSSHKeyCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    public String getUsernameVariable() {
        return usernameVariable;
    }

    @DataBoundSetter
    public void setUsernameVariable(String usernameVariable) {
        LOGGER.log(Level.INFO, "Setting usernameVariable to {0}", usernameVariable);
        this.usernameVariable = usernameVariable;
    }

    public String getPrivateKeyVariable() {
        return privateKeyVariable;
    }

    @DataBoundSetter
    public void setPrivateKeyVariable(String privateKeyVariable) {
        LOGGER.log(Level.INFO, "Setting privateKeyVariable to {0}", privateKeyVariable);
        this.privateKeyVariable = privateKeyVariable;
    }

    @Override
    protected Class<ConjurSecretUsernameSSHKeyCredentials> type() {
        return ConjurSecretUsernameSSHKeyCredentials.class;
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        LOGGER.log(Level.FINEST, "ConjurDirectSSHKeyCredentialsBinding.bind() for build: {0}", build.getDisplayName());

        ConjurSecretUsernameSSHKeyCredentials credentials = getCredentials(build);
        credentials.setContext(build);

        Map<String, String> env = new HashMap<>();
        env.put(usernameVariable, credentials.getUsername());
        env.put(privateKeyVariable, credentials.getPrivateKey());
        return new MultiEnvironment(env);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(usernameVariable, privateKeyVariable));
    }

    @Symbol("conjurDirectSSHKey")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretUsernameSSHKeyCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct SSH Username+Private Key Credential";
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        protected Class<ConjurSecretUsernameSSHKeyCredentials> type() {
            return ConjurSecretUsernameSSHKeyCredentials.class;
        }
    }
}
