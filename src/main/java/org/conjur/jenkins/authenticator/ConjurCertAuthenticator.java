package org.conjur.jenkins.authenticator;

import hudson.model.ModelObject;
import jenkins.model.GlobalConfiguration;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.AuthenticationConjurException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConjurCertAuthenticator extends AbstractAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(ConjurCertAuthenticator.class.getName());

    @Override
    public String getName() {
        return "Certificate";
    }

    @Override
    public byte[] getAuthorizationToken(ConjurAuthnInfo conjurAuthn, ModelObject context) throws IOException {
        byte[] resultingToken = null;

        LOGGER.log(Level.FINEST, String.format(
                "Authenticating with Conjur (Certificate) authnPath=%s account=%s applianceUrl=%s login=%s",
                conjurAuthn.getAuthnPath(), conjurAuthn.getAccount(),
                conjurAuthn.getApplianceUrl(), conjurAuthn.getLogin()));

        if (conjurAuthn.getAuthnPath() == null || conjurAuthn.getLogin() == null) {
            LOGGER.log(Level.SEVERE, "CJPL_043: Cannot create http call. Certificate authentication failed — missing service ID or host ID.");
            return null;
        }

        String authnPath = conjurAuthn.getAuthnPath().contains("/")
                ? conjurAuthn.getAuthnPath()
                : "authn-cert/" + conjurAuthn.getAuthnPath();

        String encodedLogin = URLEncoder.encode(conjurAuthn.getLogin(), "utf-8");
        String url = String.format("%s/%s/%s/%s/authenticate",
                conjurAuthn.getApplianceUrl(), authnPath, conjurAuthn.getAccount(), encodedLogin);

        LOGGER.log(Level.FINEST, () -> String.format("Conjur Certificate Authenticate URL: %s", url));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("text/plain"), ""))
                .build();

        OkHttpClient client = ConjurAPIUtils.getHttpClient(conjurAuthn.getConjurConfiguration());
        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (body != null) {
            byte[] respMessage = body.string().getBytes(StandardCharsets.UTF_8);
            resultingToken = Base64.getEncoder().withoutPadding()
                    .encodeToString(respMessage).getBytes(StandardCharsets.US_ASCII);
            LOGGER.log(Level.FINEST, () -> String.format(
                    "Conjur Certificate Authenticate response %d - %s", response.code(), response.message()));
        }

        if (response.code() != 200) {
            if (response.code() == 401) {
                throw new AuthenticationConjurException(response.code());
            } else {
                throw new IOException("[" + response.code() + "] - " + response.message());
            }
        }

        return resultingToken;
    }

    @Override
    public void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context) {
        String serviceId = null;
        String hostId = null;

        ConjurConfiguration contextConfig = conjurAuthn.getConjurConfiguration();
        if (contextConfig != null) {
            serviceId = contextConfig.getCertAuthServiceId();
            hostId = contextConfig.getCertHostId();
        }

        GlobalConjurConfiguration globalConfig = null;
        if (StringUtils.isBlank(serviceId) || StringUtils.isBlank(hostId)) {
            globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        }

        if (StringUtils.isBlank(serviceId) && globalConfig != null) {
            serviceId = globalConfig.getCertAuthServiceId();
        }
        if (StringUtils.isBlank(hostId) && globalConfig != null) {
            hostId = globalConfig.getCertHostId();
        }

        if (StringUtils.isNotBlank(serviceId)) {
            conjurAuthn.setAuthnPath(serviceId);
        }
        if (StringUtils.isNotBlank(hostId)) {
            conjurAuthn.setLogin(hostId);
        }
        conjurAuthn.setApiKey(null);
    }
}
