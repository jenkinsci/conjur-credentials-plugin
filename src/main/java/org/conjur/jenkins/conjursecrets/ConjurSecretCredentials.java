package org.conjur.jenkins.conjursecrets;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.conjur.jenkins.configuration.ConjurConfiguration;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;

@NameWith(value = ConjurSecretCredentials.NameProvider.class, priority = 1)
public interface ConjurSecretCredentials extends StandardCredentials {

	/** Innder class to retrieve the displayName for the job */
	class NameProvider extends CredentialsNameProvider<ConjurSecretCredentials> {
		/**
		 * returns the displayName and description to be displayed along with the Conjur
		 * secret Credential
		 */
		@Override
		public String getName(ConjurSecretCredentials c) {
			return c.getDisplayName() + c.getNameTag() + " (" + c.getDescription() + ")";
		}

	}

	public static final Logger LOGGER = Logger.getLogger(ConjurSecretCredentials.class.getName());

	String getDisplayName();

	String getNameTag();

	Secret getSecret();

	default Secret secretWithConjurConfigAndContext(ConjurConfiguration conjurConfiguration, ModelObject context) {
		setConjurConfiguration(conjurConfiguration);
		setContext(context);
		return getSecret();
	}

	void setConjurConfiguration(ConjurConfiguration conjurConfiguration);

	void setStoreContext(ModelObject storeContext);

	void setContext(ModelObject context);

	/**
	 * static method to fetch the credentials from the Context
	 *
	 * @param selected    ConjurSecretcredential
	 * @param selected    or incoming CredentialId
	 * @param ModelObject
	 * @return the ConjurSecretCredentials
	 */
	static ConjurSecretCredentials credentialFromContextIfNeeded(ConjurSecretCredentials credential,
			String credentialID, ModelObject context) {

		LOGGER.log(Level.FINE, "Start of credentialFromContextIfNeeded()");
		if (credential == null && context != null) {
			LOGGER.log(Level.FINE, "NOT FOUND at Jenkins Instance Level!");
			Item folder = null;

			if (context instanceof Run) {
				folder = Jenkins.get().getItemByFullName(((Run<?, ?>) context).getParent().getParent().getFullName());
				LOGGER.log(Level.FINE, "Context is a Run: {0}", folder);
			} else if (context instanceof AbstractItem) {
				AbstractItem item = (AbstractItem) context;
				String folderName = item.getFullName();
				if (folderName == null || folderName.isEmpty()) {
					folderName = item.getParent().getFullName(); // Fallback to parent full name
				}
				LOGGER.log(Level.FINE, "Resolving folder by name >> {0}", folderName);
				folder = Jenkins.get().getItemByFullName(folderName);
			}
			if (folder != null) {
				credential = CredentialsMatchers
						.firstOrNull(
								CredentialsProvider.lookupCredentials(ConjurSecretCredentials.class, folder, ACL.SYSTEM,
										Collections.<DomainRequirement>emptyList()),
								CredentialsMatchers.withId(credentialID));
			}
			return credential;
		}
		LOGGER.log(Level.FINE, "End  of credentialFromContextIfNeeded()... returning credentails");
		return credential;
	}

	/**
	 * static method to fetch the credentials from the Context
	 *
	 * @param selected    ConjurSecretcredential
	 * @param selected    or incoming CredentialId
	 * @param ModelObject
	 * @return the ConjurSecretCredentials
	 */

	static ConjurSecretCredentials credentialWithID(String credentialID, ModelObject context) {
		LOGGER.log(Level.FINE, "Start of credentialWithID()");
		ConjurSecretCredentials credential, conjurSecretCredential = null;
		// First, try to fetch credentials from the global Jenkins context
		credential = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentials(ConjurSecretCredentials.class, Jenkins.get(), ACL.SYSTEM,
						Collections.<DomainRequirement>emptyList()),
				CredentialsMatchers.withId(credentialID));
		// If not found, check credentials in the context hierarchy current folder job
		if (credential == null && context != null) {
			LOGGER.log(Level.FINE, "Credentials not found at Jenkins instance level >> {0}", context);
			String[] multiFolder = context.toString().split("/");
			Item parentFolder = null;
			if (context.getDisplayName().equalsIgnoreCase("Jenkins")) {

				LOGGER.log(Level.FINE, "Inside not Context Jenkins" + context.getDisplayName());

				return null;

			} else if (context instanceof Run) {
				parentFolder = Jenkins.get()
						.getItemByFullName(((Run<?, ?>) context).getParent().getParent().getFullName());
				LOGGER.log(Level.FINE, "Context is a Run, fetching parent folder:{0}" + parentFolder);
			} else if (context instanceof Job) {
				parentFolder = Jenkins.get().getItemByFullName(context.getDisplayName());
				LOGGER.log(Level.FINE, "Context is a Job, fetching parent folder: {0}" + parentFolder);
				if (parentFolder == null) {
					parentFolder = Jenkins.get()
							.getItemByFullName(((AbstractItem) ((AbstractItem) context).getParent()).getFullName());
					LOGGER.log(Level.FINE, "Job has folder level>>{0}", parentFolder);
				}
			} else {
				parentFolder = Jenkins.get()
						.getItemByFullName(((AbstractItem) ((AbstractItem) context).getParent()).getFullName());
				LOGGER.log(Level.FINE, "Inside not Context is a Job, fetching parent folder: {0}", parentFolder);
			}
			// Iterate through parent folders to search for credentials
			// Folder-level hierarchy exclude the Pipeline Job to search credentials
			for (int i = 0; i < multiFolder.length - 1; i++) {
				// check if folder has multiple parent level
				if (parentFolder != null) {
					// Pass null if credential is not found
					credential = credentialFromContextIfNeeded(conjurSecretCredential, credentialID, parentFolder);
					if (credential != null) {
						break; // Stop if credentials are found
					} else {
						// Retrieve the parent item, but check its type
						if (parentFolder.getParent() instanceof Item) {
							parentFolder = (Item) parentFolder.getParent();
							LOGGER.log(Level.FINE, "Moving up to parent folder: {0}", parentFolder);
						}
					}
				}
			}
		}
		LOGGER.log(Level.FINE, "End of credentialWithID()");
		return credential;
	}

	/**
	 * static method to set the ConjurConfiguration for CredentialWith ID
	 *
	 * @param credentialID
	 * @param conjurConfiguration
	 * @param context
	 */
	static void setConjurConfigurationForCredentialWithID(String credentialID, ConjurConfiguration conjurConfiguration,
			ModelObject context) {
		LOGGER.log(Level.FINE, "Start of setConjurConfigurationForCredentialWithID()");
		ConjurSecretCredentials credential = credentialWithID(credentialID, context);

		if (credential != null)
			credential.setConjurConfiguration(conjurConfiguration);

		LOGGER.log(Level.FINE, "End of setConjurConfigurationForCredentialWithID()");
	}

	/**
	 * static method to get secretCredentialIDWithConfigAndContext
	 *
	 * @param credentialID
	 * @param conjurConfiguration
	 * @param context
	 * @param storeContext
	 * @return
	 */
	static Secret getSecretFromCredentialIDWithConfigAndContext(String credentialID,
			ConjurConfiguration conjurConfiguration, ModelObject context, ModelObject storeContext) {

		LOGGER.log(Level.FINE, "Start of  the getSecretFromCredentialIDWithConfigAndContext()");
		Secret secret = null;

		ModelObject effectiveContext = context != null ? context : storeContext;

		LOGGER.log(Level.FINE, "Getting Secret with CredentialID: {0},{1}", new Object[] { context, credentialID });

		ConjurSecretCredentials credential = credentialWithID(credentialID, effectiveContext);

		if (credential != null) {

			LOGGER.log(Level.FINE, "Getting Secret Inside If with CredentialID: " + credential.getId());
			secret = credential.secretWithConjurConfigAndContext(conjurConfiguration, effectiveContext);

		}
		LOGGER.log(Level.FINE, "End of  the getSecretFromCredentialIDWithConfigAndContext()");

		return secret;
	}

}