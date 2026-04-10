package org.conjur.jenkins.conjursecrets;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bind the ConjurSecretCredential based on UserNameCredential
 *
 */
public class ConjurSecretUsernameCredentialsBinding extends MultiBinding<ConjurSecretUsernameCredentials> {

	private static final Logger LOGGER = Logger.getLogger(ConjurSecretUsernameCredentialsBinding.class.getName());

	private String usernameVariable;
	private String passwordVariable;

	@Symbol("conjurSecretUsername")
	@Extension
	public static class DescriptorImpl extends BindingDescriptor<ConjurSecretUsernameCredentials> {
		private final static String DISPLAY_NAME = "Conjur Secret Username credentials";

		@Override
		public String getDisplayName() {
			return DISPLAY_NAME;
		}

		@Override
		public boolean requiresWorkspace() {
			return false;
		}

		@Override
		protected Class<ConjurSecretUsernameCredentials> type() {
			return ConjurSecretUsernameCredentials.class;
		}
	}

	@DataBoundConstructor
	public ConjurSecretUsernameCredentialsBinding(String credentialsId)
	{
		super(credentialsId);
		LOGGER.log(Level.FINEST, String.format("ConjurSecretUsernameCredentialsBinding %s", credentialsId ) );
	}

	/**
	 * @return map containing username and passowrd and assign to MulitEnvironment.
	 */
	@Override
	public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {
		LOGGER.log(Level.FINEST, "Binding UserName and Password");
		Map<String, String> m = new HashMap<>();

		ConjurSecretUsernameCredentials conjurSecretCredential = getCredentials(build);

		if( conjurSecretCredential != null ) {
			conjurSecretCredential.setContext(build);
			m.put(usernameVariable, conjurSecretCredential.getUsername());
			m.put(passwordVariable, conjurSecretCredential.getPassword().getPlainText());
		}

		return new MultiEnvironment(m);
	}

	/**
	 * @return password
	 */
	public String getPasswordVariable() {
		return this.passwordVariable;
	}

	/**
	 * @return username
	 */
	public String getUsernameVariable() {
		return this.usernameVariable;
	}

	/**
	 * set password
	 * 
	 * @param passwordVariable
	 */
	@DataBoundSetter
	public void setPasswordVariable(String passwordVariable) {
		LOGGER.log(Level.FINEST, "Setting Password variable to {0}", passwordVariable);
		this.passwordVariable = passwordVariable;
	}

	/**
	 * set userName
	 * 
	 * @param usernameVariable
	 */
	@DataBoundSetter
	public void setUsernameVariable(String usernameVariable) {
		LOGGER.log(Level.FINEST, "Setting Username variable to {0}", usernameVariable);
		this.usernameVariable = usernameVariable;
	}

	@Override
	protected Class<ConjurSecretUsernameCredentials> type()
	{
		return ConjurSecretUsernameCredentials.class;
	}

	@Override
	public Set<String> variables() {
		return new HashSet<>(Arrays.asList(usernameVariable, passwordVariable));
	}
}
