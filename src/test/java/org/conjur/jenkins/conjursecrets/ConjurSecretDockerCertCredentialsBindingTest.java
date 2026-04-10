package org.conjur.jenkins.conjursecrets;

import org.conjur.jenkins.conjursecrets.ConjurSecretDockerCertCredentialsBinding.DescriptorImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretDockerCertCredentialsBindingTest {

    private static final String CREDENTIAL_ID = "cred-id";
    private ConjurSecretDockerCertCredentialsBinding binding;

    @Before
    public void setUp() throws Exception, SecurityException {
        binding = new ConjurSecretDockerCertCredentialsBinding(CREDENTIAL_ID);
    }

    @Test
    public void testType() {
        assertNotNull(binding.type());
    }

    @Test
    public void testDescriptorImplReturnsCorrectDisplayNameAndType() {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals("Conjur Secret Docker Certificate credentials", descriptor.getDisplayName());
        assertEquals(ConjurSecretDockerCertCredentials.class, descriptor.type());
        assertFalse(descriptor.requiresWorkspace());
    }

    @Test
    public void testGetClientKeyVariable() {
        binding.setClientKeyVariable("key-id");

        assertEquals("key-id", binding.getClientKeyVariable());
    }

    @Test
    public void testGetClientCertVariable() {
        binding.setClientCertVariable("cert-id");

        assertEquals("cert-id", binding.getClientCertVariable());
    }

    @Test
    public void testGetCaCertificateVariable() {
        binding.setCaCertificateVariable("certca-id");

        assertEquals("certca-id", binding.getCaCertificateVariable());
    }

    @Test
    public void testVariables() {
        Set<String> varSet = new HashSet<String>();
        String clientKeyVariable = "key-id";
        String clientCertVariable = "cert-id";
        String caCertificateVariable = "certca-id";
        varSet.add(clientKeyVariable);
        varSet.add(clientCertVariable);
        varSet.add(caCertificateVariable);
        binding.setClientKeyVariable("key-id");
        binding.setClientCertVariable("cert-id");
        binding.setCaCertificateVariable("certca-id");
        Set<String> actualVarSet = binding.variables();

        assertEquals(varSet, actualVarSet);
    }
}
