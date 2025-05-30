package org.conjur.jenkins.conjursecrets;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * ConjurSecretUsernameSSHKeyCredentials Interace to get DisplayNamecontext,ConjurConfiguration 
 * and NameProvider based SSHKeyCredentails
 *
 */
@NameWith(value = ConjurSecretUsernameSSHKeyCredentials.NameProvider.class, priority = 1)
public interface ConjurSecretUsernameSSHKeyCredentials extends SSHUserPrivateKey, ConjurSecretCredentials {

	/**
	 *
	 * @return display name
	 */
	String getDisplayName();

	/**
	 * Get Private Key
	 * @return private key
	 */
	String getPrivateKey( );

	public static class NameProvider extends CredentialsNameProvider<StandardUsernameCredentials> {
		@NonNull
		@Override
		public String getName(@NonNull StandardUsernameCredentials c) {
			return "ConjurSecretUsernameSSHKey:" + c.getUsername() + "/*ConjurSecretUsernameSSHKey*" + " (" + c.getDescription() + ")";
		}
	}
}
