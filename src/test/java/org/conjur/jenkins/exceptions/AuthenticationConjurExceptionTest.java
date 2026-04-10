package org.conjur.jenkins.exceptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationConjurExceptionTest {

    @Test
    public void testConstructorWithErrorMessageAndThrowable() {
        String errorMessage = "Unauthorized";
        Throwable throwable = mock(Throwable.class);
        AuthenticationConjurException exception = new AuthenticationConjurException(errorMessage, throwable);

        assertNotNull(exception);
    }

    @Test
    public void testConstructorWithErrorMessage() {
        String errorMessage = "Unauthorized";
        AuthenticationConjurException exception = new AuthenticationConjurException(errorMessage);

        assertNotNull(exception);
    }

    @Test
    public void testConstructorWithErrorCode() {
        int errorCode = 401;
        AuthenticationConjurException exception = new AuthenticationConjurException(errorCode);

        assertNotNull(exception);
    }

    @Test
    public void testgetErrorcode() {
        int errorCode = 501;
        AuthenticationConjurException exception = new AuthenticationConjurException(errorCode);

        assertEquals(501, exception.getErrorCode());
    }
}
