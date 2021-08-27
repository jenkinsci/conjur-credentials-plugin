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

public class ConjurDirectUsernameCredentialsBinding extends MultiBinding<ConjurSecretUsernameCredentials> {

	@Symbol("conjurDirectUsername")
	@Extension
	public static class DescriptorImpl extends BindingDescriptor<ConjurSecretUsernameCredentials> {

		@Override
		public String getDisplayName() {
			return "Conjur Secret Username credentials";
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
	private static final Logger LOGGER = Logger.getLogger(ConjurDirectUsernameCredentialsBinding.class.getName());

	private String usernameVariable;

	private String passwordVariable;

	@DataBoundConstructor
	public ConjurDirectUsernameCredentialsBinding(String credentialsId) {
		super(credentialsId);
	}

	@Override
	public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		LOGGER.log(Level.INFO, "Binding UserName and Password");

		ConjurSecretUsernameCredentials conjurSecretCredential = getCredentials(build);
		conjurSecretCredential.setContext(build);

		Map<String, String> m = new HashMap<>();
		m.put(usernameVariable, conjurSecretCredential.getUsername());
		m.put(passwordVariable, conjurSecretCredential.getPassword().getPlainText());
		return new MultiEnvironment(m);

	}

	public String getPasswordVariable() {
		return this.passwordVariable;
	}

	public String getUsernameVariable() {
		return this.usernameVariable;
	}

	@DataBoundSetter
	public void setPasswordVariable(String passwordVariable) {
		LOGGER.log(Level.INFO, "Setting Password variable to {0}", passwordVariable);
		this.passwordVariable = passwordVariable;
	}

	@DataBoundSetter
	public void setUsernameVariable(String usernameVariable) {
		LOGGER.log(Level.INFO, "Setting Username variable to {0}", usernameVariable);
		this.usernameVariable = usernameVariable;
	}

	@Override
	protected Class<ConjurSecretUsernameCredentials> type() {
		return ConjurSecretUsernameCredentials.class;
	}

	@Override
	public Set<String> variables() {
		return new HashSet<>(Arrays.asList(usernameVariable, passwordVariable));
	}

}
