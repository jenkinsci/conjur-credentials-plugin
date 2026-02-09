package org.conjur.jenkins.conjursecrets;

import hudson.FilePath;
import org.conjur.jenkins.conjursecrets.ConjurSecretFileCredentialsBinding.DescriptorImpl;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurSecretFileCredentialsBindingTest {

    private static final String CREDENTIAL_ID = "cred-id";

    private ConjurSecretFileCredentialsBinding binding;

    @Mock
    private ConjurSecretFileCredentialsBinding mockBinding;

    @BeforeEach
    void beforeEach() {
        binding = new ConjurSecretFileCredentialsBinding(CREDENTIAL_ID);
    }

    @Test
    void testType() {
        assertNotNull(binding.type());
    }

    @Test
    void testGetFileVariable() {
        String fileName = "file-name";
        binding.setFileVariable(fileName);

        assertEquals("file-name", binding.getFileVariable());
    }

    @Test
    void testGetContentVariable() {
        String contentName = "content-name";
        binding.setContentVariable(contentName);

        assertEquals("content-name", binding.getContentVariable());
    }

    @Test
    void testBind1() throws Exception {
        Map<String, String> secretVals = new HashMap<>();
        MultiEnvironment env = new MultiEnvironment(secretVals);
        when(mockBinding.bind(any(), any(), any(), any())).thenReturn(env);

        assertInstanceOf(MultiEnvironment.class, mockBinding.bind(any(), any(), any(), any()));
    }

    @Test
    void testVariables() {
        Set<String> varSet = new HashSet<>();
        String variable = "test-file";
        String contentVar = "content-test";
        varSet.add(variable);
        varSet.add(contentVar);
        binding.setFileVariable(variable);
        binding.setContentVariable(contentVar);
        Set<String> actualVarSet = binding.variables();

        assertEquals(varSet, actualVarSet);
    }

    @Test
    void testDescriptorImplReturnsCorrectDisplayNameAndType() {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals("Conjur Secret File Credentials", descriptor.getDisplayName());
        assertEquals(ConjurSecretFileCredentials.class, descriptor.type());
        assertTrue(descriptor.requiresWorkspace());
    }

    @Test
    void testCleanupAction() {
        FilePath mockPath = mock(FilePath.class);
        when(mockPath.getRemote()).thenReturn("/test/job");
        ConjurSecretFileCredentialsBinding.CleanupAction cleanupAction = new ConjurSecretFileCredentialsBinding.CleanupAction(mockPath);

        String path = cleanupAction.getPath();
        assertEquals("/test/job", path);
    }

}
