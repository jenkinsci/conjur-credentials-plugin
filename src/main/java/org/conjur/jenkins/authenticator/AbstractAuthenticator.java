package org.conjur.jenkins.authenticator;

import hudson.model.ModelObject;
import org.conjur.jenkins.api.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;

import java.io.IOException;

/**
 *  Abstract Authenticator
 */
public abstract class AbstractAuthenticator {

    public abstract byte[] getAuthorizationToken(ConjurAuthnInfo conjurAuthn,
                                                 ModelObject context) throws IOException;

    public abstract void fillAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context);

    /**
     * Fills only the structural fields needed to build a cache key (login, authnPath).
     * Does NOT sign a JWT — signing is deferred to the cache-miss loader so that cache hits
     * incur no RSA signing cost. Defaults to {@link #fillAuthnInfo} for non-JWT authenticators.
     *
     * @param globalConfig the already-resolved {@link GlobalConjurConfiguration}; may be
     *                     {@code null} when the global config is absent (e.g. early startup).
     *                     Non-JWT authenticators may ignore this parameter.
     */
    public void fillBaseAuthnInfo(ConjurAuthnInfo conjurAuthn, ModelObject context,
                                  GlobalConjurConfiguration globalConfig) {
        fillAuthnInfo(conjurAuthn, context);
    }

    public abstract String getName();

    /**
     * Returns {@code true} if tokens produced by this authenticator should be cached and shared
     * across concurrent builds with the same Conjur host identity.
     *
     * <p>Defaults to {@code false}. Cacheable authenticators (currently only
     * {@code ConjurJWTAuthenticator}) override this to return {@code true}. New authenticator
     * implementations must explicitly opt in; omitting the override is the safe default.
     */
    public boolean isCacheable() {
        return false;
    }
}
