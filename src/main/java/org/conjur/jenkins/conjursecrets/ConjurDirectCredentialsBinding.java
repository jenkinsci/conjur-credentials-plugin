package org.conjur.jenkins.conjursecrets;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.util.Secret;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.ConjurJITJobProperty;
import org.conjur.jenkins.configuration.FolderConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConjurDirectCredentialsBinding extends MultiBinding<ConjurSecretCredentials> {

    private ConjurConfiguration conjurConfiguration;
    private Run<?, ?> context;

    @Symbol("conjurDirectCredential")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct credentials";
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

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectCredentialsBinding.class.getName());

    private String variable;

    @DataBoundConstructor
    public ConjurDirectCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        this.setContext(build);
        return new MultiEnvironment(
                Collections.singletonMap(variable, this.getSecret(this.getCredentialsId()).getPlainText()));
    }

    public String getVariable() {
        return this.variable;
    }

    @DataBoundSetter
    public void setVariable(String variable) {
        LOGGER.log(Level.INFO, "Setting variable to {0}", variable);
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

    public Secret getSecret(String secretPath) {
        String result = "";
        try {
            // Get Http Client
            OkHttpClient client = ConjurAPIUtils.getHttpClient(this.conjurConfiguration);
            // Authenticate to Conjur
            String authToken = ConjurAPI.getAuthorizationToken(client, this.conjurConfiguration, context);
            // Retrieve secret from Conjur
            String secretString = ConjurAPI.getSecret(client, this.conjurConfiguration, authToken, secretPath);
            result = secretString;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "EXCEPTION: " + e.getMessage());
            throw new InvalidConjurSecretException(e.getMessage(), e);
        }

        return secretFromString(result);
    }

    static Secret secretFromString(String secretString) {
        Channel channel = Channel.current();

        if (channel != null) {
            return (Secret) ConjurAPIUtils.objectFromMaster(channel,
                    new ConjurAPIUtils.NewSecretFromString(secretString));
        }

        return Secret.fromString(secretString);
    }

    public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
        if (conjurConfiguration != null)
            this.conjurConfiguration = conjurConfiguration;
    }

    public void setContext(Run<?, ?> context) {
        LOGGER.log(Level.INFO, "Setting context");
        this.context = context;
        setConjurConfiguration(getConfigurationFromContext(context));
    }

    protected ConjurConfiguration getConfigurationFromContext(Run<?, ?> context) {
        LOGGER.log(Level.INFO, "Getting Configuration from Context");
        ConjurConfiguration conjurConfig = GlobalConjurConfiguration.get().getConjurConfiguration();

        if (context == null) {
            return ConjurAPI.logConjurConfiguration(conjurConfig);
        }

        ConjurJITJobProperty conjurJobConfig = context.getParent().getProperty(ConjurJITJobProperty.class);

        if (conjurJobConfig != null && !conjurJobConfig.getInheritFromParent()) {
            // Taking the configuration from the Job
            return ConjurAPI.logConjurConfiguration(conjurJobConfig.getConjurConfiguration());
        }

        ConjurConfiguration inheritedConfig = inheritedConjurConfiguration(context.getParent());
        if (inheritedConfig != null) {
            return ConjurAPI.logConjurConfiguration(inheritedConfig);
        }

        return ConjurAPI.logConjurConfiguration(conjurConfig);

    }

    private ConjurConfiguration inheritedConjurConfiguration(Item job) {
        for (ItemGroup<? extends Item> g = job
                .getParent(); g instanceof AbstractFolder; g = ((AbstractFolder<? extends Item>) g).getParent()) {
            FolderConjurConfiguration fconf = ((AbstractFolder<?>) g).getProperties()
                    .get(FolderConjurConfiguration.class);
            if (!(fconf == null || fconf.getInheritFromParent())) {
                // take the folder Conjur Configuration
                return fconf.getConjurConfiguration();
            }
        }
        return null;
    }

}
