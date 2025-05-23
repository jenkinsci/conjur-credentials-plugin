package org.conjur.jenkins.authenticator;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.model.ModelObject;
import jenkins.model.Jenkins;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
                conjurAuthn.authnPath, conjurAuthn.account, conjurAuthn.applianceUrl ) );

        Request request = null;
        if (conjurAuthn.apiKey != null)
        {
            if( conjurAuthn.login != null  )
            {
                String urlstring = String.format("%s/%s/%s/%s/authenticate", conjurAuthn.applianceUrl, conjurAuthn.authnPath,
                        conjurAuthn.account, URLEncoder.encode(conjurAuthn.login, "utf-8"));
                request = new Request.Builder()
                        .url(urlstring)
                        .post(RequestBody.create(MediaType.parse("text/plain"), conjurAuthn.apiKey)).build();
            }
        }

        if (request != null)
        {
            OkHttpClient client = ConjurAPIUtils.getHttpClient( conjurAuthn.conjurConfiguration );

            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();
            if( body != null )
            {
                byte[] respMessage = body.string().getBytes(StandardCharsets.UTF_8);
                resultingToken = Base64.getEncoder().withoutPadding()
                        .encodeToString(respMessage).getBytes(StandardCharsets.US_ASCII);
                LOGGER.log(Level.FINEST,
                        () -> String.format( "Conjur Authenticate response %d - %s",response.code(), response.message() ) );
            }

            if (response.code() != 200)
            {
                if( response.code() == 401)
                {
                    // we want to give feedback that we are not authorized
                    throw new AuthenticationConjurException(response.code());
                }
                else {
                    throw new IOException("[" + response.code() + "] - " + response.message());
                }
            }
        }
        else
        {
            LOGGER.log(Level.SEVERE, "Cannot create http call. Authentication failed.");
        }

        return resultingToken;
    }

    public static final String CONJUR_JENKINS_PLUGIN = "CONJUR_JENKINS_PLUGIN";
    public static final org.acegisecurity.Authentication CONJUR_JENKINS_PLUGIN2 = new org.acegisecurity.providers.UsernamePasswordAuthenticationToken( CONJUR_JENKINS_PLUGIN, CONJUR_JENKINS_PLUGIN);

    /**
     * Fill authninfo structure
     * @param conjurAuthn authentication configuration class
     * @param context Context for which APIKey will be taken from Credentials
     */
    @Override
    public void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context) {
        List<UsernamePasswordCredentials> availableCredentials;
        ConjurConfiguration configuration = ConjurAPI.getConfigurationFromContext(context);

        availableCredentials = CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.get(),
                ConjurAPIKeyAuthenticator.CONJUR_JENKINS_PLUGIN2, Collections.<DomainRequirement>emptyList());
        // if credentials are set and apikey, then we take apikey from credentials and set it


        if (configuration.getCredentialID() != null && !configuration.getCredentialID().isEmpty()) {
            UsernamePasswordCredentials credential = CredentialsMatchers.firstOrNull(availableCredentials,
                    CredentialsMatchers.withId(configuration.getCredentialID()));
            if (credential != null) {
                conjurAuthn.login = credential.getUsername();
                conjurAuthn.apiKey = credential.getPassword().getPlainText().getBytes(StandardCharsets.US_ASCII);
            }
        }
        LOGGER.log(Level.SEVERE, String.format("UsernamePasswordCredentials found %d for ID %s",availableCredentials.size(), configuration.getCredentialID( ) ) );
    }
}
