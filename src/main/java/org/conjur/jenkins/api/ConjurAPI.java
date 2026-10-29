package org.conjur.jenkins.api;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.*;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.authenticator.AbstractAuthenticator;
import org.conjur.jenkins.authenticator.ConjurAPIKeyAuthenticator;
import org.conjur.jenkins.authenticator.ConjurCertAuthenticator;
import org.conjur.jenkins.authenticator.ConjurJWTAuthenticator;
import org.conjur.jenkins.configuration.*;
import org.conjur.jenkins.conjursecrets.*;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import org.conjur.jenkins.CjplCode;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.CjplCode.*;

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

    private static final Logger LOGGER = Logger.getLogger(ConjurAPI.class.getName());
    private static volatile AbstractAuthenticator authenticator = null;

    /**
     * Package-private for tests in this package; {@link ConjurTokenCache} is itself package-private
     * so nothing outside {@code org.conjur.jenkins.api} can reach this field in production.
     */
    private static final ConjurTokenCache tokenCache = new ConjurTokenCache();

    /**
     * Constructor
     */
    private ConjurAPI() {
        super();
    }

    /**
     * Set the ConjurAuthnInfo with the environment variables
     *
     * @param conjurAuthn authentication info, will be filled with specified data used to authenticate
     */

    private static void defaultToEnvironment(ConjurAuthnInfo conjurAuthn) {
        Map<String, String> env = System.getenv();
        if (conjurAuthn.getApplianceUrl() == null && env.containsKey("CONJUR_APPLIANCE_URL"))
            conjurAuthn.setApplianceUrl(env.get("CONJUR_APPLIANCE_URL"));
        if (conjurAuthn.getAccount() == null && env.containsKey("CONJUR_ACCOUNT"))
            conjurAuthn.setAccount(env.get("CONJUR_ACCOUNT"));
        if (conjurAuthn.getLogin() == null && env.containsKey("CONJUR_AUTHN_LOGIN"))
            conjurAuthn.setLogin(env.get("CONJUR_AUTHN_LOGIN"));
        if (conjurAuthn.getApiKey() == null && env.containsKey("CONJUR_AUTHN_API_KEY"))
            conjurAuthn.setApiKey(env.get("CONJUR_AUTHN_API_KEY").getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Return authenticator by name
     *
     * @param name name of authenticator
     * @return authenticator class object
     */
    public static AbstractAuthenticator getAuthenticatorByName(String name) {
        AbstractAuthenticator chosenAuthenticator = null;

        if (name != null) {
            if (name.equalsIgnoreCase("JWT")) {
                chosenAuthenticator = new ConjurJWTAuthenticator();
            } else if (name.equalsIgnoreCase("APIKey")) {
                chosenAuthenticator = new ConjurAPIKeyAuthenticator();
            } else if (name.equalsIgnoreCase("Certificate")) {
                chosenAuthenticator = new ConjurCertAuthenticator();
            }
        }
        return chosenAuthenticator;
    }

    /**
     * Set authenticator using a pre-resolved {@link GlobalConjurConfiguration}.
     * Accepts {@code null} safely — falls back to the APIKey default when the global
     * configuration is absent (same behaviour as the no-arg overload).
     */
    private static void setAuthenticator(GlobalConjurConfiguration globalConfig) {
        if (globalConfig != null) {
            if (authenticator == null) {
                authenticator = getAuthenticatorByName(globalConfig.getSelectAuthenticator());
            } else    // if there is authenticator set but setting was changed
            {
                if (!authenticator.getName().equalsIgnoreCase(globalConfig.getSelectAuthenticator()))    // someone changed authenticator
                {
                    authenticator = getAuthenticatorByName(globalConfig.getSelectAuthenticator());
                }
            }
        }    // globalConfig != null

        // if authenticator not set use default
        if (authenticator == null) {
            authenticator = new ConjurAPIKeyAuthenticator();
        }

        LOGGER.log(Level.FINEST, String.format("Authenticator set to: %s", authenticator.getName()));
    }

    /**
     * Set authenticator — resolves {@link GlobalConjurConfiguration} and delegates to
     * {@link #setAuthenticator(GlobalConjurConfiguration)}.
     *
     * @return the resolved {@link GlobalConjurConfiguration}, or {@code null} when absent
     */
    private static GlobalConjurConfiguration setAuthenticator() {
        GlobalConjurConfiguration globalConfig =
                GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        setAuthenticator(globalConfig);
        return globalConfig;
    }

    /**
     * Method to build the client authentication API Key/JWT request based on the
     * ConjurConfiguration.
     *
     * @param conjurAuthn authentication object which contains data used to authenticate service
     * @param context     current context in which Jenkins Job is running
     * @return status code to 200-OK if request is authenticated or 401 if
     * Unauthorized
     * @throws IOException in case of error connecting to Conjur Server
     */
    public static byte[] getAuthorizationToken(ConjurAuthnInfo conjurAuthn,
                                               ModelObject context) throws IOException {
        GlobalConjurConfiguration globalConfig =
                GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        setAuthenticator(globalConfig);

        if (!authenticator.isCacheable()) {
            return authenticator.getAuthorizationToken(conjurAuthn, context);
        }

        String cacheKey = buildCacheKey(conjurAuthn, context, globalConfig);
        if (cacheKey == null) {
            // GlobalConjurConfiguration unavailable — bypass cache to prevent all identities
            // collapsing to the same key (would allow cross-job token sharing).
            LOGGER.log(Level.WARNING,
                    "GlobalConjurConfiguration unavailable; bypassing token cache to prevent identity collision");
            return authenticator.getAuthorizationToken(conjurAuthn, context);
        }

        // getOrLoad: Guava ensures only ONE authentication call fires per key at a time.
        // All concurrent callers for the same key block until the in-flight load completes.
        byte[] token = tokenCache.getOrLoad(cacheKey,
                () -> authenticator.getAuthorizationToken(conjurAuthn, context));
        if (token != null) {
            LOGGER.log(Level.FINE, () -> String.format(
                    "Conjur access token served for identity [%s]", cacheKey));
        }
        return token;
    }

    /**
     * A cached token may only be shared by builds resolving to the same Conjur host identity, so
     * the key must capture everything determining that identity: the Conjur appliance URL, the
     * authn-jwt service ID, the account, and the JWT {@code sub} claim. Components are
     * URL-encoded so a value containing the separator cannot collide with a different identity.
     *
     * <p>Returns {@code null} when {@code globalConfig} is {@code null}. A null key signals the
     * caller to bypass the cache entirely: without a globalConfig the sub-claim cannot be
     * computed, and all identities would collapse to an identical key — enabling cross-job token
     * sharing. The caller is responsible for resolving {@code GlobalConjurConfiguration} and
     * passing it in; this keeps the method free of hidden Jenkins dependencies.
     */
    private static String buildCacheKey(ConjurAuthnInfo conjurAuthn, ModelObject context,
                                        GlobalConjurConfiguration globalConfig) {
        if (globalConfig == null) {
            return null;
        }
        String identity = JwtToken.computeSubClaim(context, globalConfig);
        return String.join("|",
                encodeKeyComponent(conjurAuthn.getApplianceUrl()),
                encodeKeyComponent(conjurAuthn.getAuthnPath()),
                encodeKeyComponent(conjurAuthn.getAccount()),
                encodeKeyComponent(identity));
    }

    private static String encodeKeyComponent(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * {@link #getConjurSecret} carries no status field: it reports a non-200 as an
     * {@link IOException} whose {@link CjplCode#CONJUR_SECRET_FETCH_ERROR} message renders the code
     * as {@code [401 - Unauthorized]}, so that is what gets matched. A false positive costs one
     * redundant authenticate call and nothing else.
     */
    private static boolean isUnauthorized(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains("[401 ");
    }

    /**
     * Recovers from Conjur rejecting a token the cache still considered usable — a revoked token,
     * a rotated signing key, or clock skew beyond {@link ConjurTokenCache#TOKEN_CACHE_BUFFER_SECONDS}.
     * Evicting matters as much as retrying: otherwise every other build keeps replaying the dead
     * token until it expires naturally.
     *
     * <p>After eviction, re-authentication goes through {@code tokenCache.getOrLoad}, so
     * concurrent builds that all received a 401 simultaneously only fire one re-auth call;
     * the rest receive the same fresh token.
     *
     * @param cause the original 401, re-thrown when re-authentication yields nothing to retry with
     */
    private static byte[] reauthenticateAfterUnauthorized(ConjurAuthnInfo conjurAuthn,
                                                          ModelObject context,
                                                          IOException cause) throws IOException {
        GlobalConjurConfiguration globalConfig =
                GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        String cacheKey = buildCacheKey(conjurAuthn, context, globalConfig);
        if (cacheKey != null) {
            tokenCache.evict(cacheKey);
        }
        LOGGER.log(Level.FINE, "Conjur rejected the access token; evicted it and re-authenticating");

        if (cacheKey == null) {
            // Cache was bypassed (no globalConfig); authenticate directly without caching.
            byte[] freshToken = authenticator.getAuthorizationToken(conjurAuthn, context);
            if (freshToken == null) {
                LOGGER.log(Level.WARNING,
                        "Re-authentication after Conjur 401 returned no token. Original error: {0}",
                        cause.getMessage());
                throw cause;
            }
            return freshToken;
        }

        byte[] freshToken = tokenCache.getOrLoad(cacheKey,
                () -> authenticator.getAuthorizationToken(conjurAuthn, context));
        if (freshToken == null) {
            LOGGER.log(Level.WARNING,
                    "Re-authentication after Conjur 401 returned no token; the JWT authenticator "
                    + "may be misconfigured. Original error: {0}", cause.getMessage());
            throw cause;
        }
        return freshToken;
    }

    /**
     * Performs one Conjur secret fetch, with a single 401-evict-retry if the cached token has
     * been revoked or rotated. Only retries when the authenticator is cacheable (JWT); API Key
     * authenticators always re-authenticate per call and never produce stale cached tokens.
     *
     * <p>The caller owns {@code authToken} and is responsible for zeroing it after this method
     * returns (or throws). {@code freshToken}, when obtained, is zeroed inside this method's
     * {@code finally} block so it never escapes.
     */
    private static byte[] fetchSecretWithRetry(OkHttpClient client, ConjurConfiguration config,
                                              byte[] authToken, String variableId,
                                              ConjurAuthnInfo conjurAuthn,
                                              ModelObject context) throws IOException {
        try {
            return getConjurSecret(client, config, authToken, variableId);
        } catch (IOException e) {
            if (!isUnauthorized(e) || !authenticator.isCacheable()) {
                throw e;
            }
            byte[] freshToken = reauthenticateAfterUnauthorized(conjurAuthn, context, e);
            try {
                return getConjurSecret(client, config, freshToken, variableId);
            } finally {
                Arrays.fill(freshToken, (byte) 0);
            }
        }
    }

    /**
     * Retrieve the ConjurAuthnInfo configured for Jenkins build
     *
     * @param configuration Jenkins configuration
     * @param context       current context
     * @return ConjurAuthnInfo
     */
    public static ConjurAuthnInfo getConjurAuthnInfo(ConjurConfiguration configuration,
                                                     ModelObject context) {
        // Always call setAuthenticator() so it both (a) initialises the authenticator type on
        // first call and (b) detects live config changes on subsequent calls.  The returned
        // globalConfig is threaded down to fillBaseAuthnInfo, eliminating the redundant
        // GlobalConfiguration.all().get() call that fillBaseAuthnInfo used to make itself.
        GlobalConjurConfiguration globalConfig = setAuthenticator();

        ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();
        conjurAuthn.setConjurConfiguration(configuration);

        // Default to Environment variables if not values present
        defaultToEnvironment(conjurAuthn);

        // default settings from configuration
        if (configuration != null) {
            String applianceUrl = configuration.getApplianceURL();
            if (applianceUrl != null && !applianceUrl.isEmpty()) {
                conjurAuthn.setApplianceUrl(applianceUrl);
            }
            String account = configuration.getAccount();
            if (account != null && !account.isEmpty()) {
                conjurAuthn.setAccount(account);
            }
            // Default authentication will be authn
            conjurAuthn.setAuthnPath("authn");
        }

        authenticator.fillBaseAuthnInfo(conjurAuthn, context, globalConfig);

        return conjurAuthn;
    }

    /**
     * This method gets the {@link ConjurAuthIno} data and retrieve the secret for the valid authenticationToken,account
     * variableName. The request to fetch the secret are build using the OkHttp client.
     *
     * @param client        OkHttp builds HTTP/HTTP/2 client that shares the same connection,thread pool and configuration.
     * @param configuration {@link ConjurConfiguration} containing the Conjur authentication parameters
     * @param authToken     token to authenticate the request.
     * @param variableId    for which to retrieve the secrets
     * @return the secrets for the specified variableName
     * @throws IOException
     */
    public static byte[] getConjurSecret(OkHttpClient client, ConjurConfiguration configuration, byte[] authToken,
                                         String variableId) throws IOException {
        LOGGER.log(Level.FINEST, String.format("getConjurSecret: variable name %s", variableId));

        String formattedUrl = String.format("%s/secrets/%s/variable/%s", configuration.getApplianceURL(), configuration.getAccount(), variableId);

        Request request = new Request.Builder().url(
                        formattedUrl)
                .get()
                .addHeader("x-cybr-telemetry", TelemetryConfiguration.getTelemetryHeader()) // Added the telemetry header
                .addHeader("Authorization", "Token token=\"" + new String(authToken, StandardCharsets.US_ASCII) + "\"").build();

        Response response = client.newCall(request).execute();
        byte[] result = {};
        ResponseBody body = response.body();
        if (body != null) {
            result = body.bytes();
        }
        LOGGER.log(Level.FINEST, () -> "getConjurSecret: Fetch secret from Conjur response code " + response.code()
                + " - " + response.message());
        if (response.code() != 200) {
            if (response.code() == 404) {
                throw new AuthenticationConjurException("No access");
            }
            throw new IOException(CONJUR_SECRET_FETCH_ERROR.format(response.code(), response.message(), new String(result)));
        }
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
        if (conjurConfiguration != null) {
            LOGGER.log(Level.FINEST, "Conjur configuration provided");
            LOGGER.log(Level.FINEST, "Conjur Configuration Appliance Url:{0} ", conjurConfiguration.getApplianceURL());
            LOGGER.log(Level.FINEST, "Conjur Configuration Account: {0}", conjurConfiguration.getAccount());
            LOGGER.log(Level.FINEST, "Conjur Configuration credential ID:{0} ", conjurConfiguration.getCredentialID());
        }
        return conjurConfiguration;
    }

    /**
     * Get ConjurConfiguration object for ItemGroup
     *
     * @param itemGroup item for which configuration will be returned. It also include hierarchy
     * @return ConjurConfiguration
     */
    public static ConjurConfiguration getConjurConfig(@NonNull ItemGroup<?> itemGroup) {
        ConjurConfiguration resultingConfig = null;

        for (ItemGroup<?> g = itemGroup; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
            FolderConjurConfiguration folderProperty = ((AbstractFolder<?>) g).getProperties()
                    .get(FolderConjurConfiguration.class);

            if (folderProperty != null) {
                // stop inheritance
                if (folderProperty.getConjurConfiguration() != null && !folderProperty.getInheritFromParent()) {
                    return resultingConfig;
                }

                if (resultingConfig != null) {
                    resultingConfig = resultingConfig.mergeWithParent(folderProperty.getConjurConfiguration());
                } else {
                    resultingConfig = folderProperty.getConjurConfiguration();
                    if (resultingConfig.getCredentialID() != null) {
                        resultingConfig.setCredentialIDContext(g);
                    }
                }
            }
        }

        // Getting global configuration

        GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        if (globalConfig != null) {
            if (resultingConfig == null) {
                resultingConfig = globalConfig.getConjurConfiguration();
            } else {
                resultingConfig = resultingConfig.mergeWithParent(globalConfig.getConjurConfiguration());
            }
            // if credentialID was provided but it was not set before
            if (resultingConfig.getCredentialID() != null && resultingConfig.getCredentialIDContext() == null) {
                resultingConfig.setCredentialIDContext(Jenkins.getInstanceOrNull());
            }
        }

        // report configuration issues

        if (resultingConfig == null) {
            LOGGER.log(Level.SEVERE, MISSING_CONJUR_CONFIG.format());
        } else if (StringUtils.isEmpty(resultingConfig.getAccount())) {
            LOGGER.log(Level.SEVERE, MISSING_ACCOUNT_FIELD.format());
        } else if (StringUtils.isEmpty(resultingConfig.getApplianceURL())) {
            LOGGER.log(Level.SEVERE, MISSING_APPLIANCE_URL.format());
        } else if (globalConfig != null &&
                globalConfig.getSelectAuthenticator().equals("APIKey") &&
                StringUtils.isEmpty(resultingConfig.getCredentialID())) {
            LOGGER.log(Level.SEVERE, MISSING_APIKEY_CREDENTIALS.format());
        }

        return resultingConfig;
    }

    /**
     * Retrieve the configuration specific to Context and configuration
     *
     * @param context ModelObject context
     * @return the Conjur Configuration based on the Jenkins ModelOjbect
     */

    public static ConjurConfiguration getConfigurationFromContext(ModelObject context) {
        ConjurConfiguration returnConfig = null;
        ConjurConfiguration conjurJobConfig = null;

        LOGGER.log(Level.FINEST, String.format("getConfigurationFromContext for context: %s", context));

        if (context != null) {
            if (context instanceof Hudson)    // for global context we return global config
            {
                // Getting global configuration, its always on top
                GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
                if (globalConfig != null && globalConfig.getConjurConfiguration() != null) {
                    return globalConfig.getConjurConfiguration();
                }
            }

            // if it's runnable process or build
            if (context instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) context;
                Job<?, ?> job = run.getParent();
                // getting JOB config

                ConjurJITJobProperty<?> conjurJITConfig = (ConjurJITJobProperty<?>) run.getParent().getProperty(ConjurJITJobProperty.class);
                if (conjurJITConfig != null) {
                    conjurJobConfig = conjurJITConfig.getConjurConfiguration();
                }

                // we have configuration for job, time to merge it with configuration from parent folders
                if (conjurJobConfig != null) {
                    if (conjurJobConfig.getInheritFromParent()) {
                        // if its running process (Run) we always have to get job and then folders
                        returnConfig = conjurJobConfig.mergeWithParent(getConjurConfig(job.getParent()));
                    } else {
                        returnConfig = conjurJobConfig;
                    }
                } else {
                    returnConfig = getConjurConfig(job.getParent());
                }
            } else if (context instanceof WorkflowJob) {
                WorkflowJob cont = (WorkflowJob) context;
                ConjurJITJobProperty<?> cjitjobprop = cont.getProperty(ConjurJITJobProperty.class);
                if (cjitjobprop != null && cjitjobprop.getConjurConfiguration() != null) {
                    returnConfig = cjitjobprop.getConjurConfiguration();
                } else {
                    returnConfig = new ConjurConfiguration();
                }
                ConjurConfiguration foldconf = getConjurConfig(((WorkflowJob) context).getParent());

                returnConfig = returnConfig.mergeWithParent(foldconf);
            } else if (context instanceof AbstractFolder) {
                returnConfig = getConjurConfig((ItemGroup<?>) context);
            } else if (context instanceof AbstractItem) {
                AbstractItem abstractItem = (AbstractItem) context;
                returnConfig = getConjurConfig(abstractItem.getParent());
            }
        }
        LOGGER.log(Level.FINEST, String.format("getConfigurationFromContext for context END: returnConfig %s", returnConfig));
        return returnConfig;
    }

    /**
     * Get credentials for Context from Conjur
     *
     * @param context object
     * @return list of credentials
     */
    public static Collection<StandardCredentials> getCredentialsForContext(@NonNull Class<?> type, ModelObject context) throws Exception {
        LOGGER.log(Level.FINEST, String.format("getCredentialsForContext: %s", context.getDisplayName()));

        // in some cases when Jenkins start it want's immediately to deliver credentials
        // so it must be taken from config and set
        if (authenticator == null) {
            setAuthenticator();
        }

        ConjurConfiguration conjurConfiguration = ConjurAPI.getConfigurationFromContext(context);

        // Authenticate to Conjur
        byte[] authToken = null;

        ConjurAuthnInfo conjurAuthn;
        try {
            conjurAuthn = ConjurAPI.getConjurAuthnInfo(conjurConfiguration, context);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, AUTHN_INFO_FAILED.format(e));
            return null;
        }

        try {
            authToken = ConjurAPI.getAuthorizationToken(conjurAuthn, context);
        } catch (AuthenticationConjurException exc) {
            LOGGER.log(Level.SEVERE, AUTH_FAILED_FOR_CONTEXT.format(context.getDisplayName()));
            return null;
        } catch (SSLPeerUnverifiedException pve) {
            LOGGER.log(Level.SEVERE, AUTH_SSL_PEER_UNVERIFIED.format(conjurAuthn.getApplianceUrl()));
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, AUTH_EXCEPTION.format(e));
            return null;
        }

        // First we are getting list of secrets
        OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurConfiguration);
        try {
            return fetchVariableListWithRetry(client, conjurConfiguration, authToken, conjurAuthn, type, context);
        } finally {
            if (authToken != null) Arrays.fill(authToken, (byte) 0);
        }
    }

    /**
     * Performs one variable-list fetch from the Conjur /resources endpoint, with a single
     * 401-evict-retry if the cached token has been revoked or rotated.
     */
    private static Collection<StandardCredentials> fetchVariableListWithRetry(
            OkHttpClient client, ConjurConfiguration config, byte[] authToken,
            ConjurAuthnInfo conjurAuthn, Class<?> type, ModelObject context) throws IOException {
        try {
            return fetchVariableList(client, config, authToken, conjurAuthn, type, context);
        } catch (IOException e) {
            if (!isUnauthorized(e) || !authenticator.isCacheable()) throw e;
            byte[] freshToken = reauthenticateAfterUnauthorized(conjurAuthn, context, e);
            try {
                return fetchVariableList(client, config, freshToken, conjurAuthn, type, context);
            } finally {
                Arrays.fill(freshToken, (byte) 0);
            }
        }
    }

    /**
     * Fetches the list of Conjur variables from the /resources endpoint and maps them to
     * Jenkins credentials. Builds and returns its own collection so the retry in
     * {@link #fetchVariableListWithRetry} is naturally idempotent.
     */
    private static Collection<StandardCredentials> fetchVariableList(
            OkHttpClient client, ConjurConfiguration config, byte[] authToken,
            ConjurAuthnInfo conjurAuthn, Class<?> type, ModelObject context) throws IOException {
        Collection<StandardCredentials> allCredentials = new ArrayList<>();

        String requestUrl = String.format("%s/resources/%s?kind=variable", conjurAuthn.getApplianceUrl(), conjurAuthn.getAccount());

        Request request = new Request.Builder().url(requestUrl)
                .get().addHeader("x-cybr-telemetry", TelemetryConfiguration.getTelemetryHeader())
                .addHeader("Authorization", "Token token=\"" + new String(authToken, StandardCharsets.US_ASCII) + "\"").build();

        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();

        if (responseBody != null) {
            String respBodyString = responseBody.string();

            LOGGER.log(Level.FINEST, String.format("ConjurAPI RESULT => %s request %s", respBodyString, requestUrl));
            if (response.code() != 200) {
                LOGGER.log(Level.FINEST, CONJUR_VARIABLE_FETCH_ERROR.format(response.code(), response.message(), respBodyString));
                throw new IOException(CONJUR_VARIABLE_FETCH_ERROR.format(response.code(), response.message(), respBodyString));
            }

                // now we parse response and go through all secrets

                JSONArray resultResources = new JSONArray(respBodyString);
                for (int i = 0; i < resultResources.length(); i++) {
                    JSONObject resource = resultResources.getJSONObject(i);

                    String variableId = resource.getString("id").split(":")[2];
                    JSONArray annotations = resource.getJSONArray("annotations");
                    String userName = null;
                    String credentialType = null;
                    for (int j = 0; j < annotations.length(); j++) {
                        JSONObject annotation = annotations.getJSONObject(j);
                        switch (annotation.getString("name").toLowerCase()) {
                            case "jenkins_credential_username":
                                userName = annotation.getString("value");
                                break;
                            case "jenkins_credential_type":
                                credentialType = annotation.getString("value").toLowerCase();
                                break;
                            default:
                                break;
                        }
                    }

                    if (credentialType == null) {
                        if (userName == null) {
                            credentialType = "credential";
                        } else {
                            credentialType = "usernamecredential";
                        }
                    }

                    // Intel request, we always create secret string credentials

                    ConjurSecretCredentials credential = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, variableId, variableId, "CyberArk Conjur Provided");
                    credential.setContext(context);
                    credential.setInheritedContext(context);

                    allCredentials.add(credential);

                    switch (credentialType) {
                        case "usernamecredential":
                            ConjurSecretUsernameCredentials usernameCredential = new ConjurSecretUsernameCredentialsImpl(CredentialsScope.GLOBAL, "username-" + variableId, userName, variableId, "CyberArk Conjur Provided");
                            usernameCredential.setContext(context);
                            usernameCredential.setInheritedContext(context);
                            if (type.isInstance(usernameCredential)) {
                                allCredentials.add(usernameCredential);
                            }
                            break;
                        case "stringcredential":
                            ConjurSecretStringCredentials stringCredential = new ConjurSecretStringCredentialsImpl(CredentialsScope.GLOBAL, "string-" + variableId, variableId, "CyberArk Conjur Provided");
                            stringCredential.setContext(context);
                            stringCredential.setInheritedContext(context);
                            if (type.isInstance(stringCredential)) {
                                allCredentials.add(stringCredential);
                            }
                            break;
                        case "usernamesshkeycredential":
                            ConjurSecretUsernameSSHKeyCredentials usernameSSHKeyCredential = new ConjurSecretUsernameSSHKeyCredentialsImpl(CredentialsScope.GLOBAL, "usernamesshkey-" + variableId, userName, variableId, null /* no passphrase yet */, "CyberArk Conjur Provided");
                            usernameSSHKeyCredential.setContext(context);
                            usernameSSHKeyCredential.setInheritedContext(context);
                            if (type.isInstance(usernameSSHKeyCredential)) {
                                allCredentials.add(usernameSSHKeyCredential);
                            }
                            break;
                        case "filecredential":
                            ConjurSecretFileCredentials fileCredential = new ConjurSecretFileCredentialsImpl(
                                    CredentialsScope.GLOBAL,
                                    "file-" + variableId,
                                    "CyberArk Conjur Provided",
                                    variableId);
                            fileCredential.setContext(context);
                            fileCredential.setInheritedContext(context);
                            if (type.isInstance(fileCredential)) {
                                allCredentials.add(fileCredential);
                            }
                            break;
                        case "dockercertcredential":
                            ConjurSecretDockerCertCredentials dockerCertCredential = new ConjurSecretDockerCertCredentialsImpl(
                                    CredentialsScope.GLOBAL,
                                    "docker-cert-" + variableId,
                                    variableId,
                                    variableId + "/key",
                                    variableId + "/cert",
                                    variableId + "/ca"
                            );
                            dockerCertCredential.setContext(context);
                            dockerCertCredential.setInheritedContext(context);
                            if (type.isInstance(dockerCertCredential)) {
                                allCredentials.add(dockerCertCredential);
                            }
                            break;
                        default:
                            break;
                    }

                    LOGGER.log(Level.FINEST, String.format("[getCredentialsForContext] Path: %s  userName:[%s]  credentialType:[%s]", variableId, userName, credentialType));
                }    // for() json structures in loop
            } else {
                throw new IOException(CONJUR_VARIABLE_FETCH_FAILED.format());
            }
            LOGGER.log(Level.FINEST, String.format("%d credentials returned", allCredentials.size()));
            return allCredentials;
        }

    /**
     * Get secret from Conjur
     *
     * @param context
     * @param inheritedObjectContext
     * @param variableId
     * @return
     */
    public static Secret getSecretFromConjur(ModelObject context, ModelObject inheritedObjectContext, String variableId) {
        byte[] result;
        Secret retSecret = null;
        try {
            ConjurConfiguration conjurConfiguration = getConfigurationFromContext(context);

            // Non-global credentials in the current context and multi-branch store context

            if (context != null) {
                // if call is done when context is inherited we must use it to get proper auth data
                // like JWTToken or ApiKey Credential ID
                ConjurAuthnInfo conjurAuthn;
                if (inheritedObjectContext == null) {
                    conjurAuthn = getConjurAuthnInfo(conjurConfiguration, context);
                } else {
                    conjurAuthn = getConjurAuthnInfo(conjurConfiguration, inheritedObjectContext);
                }

                // Use the same context conjurAuthn was built from so the cache key resolves to
                // the correct Conjur identity (inheritedObjectContext, e.g. the folder) rather
                // than the build Run whose sub-claim maps to a different cached token.
                ModelObject authnContext = (inheritedObjectContext != null) ? inheritedObjectContext : context;

                // Authenticate to Conjur
                byte[] authToken = getAuthorizationToken(conjurAuthn, authnContext);
                if (authToken == null) {
                    throw new AuthenticationConjurException("Authentication returned no token", null);
                }

                // Retrieve secret from Conjur
                // Get Http Client
                OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurConfiguration);
                try {
                    result = fetchSecretWithRetry(client, conjurConfiguration, authToken, variableId, conjurAuthn, authnContext);
                } finally {
                    Arrays.fill(authToken, (byte) 0);
                }

                try {
                    retSecret = Secret.fromString(new String(result, StandardCharsets.UTF_8));
                } finally {
                    Arrays.fill(result, (byte) 0);
                }
            }
        } catch (IOException ie) {
            throw new InvalidConjurSecretException(ie.getMessage(), ie);
        } catch (Exception e) {
            throw new InvalidConjurSecretException(e.getMessage(), e);
        }
        return retSecret;
    }

    /**
     * Check if inheritance is turned on for specified context
     *
     * @param context for which inheritance option will be checked
     * @return if inheritance option is on or off for context
     */
    public static boolean isInheritanceOn(ModelObject context) {
        boolean inheritanceOn = true;

        if (context instanceof Job) {
            try {
                Job<?, ?> run = (Job<?, ?>) context;
                ConjurJITJobProperty<?> conjurJITConfig = run.getProperty(ConjurJITJobProperty.class);

                if (conjurJITConfig != null &&
                        conjurJITConfig.getConjurConfiguration() != null &&
                        !conjurJITConfig.getConjurConfiguration().getInheritFromParent()) {
                    LOGGER.log(Level.FINEST, "There is no config assigned to Job/Item. Inheritance is off.");
                    inheritanceOn = false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINEST, "Cannot get properties for Job/Item");
            }
        } else if (context instanceof AbstractFolder) {
            try {
                FolderConjurConfiguration fc = ((AbstractFolder<?>) context).getProperties()
                        .get(FolderConjurConfiguration.class);
                if (fc != null && !fc.getInheritFromParent()) {
                    inheritanceOn = false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINEST, "Cannot get properties for AbstractFolder");
            }
        }
        return inheritanceOn;
    }

    /**
     * Get secret from Conjur with using inheritance
     *
     * @param context     main context to which credential is assigned
     * @param credentials to which context will be assigned when call will be able to receive secrets
     * @param variableId  secret name
     * @return Secret
     */
    public static Secret getSecretFromConjurWithInheritance(ModelObject context, ConjurSecretCredentials credentials, String variableId) {
        byte[] result;
        Secret retSecret = null;
        byte[] authToken;
        String contextName = "null";

        if (context == null) {
            LOGGER.log(Level.FINEST, "No context set for function getSecretWithInheritance");
            StaplerRequest currentRequest = Stapler.getCurrentRequest();
            if (currentRequest != null) {
                context = currentRequest.findAncestorObject(ModelObject.class);
            }

            // Stapler.getCurrentRequest() is null on non-HTTP threads (e.g. build executors), so this
            // fallback must not be nested inside the currentRequest != null check above, or a persisted
            // credential resolved off an HTTP thread is left with a null context (see issue #48).
            if (context == null) {
                LOGGER.log(Level.FINEST, "No context available for current request");
                context = Jenkins.get();
            }

            if (context != null) {
                contextName = context.getDisplayName();
            }
        }

        LOGGER.log(Level.FINEST, String.format("Get Secret with inheritance for context: %s", contextName));
        try {
            ConjurConfiguration conjurConfiguration;

            // we go through item to the folder on the top and try to get credentials
            while (true) {
                try {
                    LOGGER.log(Level.FINEST, String.format("Get config context %s", contextName));
                    conjurConfiguration = getConfigurationFromContext(context);
                    ConjurAuthnInfo conjurAuthn;
                    conjurAuthn = getConjurAuthnInfo(conjurConfiguration, context);
                    // Authenticate to Conjur
                    authToken = getAuthorizationToken(conjurAuthn, context);
                    if (authToken == null) {
                        throw new AuthenticationConjurException("Authentication returned no token", null);
                    }

                    // Retrieve secret from Conjur
                    // Get Http Client
                    OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurConfiguration);
                    try {
                        result = fetchSecretWithRetry(client, conjurConfiguration, authToken, variableId, conjurAuthn, context);
                    } catch (IOException e) {
                        if (isUnauthorized(e)) {
                            throw new AuthenticationConjurException("Access denied; trying parent context", e);
                        }
                        throw e;
                    } finally {
                        if (authToken != null) Arrays.fill(authToken, (byte) 0);
                    }
                    try {
                        retSecret = Secret.fromString(new String(result, StandardCharsets.UTF_8));
                    } finally {
                        Arrays.fill(result, (byte) 0);
                    }
                    credentials.setContext(context);
                    break;
                } catch (AuthenticationConjurException e) {
                    // when authentication fail we check if upper level have access
                    if (isInheritanceOn(context)) {
                        LOGGER.log(Level.FINEST, "Get config context exception: " + context.getDisplayName() + " " + context);
                        if (context instanceof Run) {
                            Run<?, ?> run = (Run<?, ?>) context;
                            Job<?, ?> job = run.getParent();
                            context = job.getParent();
                        } else if (context instanceof Job) {
                            context = ((Job) context).getParent();
                        } else if (context instanceof AbstractFolder) {
                            context = ((AbstractFolder<?>) context).getParent();
                        } else if (context instanceof Jenkins) {
                            LOGGER.log(Level.FINEST, "Get config context Invalid when inheritance is on!");
                            throw new InvalidConjurSecretException(e.getMessage(), e);
                        }
                    } else {
                        // inheritance is turned off, we have to try to get credentials from root
                        if (context instanceof Jenkins) {
                            LOGGER.log(Level.FINEST, "Get config context Invalid!");
                            throw new InvalidConjurSecretException(e.getMessage(), e);
                        }
                        // we are sure this will be our last level to check
                        context = Jenkins.get();
                    }
                }
            }
        } catch (IOException e) {
            throw new InvalidConjurSecretException(e.getMessage(), e);
        }
        return retSecret;
    }
}
