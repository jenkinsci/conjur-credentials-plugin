package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.Map;

import org.conjur.jenkins.api.ConjurAPI;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ConjurDirectCredentialBindingTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String CONJUR_PATH = "team/myapp/db_password";
    private static final String SECRET_VALUE = "s3cr3t";
    private static final String VARIABLE_NAME = "MY_SECRET";

    private ConjurDirectCredentialBinding binding;
    private Run<?, ?> mockRun;
    private FilePath mockFilePath;
    private Launcher mockLauncher;
    private TaskListener mockListener;

    @Before
    public void setUp() {
        binding = new ConjurDirectCredentialBinding(CONJUR_PATH);
        binding.setVariable(VARIABLE_NAME);
        mockRun = mock(Run.class);
        mockFilePath = mock(FilePath.class);
        mockLauncher = mock(Launcher.class);
        mockListener = mock(TaskListener.class);
        when(mockRun.getFullDisplayName()).thenReturn("test-build #1");
    }

    @Test
    public void testConstructor() {
        ConjurDirectCredentialBinding b = new ConjurDirectCredentialBinding(CONJUR_PATH);
        assertNotNull(b);
    }

    @Test
    public void testGetVariable() {
        assertEquals(VARIABLE_NAME, binding.getVariable());
    }

    @Test
    public void testSetVariable() {
        binding.setVariable("OTHER_VAR");
        assertEquals("OTHER_VAR", binding.getVariable());
    }

    @Test
    public void testVariables() {
        assertTrue(binding.variables().contains(VARIABLE_NAME));
        assertEquals(1, binding.variables().size());
    }

    @Test
    public void testType() {
        assertNotNull(binding.type());
        assertEquals(ConjurSecretCredentials.class, binding.type());
    }

    @Test
    public void testBind() throws IOException, InterruptedException {
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn(SECRET_VALUE);

        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), any(), any()))
                    .thenReturn(mockSecret);

            MultiEnvironment env = binding.bind(mockRun, mockFilePath, mockLauncher, mockListener);
            Map<String, String> values = env.getValues();

            assertTrue(values.containsKey(VARIABLE_NAME));
            assertEquals(SECRET_VALUE, values.get(VARIABLE_NAME));
        }
    }

    @Test
    public void testBindReturnsEmptyStringWhenSecretIsNull() throws IOException, InterruptedException {
        try (MockedStatic<ConjurAPI> mockAPI = mockStatic(ConjurAPI.class)) {
            mockAPI.when(() -> ConjurAPI.getSecretFromConjurWithInheritance(any(), any(), any()))
                    .thenReturn(null);

            MultiEnvironment env = binding.bind(mockRun, mockFilePath, mockLauncher, mockListener);
            Map<String, String> values = env.getValues();

            assertTrue(values.containsKey(VARIABLE_NAME));
            assertEquals("", values.get(VARIABLE_NAME));
        }
    }

    @Test
    public void testDescriptorDisplayName() {
        ConjurDirectCredentialBinding.DescriptorImpl descriptor = new ConjurDirectCredentialBinding.DescriptorImpl();
        assertEquals("Conjur Direct Secret Credential", descriptor.getDisplayName());
    }

    @Test
    public void testDescriptorRequiresWorkspace() {
        ConjurDirectCredentialBinding.DescriptorImpl descriptor = new ConjurDirectCredentialBinding.DescriptorImpl();
        assertFalse(descriptor.requiresWorkspace());
    }

    @Test
    public void testDescriptorType() {
        ConjurDirectCredentialBinding.DescriptorImpl descriptor = new ConjurDirectCredentialBinding.DescriptorImpl();
        assertEquals(ConjurSecretCredentials.class, descriptor.type());
    }
}
