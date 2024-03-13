package org.conjur.jenkins.conjursecrets;

import java.util.ArrayList;
import java.util.List;

import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BaseSSHUser;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * ConjurSecretUsernameSSHKeyCredentialsImpl sets the passphrase and private key
 * details based on SSHKeyCredential
 * 
 */
public class ConjurSecretUsernameSSHKeyCredentialsImpl extends BaseSSHUser
		implements ConjurSecretUsernameSSHKeyCredentials {

	private static final long serialVersionUID = 1L;

	private String credentialID;
	private ConjurConfiguration conjurConfiguration;
	private Secret passphrase;

	transient ModelObject context;
	transient ModelObject storeContext;

	/**
	 * Constructor to set the credentialScope,id,username,credentialID,conjurConfiguration ,passphrase and description
	 * @param scope provides the Credential Scope
	 * @param id  provides the job id
	 * @param username provides the username
	 * @param credentialID provides the CredentialID
	 * @param conjurConfiguration provides the conjur configuration
	 * @param passphrase provides the passphrase
	 * @param description provides the description
	 */
	@DataBoundConstructor
	public ConjurSecretUsernameSSHKeyCredentialsImpl(final CredentialsScope scope, final String id,
			final String username, final String credentialID, final ConjurConfiguration conjurConfiguration,
			final Secret passphrase, final String description) {
		super(scope, id, username, description);
		this.credentialID = credentialID;
		this.passphrase = passphrase;
		this.conjurConfiguration = conjurConfiguration;
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
	 * Returns the ConjurConfiguration object
	 * @return ConjurConfiguration
	 */

	public ConjurConfiguration getConjurConfiguration() {
		return conjurConfiguration;
	}

	/**
	 * set the ConjurConfiguration params
	 * @param conjurConfiguration set the ConjurConfiguration object
	 */

	@DataBoundSetter
	public void setConjurConfiguration(final ConjurConfiguration conjurConfiguration) {

		ConjurAPI.logConjurConfiguration(conjurConfiguration);

		this.conjurConfiguration = conjurConfiguration;

		ConjurSecretCredentials.setConjurConfigurationForCredentialWithID(this.getCredentialID(), conjurConfiguration,
				context);

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
	 * To fill the Jenkins listbox with CredentialItems for
	 * ConjurSecretUsernameSSHKeyCredentials
     */
	@Extension
	public static class DescriptorImpl extends CredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return ConjurSecretUsernameSSHKeyCredentialsImpl.getDescriptorDisplayName();
		}

		public ListBoxModel doFillCredentialIDItems(@AncestorInPath final Item item, @QueryParameter final String uri) {
			Jenkins.get().checkPermission(Jenkins.ADMINISTER);
			return new StandardListBoxModel().includeAs(ACL.SYSTEM, item, ConjurSecretCredentials.class,
					URIRequirementBuilder.fromUri(uri).build());
		}

	}

	/**
	 * Returns the credential type description
	 * @return DescriptorDisplayName
	 */

	public static String getDescriptorDisplayName() {
		return "Conjur Secret Username SSHKey Credential";
	}

	/**
	 * Returns the display name
	 * @return DisplayName
	 */

	@Override
	public String getDisplayName() {
		return "ConjurSecretUsernameSSHKey:" + this.username;
	}

	/**
	 * Sets this ModelObject context
	 * @param context ModelObject for the context
	 */

	@Override
	public void setContext(final ModelObject context) {
		if (context != null)
			this.context = context;
	}

	/**
	 * Sets the ModelObject storeContext
	 * @param storeContext ModelObject for the storeContext
	 */

	@Override
	public void setStoreContext(ModelObject storeContext) {
		this.storeContext = storeContext;
	}

	/**
	 * Return the PrivateKey
	 * @return the SSHKey secret
	 */
	@Override
	public String getPrivateKey() {
		final Secret secret = ConjurSecretCredentials.getSecretFromCredentialIDWithConfigAndContext(
				this.getCredentialID(), this.conjurConfiguration, this.context, this.storeContext);
		return secret.getPlainText();
	}

	/**
	 * Return the list of PrivateKey
	 * @return List of PrivateKey
	 */
	@Override
	public List<String> getPrivateKeys() {
		final List<String> result = new ArrayList<String>();
		result.add(getPrivateKey());
		return result;
	}

}
