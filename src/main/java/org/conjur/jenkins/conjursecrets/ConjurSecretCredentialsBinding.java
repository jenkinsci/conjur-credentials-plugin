package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

/**
 * ConjurSecretCredentialsBinding entry level class to when build is invoked to
 * authorize and retrieve secrets
 */
public class ConjurSecretCredentialsBinding extends MultiBinding<ConjurSecretCredentials> {

	private static final Logger LOGGER = Logger.getLogger(ConjurSecretCredentialsBinding.class.getName());

	private String variable;
	private String credentialsId;

	@Symbol("conjurSecretCredential")
	@Extension
	public static class DescriptorImpl extends BindingDescriptor<ConjurSecretCredentials> {
		private static final String DISPLAY_NAME = "Conjur Secret credentials";

		@Override
		public String getDisplayName() {
			return DISPLAY_NAME;
		}

		@Override
		public boolean requiresWorkspace() {
			return false;
		}

		@Override
		protected Class<ConjurSecretCredentials> type() {
			return ConjurSecretCredentials.class;
		}
	}

	@DataBoundConstructor
	public ConjurSecretCredentialsBinding(String credentialsId) {
		super(credentialsId);
		this.credentialsId = credentialsId;
	}

	/**
	 * Bind method invoked on Jenkins build process
	 */
	@Override
	public MultiEnvironment bind(Run<?, ?> build, FilePath workSpace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {

		MultiEnvironment multiEnv;

		long start = System.nanoTime();

		try {
			ConjurSecretCredentials conjurSecretCredential;

			LOGGER.log(Level.FINEST, String.format("bind to context %s", build.getDisplayName() ) );

			conjurSecretCredential = getCredentialsFor(build);

			conjurSecretCredential.setContext(build);

			multiEnv = new MultiEnvironment(
					Collections.singletonMap(variable, conjurSecretCredential.getSecret().getPlainText()));

		}catch( CredentialNotFoundException e )
		{
			LOGGER.log(Level.SEVERE, String.format("No credentials found for: %s", build.getFullDisplayName() ) );

			multiEnv = new MultiEnvironment(
					new HashMap<String,String>());
		}
		long end = System.nanoTime();
		long execution = end - start;

		LOGGER.log(Level.FINEST, String.format("Execution of Class ConjurSecretCredentialsBinding. Method bind() time: %d miliseconds", (int)(execution / 1000000d) ) );

		return multiEnv;
	}

	/**
	 * Get Credentials
	 *
	 * @param build current context
	 * @return Credentials assigned to context
	 * @param <C> credential type
	 * @throws IOException
     */
	@SuppressWarnings("unchecked")
	private final @NonNull <C> C getCredentialsFor(@NonNull Run<?, ?> build) throws IOException {
		long start = System.nanoTime();
		LOGGER.log(Level.FINEST, String.format("getCredentialsFor context %s credentialid %s" , build.getFullDisplayName(), credentialsId ) );

		String newCredentialId = credentialsId.replaceAll("([${}])", "");
		IdCredentials cred = CredentialsProvider.findCredentialById(newCredentialId, ConjurSecretCredentials.class, build);

		if(cred==null)
		{
			throw new CredentialNotFoundException("Could not find credentials entry with ID '" + credentialsId + "'");
		}

		if (!type().isInstance(cred))
		{
			Descriptor<?> expected = Jenkins.getActiveInstance().getDescriptor(type());
			long end = System.nanoTime();
			long execution = end - start;
			LOGGER.log(Level.OFF, String.format("Execution of Class ConjurSecretCredentialsBinding -->Method getCredentialsFor() time: %d milliseconds", (int)(execution / 1000000d) ) );
			throw new CredentialNotFoundException(
					"Credentials '" + credentialsId + "' not found '" + cred + "' where '"
							+ (expected != null ? expected.getDisplayName() : type().getName()) + "' was expected");
		}
		CredentialsProvider.track(build, cred);

		return (C) type().cast(cred);
	}

	/**
	 *  @return variable
	 **/
	public String getVariable() {
		return this.variable;
	}

	/**
	 * @param variable
	 * set the variable
	 **/
	@DataBoundSetter
	public void setVariable(String variable) {
		LOGGER.log(Level.FINEST, "Setting variable to {0}", variable);
		this.variable = variable;
	}

	@Override
	protected Class<ConjurSecretCredentials> type() {
		return ConjurSecretCredentials.class;
	}

	@Override
	public Set<String> variables() {
		return Collections.singleton(variable);
	}
}
