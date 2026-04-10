

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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Nested;
import org.junit.runner.RunWith;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretCredentialsBindingTest<I> {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
    private Jenkins jenkins = null;
    private ConjurCredentialProvider provider;
    private ModelObject context;

    @Before
    public void init() throws IOException, InterruptedException, ReactorException {
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
    public void testConstuctor() {
        Object expectedObj = new ConjurSecretCredentialsBinding("testPipeline");
        assertNotEquals(expectedObj, binding);
    }

    @Test
    public void testBind1() throws IOException, InterruptedException {
        Map<String, String> secretVals = new HashMap<>();
        MultiEnvironment env = new MultiEnvironment(secretVals);
        when(binding.bind(any(), any(), any(), any())).thenReturn(env);
        assertInstanceOf(MultiEnvironment.class, binding.bind(any(), any(), any(), any()));
    }


    @Test
    public void testVariable() {
        ConjurSecretCredentialsBinding bindObj1 = new ConjurSecretCredentialsBinding("testPipeline");
        String actualVariable = "pipeline";
        bindObj1.setVariable(actualVariable);

        assertEquals(actualVariable, bindObj1.getVariable());
    }

    @Test
    public void testVariables() {
        ConjurSecretCredentialsBinding bind = new ConjurSecretCredentialsBinding("Dev-Team-1");
        Set<String> varSet = new HashSet<String>();
        String variable = "test";
        varSet.add(variable);
        bind.setVariable("test");
        Set<String> actualVarSet = bind.variables();

        assertEquals(varSet, actualVarSet);
    }

    @Nested
    public class DescriptorImpl extends BindingDescriptor<ConjurSecretCredentials> {

        @Test
        public void testGetDisplayName() {
            assertEquals("Conjur Secret credentials", bindCred.getDisplayName());
        }

        @Test
        public void testRequiresWorkspace() {
            boolean condition = false;
            assertEquals(condition, bindCred.requiresWorkspace());
        }

        @Test
        public void testRequiresWorkspace1() {
            ConjurSecretUsernameCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();
            boolean result = bindCred1.requiresWorkspace();

            assertFalse(result);
        }

        @Test
        public void testRequiresWorkspace2() {
            ConjurSecretUsernameCredentialsBinding.DescriptorImpl bindCred2 = new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();
            assertFalse(bindCred2.requiresWorkspace(), "requiresWorkspace should return false");
        }

        @Test
        public void testType() {
            ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();
            Class<ConjurSecretCredentials> expected = ConjurSecretCredentials.class;

            assertEquals(expected, bindCred1.type());
        }

        @Override
        protected Class<ConjurSecretCredentials> type() {
            // TODO Auto-generated method stub
            return ConjurSecretCredentials.class;
        }

    }

    @Test
    public void testTypeOfDescriptor() {
        ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();
        Class<ConjurSecretCredentials> expected = ConjurSecretCredentials.class;

        assertEquals(expected, bindCred1.type());
    }

    @Test
    public void testRequiresWorkspace() {
        boolean condition = false;
        ConjurSecretCredentialsBinding.DescriptorImpl bindCred1 = new ConjurSecretCredentialsBinding.DescriptorImpl();

        assertEquals(condition, bindCred1.requiresWorkspace());
    }

    @Test
    public void testType() {
        ConjurSecretCredentialsBinding bind = new ConjurSecretCredentialsBinding("Dev-Team-1");
        assertNotNull(bind.type());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testBind() throws Exception {
        mockCredentialLookup();
        MultiBinding.MultiEnvironment env = secretBinding.bind(mockRun, mockFilePath, mockLauncher, mockListener);
        Map<String, String> secretMap = env.getValues();

        assertTrue(secretMap.containsKey(VARIABLE_NAME));
        assertEquals(SECRET_VALUE, secretMap.get(VARIABLE_NAME));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testBindReturnsEmptyEnvironmentWhenCredentialNotFound() throws Exception {
        mockStatic(CredentialsProvider.class);
        when(CredentialsProvider.findCredentialById(eq(TEST_CREDENTIAL_ID), eq(ConjurSecretCredentials.class), eq(mockRun)))
                .thenReturn(null);
        MultiBinding.MultiEnvironment env = secretBinding.bind(mockRun, mockFilePath, mockLauncher, mockListener);

        assertTrue(env.getValues().isEmpty());
    }

    private void mockCredentialLookup() {
        mockStatic(CredentialsProvider.class);
        when(CredentialsProvider.findCredentialById(eq(TEST_CREDENTIAL_ID), eq(ConjurSecretCredentials.class), eq(mockRun)))
                .thenReturn(mockCredentials);
    }
}