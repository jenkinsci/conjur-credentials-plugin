package org.conjur.jenkins.authenticator;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.model.ModelObject;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConjurAPIKeyAuthenticator extends AbstractAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(ConjurAPIKeyAuthenticator.class.getName());

    /**
     * Function return authenticator name
     * @return authenticator name
     */
    @Override
    public String getName() {
        return "APIKey";
    }

    /**
     *
     * @param conjurAuthn ConjurAuthnInfo with information used to authenticate
     * @param context Jenkins context object. Current context from which call is made
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
     * Fill authninfo structure
     * @param conjurAuthn authentication configuration class
     * @param context Context for which APIKey will be taken from Credentials
     */
    @Override
    public void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context) {
        ConjurConfiguration configuration = ConjurAPI.getConfigurationFromContext(context);
        CredentialsMatcher matcher =
                CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class);
        List <UsernamePasswordCredentials> globalCreds = DomainCredentials.getCredentials(
                SystemCredentialsProvider.getInstance().getDomainCredentialsMap(), UsernamePasswordCredentials.class, Collections.emptyList(), matcher);

        if (configuration.getCredentialID() != null && !configuration.getCredentialID().isEmpty()) {
            UsernamePasswordCredentials credential = CredentialsMatchers.firstOrNull(globalCreds,
                    CredentialsMatchers.withId(configuration.getCredentialID()));
            if (credential != null) {
                conjurAuthn.setLogin(credential.getUsername());
                conjurAuthn.setApiKey(credential.getPassword().getPlainText().getBytes(StandardCharsets.US_ASCII));
            }
        }
        LOGGER.log(Level.SEVERE, String.format("UsernamePasswordCredentials found %d for ID %s",globalCreds.size(), configuration.getCredentialID( ) ) );
    }
}
