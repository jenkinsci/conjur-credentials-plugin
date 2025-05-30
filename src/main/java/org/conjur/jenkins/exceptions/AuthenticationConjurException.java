package org.conjur.jenkins.exceptions;

import java.io.IOException;

/**
 *
 */
public class AuthenticationConjurException extends IOException
{
    private int errorCode;

    /**
     * Throw error message if secret is not found
     *
     * @param errorMessage
     * @param err
     */
    public AuthenticationConjurException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    /**
     * throws error message if secret is not found
     *
     * @param errorMessage
     */
    public AuthenticationConjurException(String errorMessage) {
        super(errorMessage);
    }

    /**
     * throws error message if secret is not found
     */
    public AuthenticationConjurException(int error) {
        super("Error code:");
        errorCode = error;
    }

    public int getErrorCode(){ return errorCode;}
}
