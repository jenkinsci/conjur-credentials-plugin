package org.conjur.jenkins.conjursecrets;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BaseSSHUser;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ConjurSecretUsernameSSHKeyCredentialsImpl sets the passphrase and private key
 * details based on SSHKeyCredential
 *
 */
public class ConjurSecretUsernameSSHKeyCredentialsImpl extends BaseSSHUser
		implements ConjurSecretUsernameSSHKeyCredentials {

	private static final long serialVersionUID = 1L;
	private static final String DISPLAY_NAME = "Conjur Secret Username SSHKey Credential";

	private String credentialID;
	private Secret passphrase;
	transient ModelObject context;
	private transient ModelObject inheritedObjectContext;
	boolean storedInConjurStorage = false;

	/**
	 * Constructor to set the credentialScope,id,username,credentialID,conjurConfiguration ,passphrase and description
	 * @param scope provides the Credential Scope
	 * @param id  provides the job id
	 * @param username provides the username
	 * @param credentialID provides the CredentialID
	 * @param passphrase provides the passphrase
	 * @param description provides the description
	 */
	@DataBoundConstructor
	public ConjurSecretUsernameSSHKeyCredentialsImpl(final CredentialsScope scope, final String id,
			final String username, final String credentialID,
			final Secret passphrase, final String description) {
		super(scope, id, username, description);
		this.credentialID = credentialID;
		this.passphrase = passphrase;
		//this.conjurConfiguration = conjurConfiguration;
	}

	/**
	 * Returns the CredentialID
	 * @return credentialID
	 */
	public String getCredentialID() {
		return credentialID;
	}

	/**
	 * set the credentialID
	 * @param credentialID
	 */
	@DataBoundSetter
	public void setCredentialID(final String credentialID) {
		this.credentialID = credentialID;
	}

	/**
	 * Return this passphrase
	 * @return Secret
	 */
	public Secret getPassphrase() {
		return passphrase;
	}

	/**
	 * set the secret
	 * @param passphrase
	 */
	@DataBoundSetter
	public void setPassphrase(final Secret passphrase) {
		this.passphrase = passphrase;
	}

	/**
	 * Returns the credential type description
	 * @return DescriptorDisplayName
	 */
	public static String getDescriptorDisplayName() {
		return DISPLAY_NAME;
	}

	/**
	 * Returns the display name
	 * @return DisplayName
	 */
	@Override
	public String getDisplayName() {
		return "ConjurSecretUsernameSSHKey:" + this.username;
	}

	@Override
	public String getNameTag() {
		return "";
	}

	@Override
	public Secret getSecret() {
		return null;
	}

	/**
	 * Sets this ModelObject context
	 * @param context ModelObject for the context
	 */
	@Override
	public void setContext(final ModelObject context) {
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
	public void setInheritedContext(ModelObject context) {
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
	 * Return the PrivateKey
	 * @return the SSHKey secret
	 */
	@Override
	@SuppressWarnings("deprecation")
	public String getPrivateKey( ) {
		// First, try to fetch credentials from the global Jenkins context
		ModelObject searchContext = this.context != null ? this.context : Jenkins.get();
		ConjurSecretCredentials credential = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentials(
						ConjurSecretCredentials.class,
                        (ItemGroup) searchContext,
						ACL.SYSTEM,
						Collections.emptyList()),
				CredentialsMatchers.withId(credentialID));

		if (credential != null) {
			return credential.getSecret().getPlainText();
		}

		return "";
	}

	/**
	 * Return the list of PrivateKey
	 * @return List of PrivateKey
	 */
	@Override
	public List<String> getPrivateKeys() {
		final List<String> result = new ArrayList<>();
		result.add(getPrivateKey());
		return result;
	}

	@Extension
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return ConjurSecretUsernameSSHKeyCredentialsImpl.getDescriptorDisplayName();
		}

		public ListBoxModel doFillCredentialIDItems(@AncestorInPath final Item item, @QueryParameter final String uri) {
			Jenkins.get().checkPermission(Jenkins.ADMINISTER);
			return new StandardListBoxModel().includeAs(ACL.SYSTEM, item, ConjurSecretCredentials.class,
					URIRequirementBuilder.fromUri(uri).build());
		}

        public FormValidation doTestConnection(
                @AncestorInPath ItemGroup<Item> context,
                @QueryParameter("credentialID") String credentialID,
                @QueryParameter("passphrase") Secret passphrase,
                @QueryParameter("username") String username) {

            if (username == null || credentialID == null || passphrase == null) {
                return FormValidation.error("FAILED username,passphrase,credentialID fields is required");
            }

            ConjurSecretUsernameSSHKeyCredentialsImpl credential = new ConjurSecretUsernameSSHKeyCredentialsImpl(CredentialsScope.GLOBAL, "test", username, credentialID, passphrase,
                    "desc");
            return ConjurAPIUtils.validateCredential(context, credential);
        }
    }
}
