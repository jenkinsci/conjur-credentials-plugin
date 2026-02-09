package org.conjur.jenkins.conjursecrets;

import org.conjur.jenkins.conjursecrets.ConjurSecretDockerCertCredentialsBinding.DescriptorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurSecretDockerCertCredentialsBindingTest {

    private static final String CREDENTIAL_ID = "cred-id";
    private ConjurSecretDockerCertCredentialsBinding binding;

    @BeforeEach
    void beforeEach() {
        binding = new ConjurSecretDockerCertCredentialsBinding(CREDENTIAL_ID);
    }

    @Test
    void testType() {
        assertNotNull(binding.type());
    }

    @Test
    void testDescriptorImplReturnsCorrectDisplayNameAndType() {
        DescriptorImpl descriptor = new DescriptorImpl();

        assertEquals("Conjur Secret Docker Certificate credentials", descriptor.getDisplayName());
        assertEquals(ConjurSecretDockerCertCredentials.class, descriptor.type());
        assertFalse(descriptor.requiresWorkspace());
    }

    @Test
    void testGetClientKeyVariable() {
        binding.setClientKeyVariable("key-id");

        assertEquals("key-id", binding.getClientKeyVariable());
    }

    @Test
    void testGetClientCertVariable() {
        binding.setClientCertVariable("cert-id");

        assertEquals("cert-id", binding.getClientCertVariable());
    }

    @Test
    void testGetCaCertificateVariable() {
        binding.setCaCertificateVariable("certca-id");

        assertEquals("certca-id", binding.getCaCertificateVariable());
    }

    @Test
    void testVariables() {
        Set<String> varSet = new HashSet<>();
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
