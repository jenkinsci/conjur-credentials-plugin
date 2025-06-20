package org.conjur.jenkins.exceptions;

/**
 * Custom Exception if no secert is found or malformed authentication
 * 
 *
 */
public class InvalidConjurSecretException extends RuntimeException {

	/**
	 * Throw error message if secret is not found
	 * 
	 * @param errorMessage
	 * @param err
	 */
	public InvalidConjurSecretException(String errorMessage, Throwable err) {
		super(errorMessage, err);
	}

	/**
	 * throws error message if secret is not found
	 * 
	 * @param errorMessage
	 */
	public InvalidConjurSecretException(String errorMessage) {
		super(errorMessage);
	}

}