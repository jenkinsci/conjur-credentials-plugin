package org.conjur.jenkins.jwtauth.impl;

import java.security.interfaces.RSAPrivateKey;

import jenkins.security.RSADigitalSignatureConfidentialKey;

/**
 * RSA key pair used to sign JWT tokens.
 *
 */
public final class JwtRsaDigitalSignatureKey extends RSADigitalSignatureConfidentialKey {
    private final String id;
    private final long creationTime;

/**
 * Constructor for JwtRsaDigitalSignatureKey
 * @param id
 */
    public JwtRsaDigitalSignatureKey(String id) {
        super("conjurJWT-" + id);
        this.id = id;
        this.creationTime = System.currentTimeMillis()/1000;

    }
    /**
     * Getter for Id
     * @return Id
     */

    @Override
    public String getId() {
        return id;
    }
/**
 * Getter for JWT creationTime
 * @return creationTime
 */
    protected long getCreationTime() {
        return creationTime;
    }
    /**
     * To get the privateKey
     * @return RSAPrivateKey
     */

    protected RSAPrivateKey toSigningKey() {
        return getPrivateKey();
    }

}