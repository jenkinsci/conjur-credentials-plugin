package org.conjur.jenkins.conjursecrets;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline binding that exposes a Conjur secret text as a single environment variable,
 * without requiring a pre-configured path — the credentialsId IS the Conjur variable path.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectString(credentialsId: 'path/to/secret', variable: 'MY_SECRET')]) {
 *       sh 'echo $MY_SECRET'
 *   }
 * </pre>
 */
public class ConjurDirectStringCredentialsBinding extends Binding<ConjurSecretStringCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectStringCredentialsBinding.class.getName());

    @DataBoundConstructor
    public ConjurDirectStringCredentialsBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override
    protected Class<ConjurSecretStringCredentials> type() {
        return ConjurSecretStringCredentials.class;
    }

    @Override
    public SingleEnvironment bindSingle(@NonNull Run<?, ?> build,
                                        @Nullable FilePath workspace,
                                        @Nullable Launcher launcher,
                                        @NonNull TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.FINEST, "ConjurDirectStringCredentialsBinding.bindSingle() for build: {0}", build.getDisplayName());
        ConjurSecretStringCredentials credentials = getCredentials(build);
        credentials.setContext(build);
        return new SingleEnvironment(credentials.getSecret().getPlainText());
    }

    @Symbol("conjurDirectString")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretStringCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct Secret String Credential";
        }

        @Override
        public boolean requiresWorkspace() {
            return false;
        }

        @Override
        protected Class<ConjurSecretStringCredentials> type() {
            return ConjurSecretStringCredentials.class;
        }
    }
}
