package org.conjur.jenkins.conjursecrets;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline binding that retrieves a Conjur secret by explicit path at bind-time,
 * without requiring the path to be pre-configured in a Jenkins credential.
 *
 * Usage in Jenkinsfile:
 * <pre>
 *   withCredentials([conjurDirectCredential(credentialsId: 'my-conjur-cred', variable: 'MY_SECRET')]) {
 *       sh 'echo $MY_SECRET'
 *   }
 * </pre>
 *
 * The credentialsId is used as the Conjur variable path.
 */
public class ConjurDirectCredentialsBinding extends MultiBinding<ConjurSecretCredentials> {

    private static final Logger LOGGER = Logger.getLogger(ConjurDirectCredentialsBinding.class.getName());

    private String variable;

    @Symbol("conjurDirectCredential")
    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ConjurSecretCredentials> {

        @Override
        public String getDisplayName() {
            return "Conjur Direct Credential (by path)";
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

    @DataBoundConstructor
    public ConjurDirectCredentialsBinding(String credentialsId) {
        super(credentialsId);
    }

    @Override
    public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "ConjurDirectCredentialsBinding.bind() for variable: {0}", variable);
        Secret secret = getSecretFromConjur(this.getCredentialsId(), build);
        return new MultiEnvironment(Collections.singletonMap(variable, secret.getPlainText()));
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

    /**
     * Fetch a secret directly from Conjur by path using the upstream API chain:
     * getConfigurationFromContext → getConjurAuthnInfo → getAuthorizationToken → getConjurSecret.
     *
     * The authToken byte array is zeroed after use for security hygiene.
     *
     * @param secretPath Conjur variable path (used as credentialsId)
     * @param context    the current build Run
     * @return Secret containing the retrieved value
     */
    private Secret getSecretFromConjur(String secretPath, ModelObject context) {
        byte[] authToken = null;
        byte[] result = null;
        try {
            ConjurConfiguration conjurConfiguration = ConjurAPI.getConfigurationFromContext(context);
            OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurConfiguration);

            ConjurAuthnInfo conjurAuthn = ConjurAPI.getConjurAuthnInfo(conjurConfiguration, context);
            authToken = ConjurAPI.getAuthorizationToken(conjurAuthn, context);

            result = ConjurAPI.getConjurSecret(client, conjurConfiguration, authToken, secretPath);

            Secret secret = Secret.fromString(new String(result, StandardCharsets.UTF_8));
            return secret;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "EXCEPTION fetching Conjur secret: " + e.getMessage(), e);
            throw new InvalidConjurSecretException(e.getMessage(), e);
        } finally {
            if (authToken != null) Arrays.fill(authToken, (byte) 0);
            if (result != null) Arrays.fill(result, (byte) 0);
        }
    }
}
