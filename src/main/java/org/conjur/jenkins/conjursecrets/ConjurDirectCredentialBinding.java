package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsScope;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

/**
 * Pipeline binding that retrieves a Conjur secret using the Conjur path directly,
 * bypassing the Jenkins credential store.
 *
 * <p>Usage in Jenkinsfile:
 * <pre>
 * withCredentials([conjurDirectCredential(credentialsId: 'path/to/secret/in/conjur', variable: 'MY_VAR')]) {
 *     sh 'echo $MY_VAR'
 * }
 * </pre>
 *
 * <p>Unlike {@code conjurSecretCredential}, the {@code credentialsId} here is not a Jenkins
 * credential ID — it is the raw Conjur variable path passed directly to the Conjur API.
 */
public class ConjurDirectCredentialBinding extends MultiBinding<ConjurSecretCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectCredentialBinding.class.getName());

    private String variable;

    @Symbol("conjurDirectCredential")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretCredentials> {
        private static final String DISPLAY_NAME = "Conjur Direct Secret Credential";

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        protected Class<ConjurSecretCredentials> type() {
            return ConjurSecretCredentials.class;
        }
    }

    /**
     * @param credentialsId the Conjur variable path (e.g. {@code team/myapp/db_password}),
     *                      not a Jenkins credential ID
     */
    @DataBoundConstructor
    public ConjurDirectCredentialBinding(String credentialsId) {
        super(credentialsId);
    }

    /**
     * Resolves the Conjur secret using the path supplied as {@code credentialsId},
     * without any lookup in the Jenkins credential store.
     */
    @Override
    public MultiEnvironment bind(@NonNull Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        String conjurPath = getCredentialsId();
        LOGGER.log(Level.FINEST, "conjurDirectCredential: resolving path ''{0}'' for build {1}",
                new Object[]{conjurPath, build.getFullDisplayName()});

        ConjurSecretCredentialsImpl credential = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, conjurPath, conjurPath, "Direct Conjur credential"
        );
        credential.setContext(build);

        Secret secret = credential.getSecret();
        return new MultiEnvironment(Collections.singletonMap(variable, secret != null ? secret.getPlainText() : ""));
    }

    public String getVariable() {
        return variable;
    }

    @DataBoundSetter
    public void setVariable(String variable) {
        this.variable = variable;
    }

    @Override
    protected Class<ConjurSecretCredentials> type() {
        return ConjurSecretCredentials.class;
    }

    @Override
    public Set<String> variables() {
        return Collections.singleton(variable);
    }
}
