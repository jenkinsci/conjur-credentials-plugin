package org.conjur.jenkins.api;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.ConjurJITJobProperty;
import org.conjur.jenkins.configuration.FolderConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.impl.JwtToken;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.security.ACL;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The ConjurAPI class provides the service to authenticate and retrieve secrets
 * based on API Key/JWT authentication using the Conjur Configuration details
 * configured either through the Jenkins Global configuration form or as
 * environment. The request to authenticate (API Key/JWT) will be processed in
 * Conjur Server and return authorised(200-OK) or unauthorised code
 * (401-UnAuthorized) code. The request to fetch the secrets based on the
 * credetnialID will be processed only if the authentication is successful. Upon
 * successful authentication , the request to fetch the secret is processed and
 * returns secrets if available. The request to fetch secrets first checks if
 * the credentialId is available and having grant permission based on identity
 * If CredentialID is not found ,returns <b>Credential NotFound message</b>. If
 * CredentialID does not have permission , returns <b>401 UnAuthorized
 * message</b>. If secrets not available for the CredentialID ,returns
 * <b>Credential ID is empty message</b>.
 */
public class ConjurAPI {
	/**
	 * static constructor to set the Conjur Auth Configuration Info
	 */
	public static class ConjurAuthnInfo {
		public String applianceUrl;
		public String authnPath;
		public String account;
		public String login;
		public String apiKey;
	}

	private static final Logger LOGGER = Logger.getLogger(ConjurAPI.class.getName());

	/**
	 * Set the ConjurAuthnInfo with the environment variables
	 * 
	 * @param conjurAuthn
	 */

	private static  void defaultToEnvironment(ConjurAuthnInfo conjurAuthn) {
		LOGGER.log(Level.FINE, "Start of defaultToEnvironment()");

		Map<String, String> env = System.getenv();
		if (conjurAuthn.applianceUrl == null && env.containsKey("CONJUR_APPLIANCE_URL"))
			conjurAuthn.applianceUrl = env.get("CONJUR_APPLIANCE_URL");
		if (conjurAuthn.account == null && env.containsKey("CONJUR_ACCOUNT"))
			conjurAuthn.account = env.get("CONJUR_ACCOUNT");
		if (conjurAuthn.login == null && env.containsKey("CONJUR_AUTHN_LOGIN"))
			conjurAuthn.login = env.get("CONJUR_AUTHN_LOGIN");
		if (conjurAuthn.apiKey == null && env.containsKey("CONJUR_AUTHN_API_KEY"))
			conjurAuthn.apiKey = env.get("CONJUR_AUTHN_API_KEY");
		LOGGER.log(Level.FINE, "End of defaultToEnvironment()");
	}

	/**
	 * Method to build the client authentication API Key/JWT request based on the
	 * ConjurConfiguration.
	 * 
	 * @param client        OkHttp builds HTTP/HTTP/2 client that shares the same
	 *                      connection,thread pool and configuration.
	 * @param configuration ConjurConfiguration object containing
	 *                      account,applianceUrl,credentialID,certificateCredentialID,ownerFullName.
	 * @param context       current context in which Jenkins Job are running
	 * @return status code to 200-OK if request is authenticated or 401 if
	 *         Unauthorized
	 * @throws IOException in case of error connecting to Conjur Server
	 */
	@SuppressFBWarnings
	public static  String getAuthorizationToken(OkHttpClient client, ConjurConfiguration configuration,
			ModelObject context) throws IOException {
		LOGGER.log(Level.FINE, "Start of getAuthorizationToken()");
		LOGGER.log(Level.INFO,
				"getAuthorizationToken input params" + "Client:" + client + "Configuration:" + configuration);
		String resultingToken = null;

		List<UsernamePasswordCredentials> availableCredentials = null;

		availableCredentials = CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.get(),
				ACL.SYSTEM, Collections.<DomainRequirement>emptyList());

		if (context != null) {
			if (context instanceof Run) {
				availableCredentials.addAll(CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class,
						((Run) context).getParent(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
			} else {
				if ((context instanceof AbstractItem)) {
					availableCredentials.addAll(CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class,
							(AbstractItem) context, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
				}
			}
		}

		ConjurAuthnInfo conjurAuthn = getConjurAuthnInfo(configuration, availableCredentials, context);
		GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
		if(globalConfig != null) {
		ConjurConfiguration globalConjurConfig = globalConfig.getConjurConfiguration();
			if(conjurAuthn.account ==null || conjurAuthn.account.isEmpty()){
				conjurAuthn.account = globalConjurConfig.getAccount();
			}
			if(conjurAuthn.applianceUrl ==null || conjurAuthn.applianceUrl.isEmpty()){
				conjurAuthn.applianceUrl = globalConjurConfig.getApplianceURL();
			}
		}
		if (globalConfig != null && globalConfig.getEnableJWKS()) {
			LOGGER.log(Level.FINE, "JWT is enabled.");
			if (!globalConfig.getEnableIdentityFormatFieldsFromToken())// Simplified JWT is disabled
			{
				LOGGER.log(Level.FINE, "Simplified JWT is disabled.");
				List<String> identityFields = Arrays.asList(globalConfig.getIdentityFormatFieldsFromToken().split(","));
				if(!identityFields.contains("jenkins_full_name"))
				{
					if(!identityFields.contains("jenkins_parent_full_name") || !identityFields.contains("jenkins_name"))
					{
						throw new RuntimeException(
								"Invalid configuration on conjur jenkins plugin. Ensure Identity format fields are configured correctly.");
					}
				}
			}
		}

		Request request = null;
		if (conjurAuthn.login != null && conjurAuthn.apiKey != null) {
			LOGGER.log(Level.FINE, "Creating authentication request for API Key authentication with Conjur");
			request = new Request.Builder()
					.url(String.format("%s/%s/%s/%s/authenticate", conjurAuthn.applianceUrl, conjurAuthn.authnPath,
							conjurAuthn.account, URLEncoder.encode(conjurAuthn.login, "utf-8")))
					.post(RequestBody.create(MediaType.parse("text/plain"), conjurAuthn.apiKey)).build();
		} else if (conjurAuthn.authnPath != null && conjurAuthn.apiKey != null) {
			LOGGER.log(Level.FINE, "Creating authentication request for JWT authentication with Conjur");
			String authnPath = conjurAuthn.authnPath.indexOf("/") == -1 ? "authn-jwt/" + conjurAuthn.authnPath
					: conjurAuthn.authnPath;
			LOGGER.log(Level.FINE, "Authenticating with Conjur (JWT) authnPath={0}", authnPath);
			request = new Request.Builder()
					.url(String.format("%s/%s/%s/authenticate", conjurAuthn.applianceUrl, authnPath,
							conjurAuthn.account))
					.post(RequestBody.create(MediaType.parse("text/plain"), conjurAuthn.apiKey)).build();

		}

		if (request != null) {
			Response response = client.newCall(request).execute();
			resultingToken = Base64.getEncoder().withoutPadding()
					.encodeToString(response.body().string().getBytes("UTF-8"));
			LOGGER.log(Level.FINEST,
					() -> "Conjur Authenticate response " + response.code() + " - " + response.message());
			if (response.code() != 200) {

				throw new IOException("Error authenticating to Conjur [" + response.code() + " - " + response.message()
						+ "\n" + resultingToken);
			}
		} else {
			LOGGER.log(Level.FINE, "Failed to authenticate with conjur server");
		}
		return resultingToken;
	}

	/**
	 * Retrieve the ConjurAuthnInfo configured for Jenkins build
	 * 
	 * @param ConjurConfiguration               from Jenkins configuration
	 * @param List<UsernamePasswordCredentials> availabeCredentials
	 * @param Jenkins                           ModelObject context
	 * @return ConjurAuthnInfo
	 */
	public static ConjurAuthnInfo getConjurAuthnInfo(ConjurConfiguration configuration,
			List<UsernamePasswordCredentials> availableCredentials, ModelObject context) {
		LOGGER.log(Level.FINE, "Start of getConjurAuthnInfo()");
		ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();

		if (configuration != null) {

			if (availableCredentials != null) {
				initializeWithCredential(conjurAuthn, configuration.getCredentialID(), availableCredentials);
			}

			String applianceUrl = configuration.getApplianceURL();
			if (applianceUrl != null && !applianceUrl.isEmpty()) {
				conjurAuthn.applianceUrl = applianceUrl;
			}
			String account = configuration.getAccount();
			if (account != null && !account.isEmpty()) {
				conjurAuthn.account = account;
			}
			// Default authentication will be authn
			conjurAuthn.authnPath = "authn";
		}
		LOGGER.log(Level.FINE, "getConjurAuthnInfo() calling defaultToEnvironment");
		// Default to Environment variables if not values present
		defaultToEnvironment(conjurAuthn);

		LOGGER.log(Level.FINE, "Check for Just-In-time Credential Access if no login and apikey {0}", conjurAuthn);
		// Check for Just-In-time Credential Access if no login and apikey
		if (conjurAuthn.login == null && conjurAuthn.apiKey == null && context != null) {
			setConjurAuthnForJITCredentialAccess(context, conjurAuthn);
		}
		LOGGER.log(Level.FINE, "End of getConjurAuthnInfo()");
		return conjurAuthn;
	}
	private static void setConjurAuthnForJITCredentialAccess(ModelObject context, ConjurAuthnInfo conjurAuthn) {
		LOGGER.log(Level.FINE, "Start of setConjurAuthnForJITCredentialAccess()");
		String token = JwtToken.getToken(context);
		GlobalConjurConfiguration globalconfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);

		if (token != null && globalconfig != null) {
			conjurAuthn.login = null;
			conjurAuthn.authnPath = globalconfig.getAuthWebServiceId();
			conjurAuthn.apiKey = "jwt=" + token;
		}
		LOGGER.log(Level.FINE, "End of setConjurAuthnForJITCredentialAccess()");
	}

	/**
	 * This method gets the {@link ConjurAuthIno} data and retrieve the secret for the valid authenticationToken,account
	 * variablePath. The request to fetch the secret are build using the OkHttp client.
	 * 
	 * @param client   OkHttp builds HTTP/HTTP/2 client that shares the same connection,thread pool and configuration.
	 * @param configuration  {@link ConjurConfiguration} containing the Conjur authentication parameters 
	 * @param authToken	 token to authenticate the request.
	 * @param variablePath  for which to retrieve the secrets
	 * @return the secrets for the specified variablePath
	 * @throws IOException
	 */
	@SuppressFBWarnings
	public static String getSecret(OkHttpClient client, ConjurConfiguration configuration, String authToken,
			String variablePath) throws IOException {
		LOGGER.log(Level.FINE, "Start of getSecret()");
		
		ConjurAuthnInfo conjurAuthn = getConjurAuthnInfo(configuration, null, null);

		LOGGER.log(Level.FINEST, "Fetching secret from Conjur Server");
		Request request = new Request.Builder().url(
				String.format("%s/secrets/%s/variable/%s", conjurAuthn.applianceUrl, conjurAuthn.account, variablePath))
				.get().addHeader("Authorization", "Token token=\"" + authToken + "\"").build();

		Response response = client.newCall(request).execute();
		String result = response.body().string();
		LOGGER.log(Level.FINEST, () -> "Fetch secret [" + variablePath + "] from Conjur response " + response.code()
				+ " - " + response.message());
		if (response.code() != 200) {
			throw new IOException("Error fetching secret from Conjur [" + response.code() + " - " + response.message()
					+ "\n" + result);
		}
		
		LOGGER.log(Level.FINE, "End of getSecret()");
		return result;
	}

	/**
	 * Log the Conjur Configuration details
	 * 
	 * @param conjurConfiguration log the ConjurConfiguration from Jenkins
	 *                            configuration
	 * @return ConjurConfiguration log the Conjur Configuration parameters
	 */
	public static ConjurConfiguration logConjurConfiguration(ConjurConfiguration conjurConfiguration) {
		LOGGER.log(Level.FINE, "Start of logConjurConfiguration()");
		if (conjurConfiguration != null) {
			LOGGER.log(Level.FINEST, "Conjur configuration provided");
			LOGGER.log(Level.FINEST, "Conjur Configuration Appliance Url:{0} ", conjurConfiguration.getApplianceURL());
			LOGGER.log(Level.FINEST, "Conjur Configuration Account: {0}", conjurConfiguration.getAccount());
			LOGGER.log(Level.FINEST, "Conjur Configuration credential ID:{0} ", conjurConfiguration.getCredentialID());
		}
		LOGGER.log(Level.FINE, "End of logConjurConfiguration()");
		return conjurConfiguration;
	}

	private static void initializeWithCredential(ConjurAuthnInfo conjurAuthn, String credentialID,
			List<UsernamePasswordCredentials> availableCredentials) {
		LOGGER.log(Level.FINE, "Start of initializeWithCredential()");
		if (credentialID != null && !credentialID.isEmpty()) {
			LOGGER.log(Level.FINEST, "Retrieving Conjur credential stored in Jenkins");
			UsernamePasswordCredentials credential = CredentialsMatchers.firstOrNull(availableCredentials,
					CredentialsMatchers.withId(credentialID));
			if (credential != null) {
				conjurAuthn.login = credential.getUsername();
				conjurAuthn.apiKey = credential.getPassword().getPlainText();
			}
		}
		LOGGER.log(Level.FINE, "End of initializeWithCredential()");
	}

	/**
	 * Retrieve the configuration specific to Context
	 * 
	 * @param Jenkins ModelObject context
	 * @param Jenkins ModelObject storeContext
	 * @return the Conjur Configuration based on the Jenkins ModelOjbect
	 */

	public static ConjurConfiguration getConfigurationFromContext(ModelObject context, ModelObject storeContext) {
		LOGGER.log(Level.FINE, "Start of getConfigurationFromContext()");
		ModelObject effectiveContext = context != null ? context : storeContext;

		Item contextObject = null;
		ConjurJITJobProperty conjurJobConfig = null;

		if (effectiveContext instanceof Run) {
			LOGGER.log(Level.FINE, "getConfigurationFromContext():instanceOf Run");
			Run run = (Run) effectiveContext;
			conjurJobConfig = (ConjurJITJobProperty) run.getParent().getProperty(ConjurJITJobProperty.class);
			contextObject = run.getParent();
		} else if (effectiveContext instanceof AbstractItem) {
			LOGGER.log(Level.FINE, "getConfigurationFromContext():instanceOf AbstractItem");
			contextObject = (Item) effectiveContext;
		}

		ConjurConfiguration conjurConfig = GlobalConjurConfiguration.get().getConjurConfiguration();

		if (effectiveContext == null) {
			LOGGER.log(Level.FINE, "getConfigurationFromContext():Context null,logging the configuration");
			return ConjurAPI.logConjurConfiguration(conjurConfig);
		}

		if (conjurJobConfig != null && !conjurJobConfig.getInheritFromParent()) {
			LOGGER.log(Level.FINE, "getConfigurationFromContext():Configuration from Job and inheritedParent");
			// Taking the configuration from the Job
			return ConjurAPI.logConjurConfiguration(conjurJobConfig.getConjurConfiguration());
		}

		ConjurConfiguration inheritedConfig = inheritedConjurConfiguration(contextObject);
		if (inheritedConfig != null) {
			return ConjurAPI.logConjurConfiguration(inheritedConfig);
		}
		LOGGER.log(Level.FINE, "End of getConfigurationFromContext()");
		return ConjurAPI.logConjurConfiguration(conjurConfig);

	}

	@SuppressWarnings("unchecked")
	private static ConjurConfiguration inheritedConjurConfiguration(Item job) {
		LOGGER.log(Level.FINE, "Start of inheritedConjurConfiguration()");
		for (ItemGroup<? extends Item> g = job != null ? job.getParent()
				: null; g instanceof AbstractFolder; g = ((AbstractFolder<? extends Item>) g).getParent()) {
			FolderConjurConfiguration fconf = ((AbstractFolder<?>) g).getProperties()
					.get(FolderConjurConfiguration.class);
			if (!(fconf == null || fconf.getInheritFromParent())) {
				// take the folder Conjur Configuration
				return fconf.getConjurConfiguration();
			}
		}
		LOGGER.log(Level.FINE, "End of inheritedConjurConfiguration()");
		return null;
	}

	private ConjurAPI() {
		super();
	}

}
