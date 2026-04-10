package org.conjur.jenkins.exceptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class InvalidConjurSecretExceptionTest {

    @Test
    public void testConstructorWithErrorMessageAndThrowable() {
        String errorMessage = "Test Error Message";
        Throwable throwable = mock(Throwable.class);
        InvalidConjurSecretException exception = new InvalidConjurSecretException(errorMessage, throwable);

        assertNotNull(exception);
    }

    @Test
    public void testConstructorWithErrorMessage() {
        String errorMessage = "Test Error Message";
        InvalidConjurSecretException exception = new InvalidConjurSecretException(errorMessage);

        assertNotNull(exception);
    }
}