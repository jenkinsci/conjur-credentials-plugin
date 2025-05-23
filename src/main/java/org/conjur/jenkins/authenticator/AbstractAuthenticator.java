package org.conjur.jenkins.authenticator;

import hudson.model.ModelObject;
import org.conjur.jenkins.api.ConjurAuthnInfo;

import java.io.IOException;

/**
 *  Abstract Authenticator
 */
public abstract class AbstractAuthenticator {

    public abstract byte[] getAuthorizationToken(ConjurAuthnInfo conjurAuthn,
                                                 ModelObject context) throws IOException;

    public abstract void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context );

    public abstract String getName();
}
