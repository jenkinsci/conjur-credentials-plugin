package org.conjur.jenkins.authenticator;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.credentials.ConjurCredentialProvider;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConjurAPIKeyAuthenticator extends AbstractAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(ConjurAPIKeyAuthenticator.class.getName());

    /**
     * Function return authenticator name
     *
     * @return authenticator name
     */
    @Override
    public String getName() {
        return "APIKey";
    }

    /**
     *
     * @param conjurAuthn ConjurAuthnInfo with information used to authenticate
     * @param context     Jenkins context object. Current context from which call is made
     * @return authorization token
     * @throws IOException
     */
    @Override
    @SuppressWarnings("deprecation")
    public byte[] getAuthorizationToken(ConjurAuthnInfo conjurAuthn, ModelObject context) throws IOException {
        byte[] resultingToken = null;

        LOGGER.log(Level.FINEST, String.format("getAuthorizationToken: authnPath %s account %s conjurAuthn.applianceUrl %s",
                conjurAuthn.getAuthnPath(), conjurAuthn.getAccount(), conjurAuthn.getApplianceUrl()));

        Request request = null;
        if (conjurAuthn.getApiKey() != null && conjurAuthn.getLogin() != null) {
            String urlstring = String.format("%s/%s/%s/%s/authenticate", conjurAuthn.getApplianceUrl(), conjurAuthn.getAuthnPath(),
                    conjurAuthn.getAccount(), URLEncoder.encode(conjurAuthn.getLogin(), "utf-8"));
            request = new Request.Builder()
                    .url(urlstring)
                    .post(RequestBody.create(MediaType.parse("text/plain"), conjurAuthn.getApiKey())).build();
        }

        if (request != null) {
            OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurAuthn.getConjurConfiguration());
            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();
            if (body != null) {
                byte[] respMessage = body.string().getBytes(StandardCharsets.UTF_8);
                resultingToken = Base64.getEncoder().withoutPadding()
                        .encodeToString(respMessage).getBytes(StandardCharsets.US_ASCII);
                LOGGER.log(Level.FINEST,
                        () -> String.format("Conjur Authenticate response %d - %s", response.code(), response.message()));
            }

            if (response.code() != 200) {
                if (response.code() == 401) {
                    throw new AuthenticationConjurException(response.code());
                } else {
                    throw new IOException("[" + response.code() + "] - " + response.message());
                }
            }
        } else {
            LOGGER.log(Level.SEVERE, "Cannot create http call. Authentication failed.");
        }
        return resultingToken;
    }

    /**
     * Get Username credentials for context
     *
     * @param context      Context for which APIKey will be taken from Credentials
     * @param credentialId
     * @return UsernamePasswordCredentials
     */
    private UsernamePasswordCredentials getUsernameCredentialsForContext(ModelObject context, String credentialId) {
        UsernamePasswordCredentials credential = null;

        try {
            List<UsernamePasswordCredentials> creds = new ArrayList<>();
            for (CredentialsProvider provider : CredentialsProvider.all()) {
                if (provider instanceof ConjurCredentialProvider) {
                    continue; // skip our current provider
                }

                if (context instanceof Hudson || context == null) {
                    CredentialsMatcher matcher =
                            CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class);
                    creds.addAll(DomainCredentials.getCredentials(
                            SystemCredentialsProvider.getInstance().getDomainCredentialsMap(), UsernamePasswordCredentials.class, Collections.emptyList(), matcher));
                } else {
                    creds.addAll(provider.getCredentials(UsernamePasswordCredentials.class, (Item) context, ACL.SYSTEM,
                            Collections.emptyList()));
                }
            }
            credential = CredentialsMatchers.firstOrNull(creds, CredentialsMatchers.withId(credentialId));
        } catch (Exception e) {
            String conDisplay = context != null ? context.getDisplayName() : "Jenkins";
            LOGGER.log(Level.SEVERE, String.format("Cannot get Username Credentials for context %s", conDisplay), e);
        }

        return credential;
    }

    /**
     * Fill authninfo structure
     *
     * @param conjurAuthn authentication configuration class
     * @param context     Context for which APIKey will be taken from Credentials
     */
    @Override
    public void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context) {
        ConjurConfiguration configuration = ConjurAPI.getConfigurationFromContext(context);
        UsernamePasswordCredentials credential = null;

        if (configuration.getCredentialID() == null || configuration.getCredentialID().isEmpty()) {
            return;
        }

        credential = getUsernameCredentialsForContext(configuration.getCredentialIDContext(), configuration.getCredentialID());

        if (credential == null) {
            credential = getUsernameCredentialsForContext(Jenkins.get(), configuration.getCredentialID());
        }

        if (credential != null) {
            conjurAuthn.setLogin(credential.getUsername());
            conjurAuthn.setApiKey(
                    credential.getPassword().getPlainText().getBytes(StandardCharsets.US_ASCII)
            );
        }

        LOGGER.log(Level.SEVERE, String.format("UsernamePasswordCredentials found for ID %s", configuration.getCredentialID()
        ));
    }
}
