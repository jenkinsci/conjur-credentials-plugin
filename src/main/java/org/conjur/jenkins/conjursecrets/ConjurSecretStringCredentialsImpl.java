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
        return "ConjurSecretString:" + this.variableId;
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ConjurSecretStringCredentialsImpl.class.getName());
    private String variableId; // to be used as Username
    private transient ModelObject context;
    boolean storedInConjurStorage = false;
    private transient ModelObject inheritedObjectContext;

    /**
     * to set the varaiblePath,scope,id,description
     *
     * @param scope
     * @param id
     * @param variableId
     * @param description
     */
    @DataBoundConstructor
    public ConjurSecretStringCredentialsImpl(CredentialsScope scope, String id, String variableId,
                                             String description) {
        super(scope, id, description);
        this.variableId = variableId;

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
     * set the variableId as String
     *
     * @param variableId
     */
    @DataBoundSetter
    public void setVariableId(String variableId) {
        this.variableId = variableId;
    }

    /**
     *
     * @return variableId as String
     **/
    public String getVariableId() {
        return this.variableId;
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
            retSecret = ConjurAPI.getSecretFromConjur(this.context, this.inheritedObjectContext, this.variableId);
        }else {
            retSecret = ConjurAPI.getSecretFromConjurWithInheritance(this.context, this, this.variableId);
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
                @QueryParameter("variableId") String variableId) {

            if (variableId == null || variableId.isEmpty()) {
                return FormValidation.error("FAILED variableId field is required");
            }
            ConjurSecretStringCredentialsImpl credential = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL, credentialID, variableId,
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
            super( base.getScope(), base.getId(), base.getVariableId() , base.getDescription());
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
