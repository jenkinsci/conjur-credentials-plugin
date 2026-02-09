package org.conjur.jenkins.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvalidConjurSecretExceptionTest {

    @Test
    void testConstructorWithErrorMessageAndThrowable() {
        String errorMessage = "Test Error Message";
        Throwable throwable = mock(Throwable.class);
        InvalidConjurSecretException exception = new InvalidConjurSecretException(errorMessage, throwable);

        assertNotNull(exception);
    }

    @Test
    void testConstructorWithErrorMessage() {
        String errorMessage = "Test Error Message";
        InvalidConjurSecretException exception = new InvalidConjurSecretException(errorMessage);

        assertNotNull(exception);
    }
}