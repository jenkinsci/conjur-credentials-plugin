package org.conjur.jenkins.credentials;


import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.ModelObject;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

@RunWith(MockitoJUnitRunner.class)
public class ConjurCredentialsSupplierTest {

    private TestLogHandler handler;

    @Mock
    private ModelObject mockContext;

    @Mock
    private ConjurSecretCredentials conjurCred;

    @Mock
    private StandardCredentials otherCred;

    Logger actualLogger = Logger.getLogger(ConjurCredentialsSupplier.class.getName());

    @Before
    public void setUp() {
        handler = new TestLogHandler();
        actualLogger.setLevel(Level.ALL);
        actualLogger.addHandler(handler);
    }

    @After
    public void tearDownLogger() {
        actualLogger.removeHandler(handler);
    }

    @Test
    public void testGetReturnsEmptyListContextIsNull() {
        Supplier<Collection<StandardCredentials>> baseSupplier = ConjurCredentialsSupplier.standard(null);
        ConjurCredentialsSupplier spySupplier = Mockito.spy((ConjurCredentialsSupplier) baseSupplier);
        Collection<StandardCredentials> result = spySupplier.get();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetReturnsStandardCredentials() {
        Collection<StandardCredentials> mockCredentials = new ArrayList<>();
        mockCredentials.add(conjurCred);
        mockCredentials.add(otherCred);

        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() ->
                    ConjurAPI.getCredentialsForContext(StandardCredentials.class, mockContext)
            ).thenReturn(mockCredentials);
            Supplier<Collection<StandardCredentials>> supplier = ConjurCredentialsSupplier.standard(mockContext);
            Collection<StandardCredentials> result = supplier.get();

            assertNotNull(result);
            assertEquals(2, result.size());
        }
    }

    @Test
    public void testGetConjurAPIThrowsExceptionReturnsEmptyListLogsError() {
        Supplier<Collection<StandardCredentials>> supplier = ConjurCredentialsSupplier.standard(mockContext);
        try (MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class)) {
            conjurApiMock.when(() ->
                    ConjurAPI.getCredentialsForContext(StandardCredentials.class, mockContext)
            ).thenThrow(new RuntimeException("Test failure"));
            Collection<StandardCredentials> result = supplier.get();

            assertTrue(result.isEmpty());
            String log = handler.getLog();
            assertTrue(log.contains("EXCEPTION: ConjurCredentialsSupplier: returned Test failure"));
        }
    }

    static class TestLogHandler extends Handler {
        private final StringBuilder logMessages = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            logMessages.append(record.getLevel())
                    .append(": ")
                    .append(record.getMessage())
                    .append("\n");
        }

        @Override
        public void flush() {
            // No-op: not needed for test handler
        }

        @Override
        public void close() throws SecurityException {
            // No-op: not needed for test handler
        }

        public String getLog() {
            return logMessages.toString();
        }
    }

}
