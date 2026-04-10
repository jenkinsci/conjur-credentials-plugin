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
import java.util.*;

public class ConjurSecretFileCredentialsBinding extends MultiBinding<ConjurSecretFileCredentials> {

    private String fileVariable;
    private String contentVariable;

    @DataBoundConstructor
    public ConjurSecretFileCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @Override
    protected Class<ConjurSecretFileCredentials> type() {
        return ConjurSecretFileCredentials.class;
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
    public MultiEnvironment bind(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        ConjurSecretFileCredentials credentials = getCredentials(build);
        credentials.setContext(build);
        FilePath tempFile = workspace.createTextTempFile(getCredentialsId(), ".tmp", credentials.getSecret().getPlainText());
        build.addAction(new CleanupAction(tempFile));

        Map<String, String> env = new HashMap<>();
        env.put(fileVariable, tempFile.getRemote());
        env.put(contentVariable, tempFile.readToString());

        return new MultiEnvironment(env);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(fileVariable, contentVariable));
    }

    @Symbol("conjurSecretFile")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretFileCredentials> {
        @Override
        public String getDisplayName() {
            return "Conjur Secret File Credentials";
        }

        @Override
        protected Class<ConjurSecretFileCredentials> type() {
            return ConjurSecretFileCredentials.class;
        }

        @Override
        public boolean requiresWorkspace() {
            return true;
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
                    listener.error("Can't delete temp file: " + e.getMessage());
                }
            });
        }
    }
}