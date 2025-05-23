package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsSnapshotTaker;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class ConjurSecretStringCredentialsImpl extends BaseStandardCredentials implements ConjurSecretStringCredentials {
    @Override
    public String getDisplayName() {
        return "ConjurSecretString:" + this.variableName;
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ConjurSecretStringCredentialsImpl.class.getName());
    private String variableName; // to be used as Username
    private transient ModelObject context;
    boolean storedInConjurStorage = false;
    private transient ModelObject inheritedObjectContext;

    /**
     * to set the varaiblePath,scope,id,description
     *
     * @param scope
     * @param id
     * @param variableName
     * @param description
     */
    @DataBoundConstructor
    public ConjurSecretStringCredentialsImpl(CredentialsScope scope, String id, String variableName,
                                             String description) {
        super(scope, id, description);
        this.variableName = variableName;

        LOGGER.log( Level.FINEST, "ConjurSecretStringCredentialsImpl");
    }

    /**
     * @retrun the Secret based on the credentialId
     * @param secretString
     * @return
     */
    static Secret secretFromString(String secretString) {
        return Secret.fromString(secretString);
    }

    /**
     * set the variableName as String
     *
     * @param variableName
     */
    @DataBoundSetter
    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    /**
     *
     * @return variableName as String
     **/
    public String getVariableName() {
        return this.variableName;
    }

    /**
     * Set context to which Credential will be bind
     * @param context ModelObject
     */
    @Override
    public void setContext(ModelObject context) {
        this.context = context;
    }

    /**
     * get the ModelObject context
     * @return context
     */
    @Override
    public ModelObject getContext() {
        return this.context;
    }

    /**
     * Set Context of inherited object to which call works (support for inheritance)
     * @param context ModelObject context
     */
    @Override
    public void setInheritedContext(ModelObject context)
    {
        inheritedObjectContext = context;
    }

    /**
     * get the ModelObject context
     * @return context
     */
    @Override
    public ModelObject getInheritedContext() {
        return this.inheritedObjectContext;
    }

    /**
     * set information if Credential is stored in ConjurStorage
     * @param storedInConjurStorage boolean value
     */
    @Override
    public void setStoredInConjurStorage(boolean storedInConjurStorage) {
        this.storedInConjurStorage = storedInConjurStorage;
    }

    /**
     * return information if Credential is stored in ConjurStorage
     * @return context
     */
    @Override
    public boolean storedInConjurStorage() {
        return this.storedInConjurStorage;
    }

    /**
     * @return the Secret calling the {@link ConjurAPI } class , Gets the
     *         OkHttpclient by calling getHttpclient of {@link ConjurAPIUtils} Get
     *         the AuthToken by calling getAuthorizationToken of {@link ConjurAPI }
     *         Get the secret by calling teh getSecret of {@link ConjurAPI }
     */
    @Override
    public Secret getSecret( ) {
        Secret retSecret;
        if( storedInConjurStorage ) {
            retSecret = ConjurAPI.getSecretFromConjur(this.context, this.inheritedObjectContext, this.variableName);
        }else {
            retSecret = ConjurAPI.getSecretFromConjurWithInheritance(this.context, this, this.variableName);
        }
        return retSecret;
    }

    /**
     * @return the Name Tag
     */
    @Override
    public String getNameTag() {
        return "";
    }

    /**
     *
     */
    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        private static final String DISPLAY_NAME = "Conjur Secret String Credential";

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        public FormValidation doTestConnection(
                @AncestorInPath ItemGroup<Item> context,
                @QueryParameter("credentialID") String credentialID,
                @QueryParameter("variableName") String variableName) {

            if (variableName == null || variableName.isEmpty()) {
                return FormValidation.error("FAILED variableName field is required");
            }
            ConjurSecretStringCredentialsImpl credential = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL, credentialID, variableName,
                    "desc");
            return ConjurAPIUtils.validateCredential(context, credential);
        }
    }

    /**
     *
     */
    static class SelfContained extends ConjurSecretStringCredentialsImpl {
        private final Secret secret;

        public SelfContained(ConjurSecretStringCredentialsImpl base) {
            super( base.getScope(), base.getId(), base.getVariableName() , base.getDescription());
            secret = base.getSecret();
        }

        @NonNull
        @Override
        public Secret getSecret() {
            return secret;
        }
    }

    @Extension
    public static class SnapshotTaker extends CredentialsSnapshotTaker<ConjurSecretStringCredentialsImpl> {
        @Override
        public Class<ConjurSecretStringCredentialsImpl> type() {
            return ConjurSecretStringCredentialsImpl.class;
        }

        @Override
        public ConjurSecretStringCredentialsImpl snapshot(ConjurSecretStringCredentialsImpl credentials) {
            return new SelfContained(credentials);
        }
    }
}
