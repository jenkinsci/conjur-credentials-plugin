
package org.conjur.jenkins.jwtauth.impl;

import hudson.model.ModelObject;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.jose4j.jws.JsonWebSignature;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class JwtTokenTest {

    private JenkinsRule j;

    @Mock
    private GlobalConjurConfiguration globalConfigMock;

    @SuppressWarnings("deprecation")
    @Mock
    private Authentication authentication;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        GlobalConjurConfiguration mockConfig = mock(GlobalConjurConfiguration.class);
        when(mockConfig.getJwtAudience()).thenReturn("test-audience");
        when(mockConfig.getTokenDurationInSeconds()).thenReturn(600L);
        when(mockConfig.getKeyLifetimeInMinutes()).thenReturn(10L);
    }

    @Test
    void mockSign() {
        JwtToken jwtToken = mock(JwtToken.class);
        when(jwtToken.sign()).thenReturn("Signing Token");
        assertEquals("Signing Token", jwtToken.sign());

    }

    @Test
    void mockGetToken() {
        try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
            mock(JwtToken.class);
            Object context = "secretId";
            jwtTokenTestMockedStatic.when(() -> JwtToken.getToken(context, globalConfigMock)).thenReturn("secret retrieval " + context);

            assertEquals("secret retrieval secretId", JwtToken.getToken(context, globalConfigMock));
        }
    }

    @Test
    void mockGetUnsignedToken() {
        try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
            JwtToken jwtToken2 = mock(JwtToken.class);
            String pluginAction = " sdfghjkl";
            jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, jwtToken2, globalConfigMock))
                    .thenReturn(jwtToken2);

            assertSame(jwtToken2, JwtToken.getUnsignedToken(pluginAction, jwtToken2, globalConfigMock));
        }
    }

    @Test
    void getUnsignedTokenNull() {
        try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
            JwtToken jwtToken2 = null;
            String pluginAction = " testAction";
            jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, null, globalConfigMock)).thenReturn(jwtToken2);
            JwtToken mockResult = JwtToken.getUnsignedToken(pluginAction, jwtToken2, globalConfigMock);

            assertNull(mockResult);
        }
    }


    @Test
    void testTokenFields() {
        ModelObject mockContext = Jenkins.get();
        JwtToken jwtToken = JwtToken.getUnsignedToken("test", mockContext, globalConfigMock);

        assertEquals("GlobalCredentials", jwtToken.claim.get("jenkins_full_name"));
    }


    @Test
    void testGetUnsignedTokenReturnsNullIfGlobalConfigIsNull() {
        JwtToken token = JwtToken.getUnsignedToken("TestAction", new Object(), null);
        assertNull(token);
    }

    @Test
    void testGetCurrentSigningKeyGeneratesNewKey() {
        JwtToken token = new JwtToken();
        token.claim.put("exp", System.currentTimeMillis() / 1000 + 600);
        JwtRsaDigitalSignatureKey key = JwtToken.getCurrentSigningKey(token);

        assertNotNull(key);
        assertNotNull(key.getId());
    }

    @SuppressWarnings("static-access")
    @Test
    void testGetToken() {
        JwtToken mockToken = spy(new JwtToken());
        ModelObject mockObject = mock(ModelObject.class);
        assertNull(mockToken.getToken("testAction", null, globalConfigMock));
    }

    @SuppressWarnings("static-access")
    @Test
    void testGetTokenWithDifferentParameters() {
        JwtToken mockToken = spy(new JwtToken());

        assertNull(mockToken.getToken(null, globalConfigMock));
    }

    @Test
    void testGetJwkSet() {
        try (MockedStatic<JwtToken> mockToken = mockStatic(JwtToken.class)) {
            JSONObject jwks = new JSONObject();
            when(JwtToken.getJwkset()).thenReturn(jwks);

            assertNotNull(JwtToken.getJwkset());
        }
    }

    @Test
    void testSignReturnsString() throws Exception {
        JwtToken mockToken = spy(new JwtToken());
        JsonWebSignature mockSignature = mock(JsonWebSignature.class);
        when(mockSignature.getCompactSerialization()).thenReturn("signed");
        doReturn(mockSignature.getCompactSerialization()).when(mockToken).sign();

        assertNotNull(mockToken.sign());
    }

    @Test
    void testSign_ReturnsSignedJwtToken() throws Exception {
        // Generate real RSA keypair (2048-bit)
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        // Mock signing key
        JwtRsaDigitalSignatureKey mockKey = mock(JwtRsaDigitalSignatureKey.class);
        when(mockKey.toSigningKey()).thenReturn(privateKey); // real RSA key
        when(mockKey.getId()).thenReturn("test-key-id");

        // mock static JwtToken.getCurrentSigningKey(...)
        try (MockedStatic<JwtToken> mockedStatic = mockStatic(JwtToken.class, CALLS_REAL_METHODS)) {
            mockedStatic.when(() -> JwtToken.getCurrentSigningKey(any())).thenReturn(mockKey);

            JwtToken jwtToken = new JwtToken();
            jwtToken.claim.put("sub", "test-subject");
            jwtToken.claim.put("aud", "test-audience");
            jwtToken.claim.put("iss", "test-issuer");

            // Call the method
            String token = jwtToken.sign();

            assertNotNull(token);
            assertEquals(3, token.split("\\.").length); // Should be a valid JWT structure
        }
    }

}

