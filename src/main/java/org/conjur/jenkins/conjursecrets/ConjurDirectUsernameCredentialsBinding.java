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
 * Pipeline binding that exposes a Conjur Username+Password credential as two
 * separate environment variables without requiring the path to be pre-configured.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectUsername(
 *       credentialsId: 'my-conjur-username-cred',
 *       usernameVariable: 'MY_USER',
 *       passwordVariable: 'MY_PASS')]) {
 *       sh 'echo $MY_USER'
 *   }
 * </pre>
 */
public class ConjurDirectUsernameCredentialsBinding extends MultiBinding<ConjurSecretUsernameCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectUsernameCredentialsBinding.class.getName());

    private String usernameVariable;
    private String passwordVariable;

    @Symbol("conjurDirectUsername")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretUsernameCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct Username+Password Credential";
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        protected Class<ConjurSecretUsernameCredentials> type() {
            return ConjurSecretUsernameCredentials.class;
        }
    }

    @DataBoundConstructor
    public ConjurDirectUsernameCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "ConjurDirectUsernameCredentialsBinding.bind() for [{0}, {1}]",
                new Object[]{usernameVariable, passwordVariable});

        ConjurSecretUsernameCredentials credential = getCredentials(build);
        // Inject context so the credential can resolve auth info from the build
        credential.setContext(build);

        Map<String, String> env = new HashMap<>();
        env.put(usernameVariable, credential.getUsername());
        env.put(passwordVariable, credential.getPassword().getPlainText());
        return new MultiEnvironment(env);
    }

    public String getUsernameVariable() {
        return this.usernameVariable;
    }

    public String getPasswordVariable() {
        return this.passwordVariable;
    }

    @DataBoundSetter
    public void setUsernameVariable(String usernameVariable) {
        LOGGER.log(Level.INFO, "Setting usernameVariable to {0}", usernameVariable);
        this.usernameVariable = usernameVariable;
    }

    @DataBoundSetter
    public void setPasswordVariable(String passwordVariable) {
        LOGGER.log(Level.INFO, "Setting passwordVariable to {0}", passwordVariable);
        this.passwordVariable = passwordVariable;
    }

    @Override
    protected Class<ConjurSecretUsernameCredentials> type() {
        return ConjurSecretUsernameCredentials.class;
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(usernameVariable, passwordVariable));
    }
}
