package org.conjur.jenkins.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationConjurExceptionTest {

    @Test
    void testConstructorWithErrorMessageAndThrowable() {
        String errorMessage = "Unauthorized";
        Throwable throwable = mock(Throwable.class);
        AuthenticationConjurException exception = new AuthenticationConjurException(errorMessage, throwable);

        assertNotNull(exception);
    }

    @Test
    void testConstructorWithErrorMessage() {
        String errorMessage = "Unauthorized";
        AuthenticationConjurException exception = new AuthenticationConjurException(errorMessage);

        assertNotNull(exception);
    }

    @Test
    void testConstructorWithErrorCode() {
        int errorCode = 401;
        AuthenticationConjurException exception = new AuthenticationConjurException(errorCode);

        assertNotNull(exception);
    }

    @Test
    void testgetErrorcode() {
        int errorCode = 501;
        AuthenticationConjurException exception = new AuthenticationConjurException(errorCode);

        assertEquals(501, exception.getErrorCode());
    }
}
