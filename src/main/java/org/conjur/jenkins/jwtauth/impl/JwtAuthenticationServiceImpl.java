package org.conjur.jenkins.jwtauth.impl;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.JwtAuthenticationService;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.CjplCode.JWKS_METHOD_NOT_SUPPORTED;

/**
 * 
 * Class invoked when JWT token based authentication is invoked
 */
@Extension
public class JwtAuthenticationServiceImpl extends JwtAuthenticationService {
	private static final Logger LOGGER = Logger.getLogger(JwtAuthenticationServiceImpl.class.getName());
	private static final String DISPLAY_NAME = "Conjur JWT endpoint";

	/**
	 * get the public key based on the Global Configuration
	 * 
	 * @return public key 
	 */
	@Override
	public String getJwkSet() {
		try {
			GlobalConjurConfiguration result = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
			if (result == null ) {
				throw new IOException(JWKS_METHOD_NOT_SUPPORTED.format());
			}

			return JwtToken.getJwkset().toString(4);
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE,ex.getMessage());
		}
		return null;
	}
	
	/**
	 * Get the IconFileName
	 * @return null;
	 */
	@Override
	public String getIconFileName() {
		return null;
	}

	/**
	 * Get the displayname
	 * @return displayname
	 */
	@Override
	public String getDisplayName() {
		return DISPLAY_NAME;
	}
}
