package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.ModelObject;
import hudson.util.Secret;

import java.util.logging.Logger;

@NameWith(value = ConjurSecretCredentials.NameProvider.class, priority = 1)
public interface ConjurSecretCredentials extends StandardCredentials {

	Logger LOGGER = Logger.getLogger(ConjurSecretCredentials.class.getName());

	/**
	 * Inner class to retrieve the displayName for the job
	 */
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

	String getDisplayName();

	String getNameTag();

	Secret getSecret( );

	void setContext(ModelObject context);

	ModelObject getContext();

	void setInheritedContext(ModelObject context);

	ModelObject getInheritedContext();

	void setStoredInConjurStorage( boolean storedInConjurStorage );

	boolean storedInConjurStorage();
}