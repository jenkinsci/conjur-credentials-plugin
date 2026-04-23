package org.conjur.jenkins.conjursecrets;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline binding that exposes a Conjur file secret as a temporary file on disk
 * and also as a content variable, without requiring the path to be pre-configured.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectFile(
 *       credentialsId: 'path/to/secret',
 *       fileVariable: 'SECRET_FILE',
 *       contentVariable: 'SECRET_CONTENT')]) {
 *       sh 'cat $SECRET_FILE'
 *   }
 * </pre>
 */
public class ConjurDirectFileCredentialsBinding extends MultiBinding<ConjurSecretFileCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectFileCredentialsBinding.class.getName());

    private String fileVariable;
    private String contentVariable;

    @DataBoundConstructor
    public ConjurDirectFileCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    public String getFileVariable() {
        return fileVariable;
    }

    @DataBoundSetter
    public void setFileVariable(String fileVariable) {
        this.fileVariable = fileVariable;
    }

    public String getContentVariable() {
        return contentVariable;
    }

    @DataBoundSetter
    public void setContentVariable(String contentVariable) {
        this.contentVariable = contentVariable;
    }

    @Override
    protected Class<ConjurSecretFileCredentials> type() {
        return ConjurSecretFileCredentials.class;
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        LOGGER.log(Level.FINEST, "ConjurDirectFileCredentialsBinding.bind() for build: {0}", build.getDisplayName());

        ConjurSecretFileCredentials credentials = getCredentials(build);
        credentials.setContext(build);

        String content = credentials.getSecret().getPlainText();
        FilePath tempFile = workspace.createTextTempFile(getCredentialsId(), ".tmp", content);
        build.addAction(new CleanupAction(tempFile));

        Map<String, String> env = new HashMap<>();
        env.put(fileVariable, tempFile.getRemote());
        if (contentVariable != null) {
            env.put(contentVariable, content);
        }
        return new MultiEnvironment(env);
    }

    @Override
    public Set<String> variables() {
        Set<String> vars = new HashSet<>();
        vars.add(fileVariable);
        if (contentVariable != null) vars.add(contentVariable);
        return vars;
    }

    @Symbol("conjurDirectFile")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretFileCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct File Credential";
        }

        @Override
        public boolean requiresWorkspace() {
            return true;
        }

        @Override
        protected Class<ConjurSecretFileCredentials> type() {
            return ConjurSecretFileCredentials.class;
        }
    }

    protected static class CleanupAction extends InvisibleAction {
        private final String path;

        CleanupAction(FilePath tempFile) {
            this.path = tempFile.getRemote();
        }

        public String getPath() {
            return path;
        }
    }

    @Extension
    public static class CleanupListener extends RunListener<Run<?, ?>> {
        @Override
        public void onCompleted(Run<?, ?> run, TaskListener listener) {
            run.getActions(CleanupAction.class).forEach(cleanupAction -> {
                try {
                    new FilePath(new File(cleanupAction.getPath())).delete();
                } catch (Exception e) {
                    listener.error("ConjurDirectFile: can't delete temp file: " + e.getMessage());
                }
            });
        }
    }
}
