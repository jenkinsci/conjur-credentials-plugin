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

import javax.annotation.CheckForNull;
import java.util.logging.Logger;

/** Class to retrieve the secrets */
public class ConjurSecretCredentialsImpl extends BaseStandardCredentials implements ConjurSecretCredentials {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(ConjurSecretCredentialsImpl.class.getName());
	private String variableName; // to be used as Username
	private transient ModelObject context;
	boolean storedInConjurStorage = false;
	private transient ModelObject inheritedObjectContext;

	/**
	 * to set the varaibleName,scope,id,description
	 * 
	 * @param scope
	 * @param id
	 * @param variableName
	 * @param description
	 */
	@DataBoundConstructor
	public ConjurSecretCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String variableName, @CheckForNull String description) {
		super(scope, id, description);
		this.variableName = variableName;
	}

	/**
	 * @return the DisplayName
	 */
	@Override
	public String getDisplayName() {
		return "ConjurSecret:" + this.variableName;
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
	 *
	 * @param context ModelObject
	 */
	@Override
	public void setInheritedContext( ModelObject context)
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
	public Secret getSecret( ) {
		Secret retSecret = null;
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
		private static final String DISPLAY_NAME = "Conjur Secret Credential";

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
			ConjurSecretCredentialsImpl credential = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, credentialID, variableName,
					"desc");
			return ConjurAPIUtils.validateCredential(context, credential);
		}
	}

	/**
	 *
	 */
	static class SelfContained extends ConjurSecretCredentialsImpl {
		private final Secret secret;

		public SelfContained(ConjurSecretCredentialsImpl base) {
			super( base.getScope(), base.getId(), base.getVariableName() , base.getDescription());
			secret = base.getSecret();
		}

		@NonNull
		@Override
		public Secret getSecret() {
			return secret;
		}
	}

	/**
	 *
	 */
	@Extension
	public static class SnapshotTaker extends CredentialsSnapshotTaker<ConjurSecretCredentialsImpl> {
		@Override
		public Class<ConjurSecretCredentialsImpl> type() {
			return ConjurSecretCredentialsImpl.class;
		}

		@Override
		public ConjurSecretCredentialsImpl snapshot(ConjurSecretCredentialsImpl credentials) {
			return new ConjurSecretCredentialsImpl.SelfContained(credentials);
		}
	}
}
