

package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.credentials.ConjurCredentialProvider;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurSecretCredentialsBindingTest {

    private JenkinsRule j;

    @Mock
    public ConjurSecretCredentialsBinding binding;

    public CredentialsStore store;

    private static final String TEST_CREDENTIAL_ID = "test-id";
    private static final String SECRET_VALUE = "MySecret";
    private static final String VARIABLE_NAME = "MY_SECRET";

    private ConjurSecretCredentialsBinding secretBinding;
    private Run<?, ?> mockRun;
    private FilePath mockFilePath;
    private Launcher mockLauncher;
    private TaskListener mockListener;
    private ConjurSecretCredentials mockCredentials;
    private BindingDescriptor<ConjurSecretCredentials> bindCred = null;
    private final Jenkins jenkins = null;
    private ConjurCredentialProvider provider;
    private ModelObject context;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        bindCred = new ConjurSecretCredentialsBinding.DescriptorImpl();
        secretBinding = new ConjurSecretCredentialsBinding(TEST_CREDENTIAL_ID);
        secretBinding.setVariable(VARIABLE_NAME);
        mockRun = mock(Run.class);
        mockFilePath = mock(FilePath.class);
        mockLauncher = mock(Launcher.class);
        mockListener = mock(TaskListener.class);
        mockCredentials = mock(ConjurSecretCredentials.class);
        Secret mockSecret = mock(Secret.class);
        when(mockSecret.getPlainText()).thenReturn(SECRET_VALUE);
        when(mockCredentials.getSecret()).thenReturn(mockSecret);
        doNothing().when(mockCredentials).setContext(any());
    }

    @Test
    void testConstructor() {
        Object expectedObj = new ConjurSecretCredentialsBinding("testPipeline");
        assertNotEquals(expectedObj, binding);
    }

    @Test
    void testBind1() throws Exception {
        Map<String, String> secretVals = new HashMap<>();
        MultiEnvironment env = new MultiEnvironment(secretVals);
        when(binding.bind(any(), any(), any(), any())).thenReturn(env);
        assertInstanceOf(MultiEnvironment.class, binding.bind(any(), any(), any(), any()));
    }


    @Test
    void testVariable() {
        ConjurSecretCredentialsBinding bindObj1 = new ConjurSecretCredentialsBinding("testPipeline");
        String actualVariable = "pipeline";
        bindObj1.setVariable(actualVariable);

        assertEquals(actualVariable, bindObj1.getVariable());
    }

    @Test
    void testVariables() {
        ConjurSecretCredentialsBinding bind = new ConjurSecretCredentialsBinding("Dev-Team-1");
        Set<String> varSet = new HashSet<>();
        String variable = "test";
        varSet.add(variable);
        bind.setVariable("test");
        Set<String> actualVarSet = bind.variables();

        assertEquals(varSet, actualVarSet);
    }

    @Nested
    class DescriptorImpl {

        @Test
        void testGetDisplayName() {
            assertEquals("Conjur Secret credentials", bindCred.getDisplayName());
        }

        @Test
        void testRequiresWorkspace() {
            boolean condition = false;
            assertEquals(condition, bindCred.requiresWorkspace());
        }

        @Test
        void testRequiresWorkspace1() {
            ConjurSecretUsernameCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();
            boolean result = bindCred1.requiresWorkspace();

            assertFalse(result);
        }

        @Test
        void testRequiresWorkspace2() {
            ConjurSecretUsernameCredentialsBinding.DescriptorImpl bindCred2 = new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();
            assertFalse(bindCred2.requiresWorkspace(), "requiresWorkspace should return false");
        }

        @Test
        void testType() {
            ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();
            Class<ConjurSecretCredentials> expected = ConjurSecretCredentials.class;

            assertEquals(expected, bindCred1.type());
        }
    }

    @Test
    void testTypeOfDescriptor() {
        ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();
        Class<ConjurSecretCredentials> expected = ConjurSecretCredentials.class;

        assertEquals(expected, bindCred1.type());
    }

    @Test
    void testRequiresWorkspace() {
        boolean condition = false;
        ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();

        assertEquals(condition, bindCred1.requiresWorkspace());
    }

    @Test
    void testType() {
        ConjurSecretCredentialsBinding bind = new ConjurSecretCredentialsBinding("Dev-Team-1");
        assertNotNull(bind.type());
    }

    @SuppressWarnings("deprecation")
    @Test
    void testBind() throws Exception {
        try (MockedStatic<CredentialsProvider> ignored = mockStatic(CredentialsProvider.class)) {
            when(CredentialsProvider.findCredentialById(eq(TEST_CREDENTIAL_ID), eq(ConjurSecretCredentials.class), eq(mockRun)))
                    .thenReturn(mockCredentials);
            MultiBinding.MultiEnvironment env = secretBinding.bind(mockRun, mockFilePath, mockLauncher, mockListener);
            Map<String, String> secretMap = env.getValues();

            assertTrue(secretMap.containsKey(VARIABLE_NAME));
            assertEquals(SECRET_VALUE, secretMap.get(VARIABLE_NAME));
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void testBindReturnsEmptyEnvironmentWhenCredentialNotFound() throws Exception {
        try (MockedStatic<CredentialsProvider> ignored = mockStatic(CredentialsProvider.class)) {
            when(CredentialsProvider.findCredentialById(eq(TEST_CREDENTIAL_ID), eq(ConjurSecretCredentials.class), eq(mockRun)))
                    .thenReturn(null);
            MultiBinding.MultiEnvironment env = secretBinding.bind(mockRun, mockFilePath, mockLauncher, mockListener);

            assertTrue(env.getValues().isEmpty());
        }
    }
}