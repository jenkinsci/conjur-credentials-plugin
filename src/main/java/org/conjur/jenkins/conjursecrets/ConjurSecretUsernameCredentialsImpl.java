package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
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

/**
 * Class to get the secret for UserNameCredential
 */
@NameWith(value = ConjurSecretCredentials.NameProvider.class, priority = 1)
public class ConjurSecretUsernameCredentialsImpl extends BaseStandardCredentials
		implements ConjurSecretUsernameCredentials {

	private String username;
	private String variableId;
	private transient ModelObject context;
	private transient ModelObject inheritedObjectContext;
	boolean storedInConjurStorage = false;

	/**
	 * Constructor to set the scope,id,username,credentailID,conjurConfiguration
	 * 
	 * @param scope CredentialScope
	 * @param id String
	 * @param username String
	 * @param variableId String
	 * @param description String
	 */
	@DataBoundConstructor
	public ConjurSecretUsernameCredentialsImpl(CredentialsScope scope, String id, String username, String variableId,
			 String description) {
		super(scope, id, description);
		LOGGER.log(Level.FINEST, String.format("ConjurSecretUsernameCredentialsImpl, id %s", id ) );
		this.username = username;
		this.variableId = variableId;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * @return usernmae
	 */
	@Override
	public String getUsername() {
		return this.username;
	}

	/**
	 * set the userName
	 * 
	 * @param username
	 */
	@DataBoundSetter
	public void setUserName(String username) {
		this.username = username;
	}

	/**
	 * 
	 * @return credentalID as String
	 */
	public String getVariableId() {
		return variableId;
	}

	/**
	 * set the CredentialId as String
	 * 
	 * @param variableId
	 */
	@DataBoundSetter
	public void setVariableId(String variableId) {
		this.variableId = variableId;
	}

	private static final String name = "Conjur Secret Username Credential";
	/**
	 * 
	 * @return DisplayName for Descriptor
	 */
	public static String getDescriptorDisplayName() {
		return name;
	}

	/**
	 * @return DisplayName
	 */
	@Override
	public String getDisplayName() {
		return "ConjurSecretUsername:" + this.getVariableId();
	}

	/**
	 * set the ModelObject context
	 * @param context Jenkins context
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
	 * @return password
	 */
	@Override
	public Secret getSecret(  ) {
		return getPassword(  );
	}

	/**
	 * @retrun secret
	 */
	@Override
	public Secret getPassword( ) {
		LOGGER.log(Level.FINEST, String.format("getPassword, stored %b context %s",storedInConjurStorage , this.context ) );
		Secret retSecret = null;
		if( storedInConjurStorage ) {
			retSecret = ConjurAPI.getSecretFromConjur(this.context, this.inheritedObjectContext, this.variableId);
		}else {
			retSecret = ConjurAPI.getSecretFromConjurWithInheritance(this.context, this, this.variableId);
		}
		return retSecret;
	}

	/**
	 * @return NameTag
	 */
	@Override
	public String getNameTag() {
		return "";
	}


	@Extension
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return ConjurSecretUsernameCredentialsImpl.getDescriptorDisplayName();
		}

		public FormValidation doTestConnection(
				@AncestorInPath ItemGroup<Item> context,
				@QueryParameter("variableId") String variableId,
				@QueryParameter("username") String username) {

			if (username == null || variableId == null) {
				return FormValidation.error("FAILED username,credentialID fields is required");
			}
			ConjurSecretUsernameCredentialsImpl credential = new ConjurSecretUsernameCredentialsImpl(CredentialsScope.GLOBAL, "test", username, variableId,
					"desc");
			return ConjurAPIUtils.validateCredential(context, credential);
		}

	}
}
