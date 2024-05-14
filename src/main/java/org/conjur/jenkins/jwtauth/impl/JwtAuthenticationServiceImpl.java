package org.conjur.jenkins.jwtauth.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.JwtAuthenticationService;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

/**
 * 
 * Class invoked when JWT token based authentication is invoked
 */
@Extension
public class JwtAuthenticationServiceImpl extends JwtAuthenticationService {
	private static final Logger LOGGER = Logger.getLogger(JwtAuthenticationServiceImpl.class.getName());

	/**
	 * get the public key based on the Global Configuration
	 * 
	 * @return public key 
	 */
	@Override
	public String getJwkSet() throws HttpRequestMethodNotSupportedException {
		LOGGER.log(Level.FINE, "Start of getJwkSet");
		try {
			GlobalConjurConfiguration result = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
			LOGGER.log(Level.FINE, "Getting JwkSet() -->GlobalConjurConfiguration result: " + result);
			if (result == null || !result.getEnableJWKS()) {
				throw new HttpRequestMethodNotSupportedException("conjur-jwk-set");
			}

			return JwtToken.getJwkset().toString(4);
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE,ex.getMessage());

		}
		LOGGER.log(Level.FINE, "End of getJwkSet");
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
		return "Conjur JWT endpoint";
	}

}
