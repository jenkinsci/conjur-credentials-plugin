package org.conjur.jenkins.api;

import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import okhttp3.OkHttpClient;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.conjur.jenkins.conjursecrets.ConjurSecretUsernameSSHKeyCredentials;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.kohsuke.stapler.Stapler;

import javax.net.ssl.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ConjurAPIUtils class used to build the OkHttp Client object and create
 * CertificateCredentials.
 */
public class ConjurAPIUtils {

	public static final Logger LOGGER = Logger.getLogger(ConjurAPIUtils.class.getName());

	/**
	 * static method to generate CertificateCredentials
	 *
	 * @param configuration ConjurConfiguration
	 * @return CertificateCredentials
	 */
	static synchronized CertificateCredentials certificateFromConfiguration(ConjurConfiguration configuration) {
		return configuration.getCertificateCredentials();
	}

	/**
	 * static method to return the OkHttpClient with the certificate
	 *
	 * @param certificate CertificateCredentials
	 * @return OkHttpClient client
	 */
	static synchronized OkHttpClient httpClientWithCertificate(CertificateCredentials certificate) {
		OkHttpClient client = null;

		try {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(certificate.getKeyStore(), certificate.getPassword().getPlainText().toCharArray());
			KeyManager[] kms = kmf.getKeyManagers();

			KeyStore trustStore = KeyStore.getInstance("JKS");
			trustStore.load(null, null);
			Enumeration<String> e = certificate.getKeyStore().aliases();
			while (e.hasMoreElements()) {
				String alias = e.nextElement();
				trustStore.setCertificateEntry(alias, certificate.getKeyStore().getCertificate(alias));
			}
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);
			TrustManager[] tms = tmf.getTrustManagers();

			SSLContext sslContext;

			sslContext = SSLContext.getInstance("TLSv1.3");
			sslContext.init(kms, tms, new SecureRandom());

			client = new OkHttpClient.Builder()
					.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tms[0]).build();
		} catch (Exception e) {
			throw new IllegalArgumentException("Error configuring server certificates.", e);
		}
		return client;
	}

	/**
	 * Static method to get HttpClinet
	 * We have to use this method as we want to use certificate from configuration
	 *
	 * @param configuration ConjurConfiguration
	 * @return OkHttpClient client
	 */
	public static synchronized OkHttpClient getHttpClient(ConjurConfiguration configuration)
	{
		CertificateCredentials certificate = certificateFromConfiguration(configuration);

		if (certificate != null) {
			return httpClientWithCertificate(certificate);
		}
		return new OkHttpClient.Builder().build();
	}

	/**
	 * Get exception and stacktrace from Exception object
	 *
	 * @param e exception
	 * @return exception and stacktrace in String
	 */
	public static StringBuffer getStringFromException( Exception e )
	{
		StringBuffer message = new StringBuffer();
		message.append( String.format( "%s%s", e.getMessage(), System.lineSeparator() ));

		for (StackTraceElement stackTraceElement : e.getStackTrace()) {
			message.append( String.format( "%s%s", stackTraceElement.toString(), System.lineSeparator() ));
		}
		return message;
	}

	/**
	 * Get default value if passed parameter is null
	 *
	 * @param value set value
	 * @param defaultValue default value
	 * @return String
	 */
	public static String defaultIfBlank( String value, String defaultValue )
	{
		if( value != null && value.length() > 0 )
		{
			return value;
		}
		return defaultValue;
	}

	/**
	 * Validates the Conjur secret credentials by attempting to retrieve a secret.
	 * This method performs several validation steps:
	 * 1. Checks if the variable path is provided
	 * 2. Verifies if the user has ADMINISTER permission
	 * 3. Attempts to retrieve the secret using provided credentials
	 *
	 * @param context       The Jenkins ItemGroup context where the credential validation is performed
	 * @param credential    The Conjur credential object to validate
	 * @return FormValidation containing the result of validation:
	 *         - ok: if secret was successfully retrieved
	 *         - error: if validation failed, with detailed error message
	 * @throws SecurityException if the user doesn't have ADMINISTER permission
	 */
	public static FormValidation validateCredential(
			ItemGroup<Item> context,
			ConjurSecretCredentials credential) {

		Jenkins.get().checkPermission(Jenkins.ADMINISTER);
		String secretValue = null;

		try {
			credential.setContext(context);
			if (credential instanceof ConjurSecretUsernameSSHKeyCredentials) {
				secretValue = ((ConjurSecretUsernameSSHKeyCredentials) credential).getPrivateKey();
			} else {
				LOGGER.log(Level.FINEST, String.format("Context set %s", context.getDisplayName()));
				Secret secret = credential.getSecret();
				secretValue = secret != null ? secret.getPlainText() : null;
			}
		} catch (InvalidConjurSecretException e) {
			try {
				credential.setContext(getItemFromReferer());
				if (credential instanceof ConjurSecretUsernameSSHKeyCredentials) {
					secretValue = ((ConjurSecretUsernameSSHKeyCredentials) credential).getPrivateKey();
				} else {
					Secret secret = credential.getSecret();
					secretValue = secret != null ? secret.getPlainText() : null;
				}
				return FormValidation
						.ok("Successfully retrieved secret string");
			} catch (Exception ex) {
				LOGGER.log(Level.FINEST, "FAILED to retrieve secret!");
			}
			return FormValidation.error("FAILED to retrieve secret: \n" + e + "\nPlease check Conjur configuration or add credentials from credentials page");
		}

		if (secretValue == null || secretValue.isEmpty()) {
			return FormValidation.error("FAILED to retrieve secret!" );
		}
		return FormValidation
				.ok("Successfully retrieved secret string");
	}

	/**
	 * Retrieves the Jenkins Item from the referer URL in the current HTTP request.
	 * Extracts the full job path from the referer URL and uses it to locate the corresponding Item.
	 *
	 * @return Jenkins Item obtained from the referer URL, or null if the Item cannot be found
	 * @throws URISyntaxException if the referer URL is malformed
	 */
	public static Item getItemFromReferer() throws URISyntaxException {
		String referer = Stapler.getCurrentRequest().getReferer();
		String jobPath = extractJobPathFromUrl(new URI(referer).getPath());
		return Jenkins.get().getItemByFullName(jobPath);
	}

	private static String extractJobPathFromUrl(String urlPath) {
		if (urlPath.contains("/job/")) {
			return Arrays.stream(urlPath.split("/job/"))
					.filter(segment -> !segment.isEmpty())
					.map(segment -> segment.replaceAll("/.*", ""))
					.collect(Collectors.joining("/"));
		} else throw new IllegalArgumentException("Invalid job path: " + urlPath);
	}
}