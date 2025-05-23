package org.conjur.jenkins.authenticator;

import hudson.model.ModelObject;
import jenkins.model.GlobalConfiguration;
import okhttp3.*;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;
import org.conjur.jenkins.jwtauth.impl.JwtToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConjurJWTAuthenticator extends AbstractAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(ConjurJWTAuthenticator.class.getName());

    /**
     * Function return authenticator name
     * @return authenticator name
     */
    @Override
    public String getName() {
        return "JWT";
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

        LOGGER.log(Level.FINEST, String.format("Authenticating with Conjur (JWT) conjurAuthn.authnPath %s conjurAuthn.account %s conjurAuthn.applianceUrl %s", conjurAuthn.authnPath,conjurAuthn.account, conjurAuthn.applianceUrl ) );

        Request request = null;
        if (conjurAuthn.apiKey != null)
        {
            if (conjurAuthn.authnPath != null )
            {
                String authnPath = !conjurAuthn.authnPath.contains("/") ? "authn-jwt/" + conjurAuthn.authnPath
                        : conjurAuthn.authnPath;

                request = new Request.Builder()
                        .url(String.format("%s/%s/%s/authenticate", conjurAuthn.applianceUrl, authnPath,
                                conjurAuthn.account))
                        .post(RequestBody.create(MediaType.parse("text/plain"), conjurAuthn.apiKey)).build();
            }
        }

        if (request != null)
        {
            OkHttpClient client = ConjurAPIUtils.getHttpClient( conjurAuthn.conjurConfiguration );

            Response response = client.newCall(request).execute();

                ResponseBody body = response.body();
                if (body != null) {
                    byte[] respMessage = body.string().getBytes(StandardCharsets.UTF_8);
                    resultingToken = Base64.getEncoder().withoutPadding()
                            .encodeToString(respMessage).getBytes(StandardCharsets.US_ASCII);
                    LOGGER.log(Level.FINEST,
                            () -> "Conjur Authenticate response " + response.code() + " - " + response.message());
                }

            if (response.code() != 200) {
                if (response.code() == 401) {
                    // we throw different exception in case if there will be case to quickly recognize issue

                    throw new AuthenticationConjurException(response.code());
                } else {
                    throw new IOException("[" + response.code() + "] - " + response.message());
                }
            }
        }
        else
        {
            LOGGER.log(Level.SEVERE, "Cannot create http call. JWTAuthentication failed.");
        }
        return resultingToken;
    }

    /**
     * Fill authninfo structure
     * @param conjurAuthn authentication configuration class
     * @param context ModelObject
     */
    @Override
    public void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context) {
        GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);

        String jwtToken = JwtToken.getToken( context, globalConfig );

        conjurAuthn.login = null;
        if( globalConfig != null )
        {
            conjurAuthn.authnPath = globalConfig.getAuthWebServiceId();
        }
        byte[] jwtNameBytes = "jwt=".getBytes(StandardCharsets.US_ASCII);
        byte[] jwtTokenBytes = jwtToken.getBytes(StandardCharsets.US_ASCII);
        byte[] jwt = new byte[jwtNameBytes.length + jwtTokenBytes.length];

        System.arraycopy(jwtNameBytes,0,jwt,0         ,jwtNameBytes.length);
        System.arraycopy(jwtTokenBytes,0,jwt,jwtNameBytes.length,jwtTokenBytes.length);
        conjurAuthn.apiKey = jwt;
    }
}
