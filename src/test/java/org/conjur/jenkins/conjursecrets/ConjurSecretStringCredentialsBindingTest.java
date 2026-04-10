package org.conjur.jenkins.conjursecrets;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.conjur.jenkins.conjursecrets.ConjurSecretStringCredentialsBinding.DescriptorImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

public class ConjurSecretStringCredentialsBindingTest {

    private static final String VARIABLE_NAME = "MY_SECRET";
    private static final String CREDENTIAL_ID = "cred-id";

    private ConjurSecretStringCredentialsBinding binding;

    @Mock
    private Run<?, ?> mockRun;

    @Mock
    private FilePath mockWorkspace;

    @Mock
    private Launcher mockLauncher;

    @Mock
    private TaskListener mockListener;

    @Mock
    private ConjurSecretStringCredentials mockCredentials;

    @Before
    public void setUp() throws Exception, SecurityException {
        binding = new ConjurSecretStringCredentialsBinding(VARIABLE_NAME, CREDENTIAL_ID);
    }

    @Test
    public void testType() {
        assertNotNull(binding.type());
    }

    @Test
    public void testDescriptorImplReturnsCorrectDisplayNameAndType() {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals("Secret String Credential", descriptor.getDisplayName());
        assertEquals(ConjurSecretStringCredentials.class, descriptor.type());
        assertFalse(descriptor.requiresWorkspace());
    }
}
