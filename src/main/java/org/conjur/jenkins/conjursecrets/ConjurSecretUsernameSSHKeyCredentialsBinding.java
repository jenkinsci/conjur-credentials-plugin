package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Class to bind secrets based on SSHKeyCredential
 *
 */
public class ConjurSecretUsernameSSHKeyCredentialsBinding extends MultiBinding<ConjurSecretUsernameSSHKeyCredentials> {

	private static final Logger LOGGER = Logger.getLogger(ConjurSecretUsernameSSHKeyCredentialsBinding.class.getName());

	private String usernameVariable;
	private String secretVariable;

	@Symbol("conjurSecretUsernameSSHKey")
	@Extension
	public static class DescriptorImpl extends BindingDescriptor<ConjurSecretUsernameSSHKeyCredentials> {
		private static final String DISPLAY_NAME = "Conjur Secret Username SSHKey credentials";

		@Override
		public String getDisplayName() {
			return DISPLAY_NAME;
		}

		@Override
		public boolean requiresWorkspace() {
			return false;
		}

		@Override
		protected Class<ConjurSecretUsernameSSHKeyCredentials> type() {
			return ConjurSecretUsernameSSHKeyCredentials.class;
		}
	}

	@DataBoundConstructor
	public ConjurSecretUsernameSSHKeyCredentialsBinding(String credentialsId) {
		super(credentialsId);
	}

	/**
	 * Binding UserName and SSHKey
	 *
	 * @return map with username ,secretVariable assign to MultiEnvironment
	 */
	@Override
	public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		LOGGER.log( Level.FINEST, String.format("Bind ConjurSecretUsernameSSHKeyCredentials to %s", build.getDisplayName() ) );
		Map<String, String> m = new HashMap<>();

		ConjurSecretUsernameSSHKeyCredentials conjurSecretCredential = getCredentials(build);

		m.put(usernameVariable, conjurSecretCredential.getUsername());
		m.put(secretVariable, conjurSecretCredential.getPrivateKey());

		return new MultiEnvironment(m);
	}

	/**
	 * Return the secretVarialbe
	 * @return secretVaraible f
	 */
	public String getSecretVariable() {
		return this.secretVariable;
	}

	/**
	 * Return the UserNameVariable
	 * @return userNameVaraible
	 */
	public String getUsernameVariable() {
		return this.usernameVariable;
	}

	/**
	 * Sets secretvariable
	 * @param secretVariable
	 */
	@DataBoundSetter
	public void setSecretVariable(String secretVariable) {
		this.secretVariable = secretVariable;
	}

	/**
	 * Sets userNamevariable
	 * @param usernameVariable
	 */
	@DataBoundSetter
	public void setUsernameVariable(String usernameVariable) {
		this.usernameVariable = usernameVariable;
	}

	/**
	 *
	 * @return ConjurSecretUsernameSSHKeyCredentials class
	 */
	@Override
	protected Class<ConjurSecretUsernameSSHKeyCredentials> type() {
		return ConjurSecretUsernameSSHKeyCredentials.class;
	}

	@Override
	public Set<String> variables() {
		return new HashSet<>(Arrays.asList(usernameVariable, secretVariable));
	}
}
