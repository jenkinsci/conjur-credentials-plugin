package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.Binding;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 *
 */
public class ConjurSecretStringCredentialsBinding extends Binding<ConjurSecretStringCredentials> {
    private static final Logger LOGGER = Logger.getLogger(ConjurSecretStringCredentialsBinding.class.getName());
    private static final String DISPLAY_NAME = "Secret String Credential";

    @DataBoundConstructor
    public ConjurSecretStringCredentialsBinding(String variable, String credentialsId) {
        super(variable, credentialsId);
    }

    @Override protected Class<ConjurSecretStringCredentials> type() {
        return ConjurSecretStringCredentials.class;
    }

    @Override public SingleEnvironment bindSingle(@NonNull Run<?,?> build,
                                                  @Nullable FilePath workspace,
                                                  @Nullable Launcher launcher,
                                                  @NonNull TaskListener listener) throws IOException, InterruptedException {

        LOGGER.log( Level.FINEST, String.format("Bind ConjurSecretStringCredentials to %s", build.getDisplayName() ) );
        return new SingleEnvironment(getCredentials(build).getSecret().getPlainText());
    }

    @Symbol("conjurSecretString")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretStringCredentials> {

        @Override public boolean requiresWorkspace() {
            return false;
        }

        @Override protected Class<ConjurSecretStringCredentials> type() {
            return ConjurSecretStringCredentials.class;
        }

        @Override public String getDisplayName() {
            return DISPLAY_NAME;
        }
    }
}
