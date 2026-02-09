
package org.conjur.jenkins.configuration;

import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class GlobalConjurConfigurationTest {

    @Mock
    private GlobalConjurConfiguration config;

    @Mock
    private AbstractItem abstractItem;

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testGetConjurConfiguration() {
        assertNull(config.getConjurConfiguration());
    }

    @Test
    void testGetAuthWebServiceId() {
        assertNull(config.getAuthWebServiceId());
    }

    @Test
    void testGetJwtAudience() {
        assertNull(config.getJwtAudience());
    }

    @Test
    void testKeyLifetimeInMinutes() {
        assertEquals(0, config.getKeyLifetimeInMinutes());
    }

    @Test
    void testGetJwtAudienceReturnsValue() {
        GlobalConjurConfiguration conf = new GlobalConjurConfiguration();
        assertEquals("cyberark-conjur", conf.getJwtAudience());
    }

    @Test
    void testSetConjurConfiguration() {
        ConjurConfiguration mockConjurConfig = mock(ConjurConfiguration.class);
        GlobalConjurConfiguration globalConfig = new GlobalConjurConfiguration();
        globalConfig.setConjurConfiguration(mockConjurConfig);

        assertEquals(mockConjurConfig, globalConfig.getConjurConfiguration());
    }

    @Test
    void doCheckAuthWebServiceId() {
        try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
                GlobalConjurConfiguration.class)) {
            String authWebServiceId = "jenkins";
            getConfigMockStatic.when(() -> config.doCheckAuthWebServiceId(abstractItem, authWebServiceId))
                    .thenReturn(FormValidation.ok());

            assertEquals(FormValidation.ok(), config.doCheckAuthWebServiceId(abstractItem, authWebServiceId));
        }
    }

    @Test
    void doCheckAuthWebServiceIdEmpty() {
        try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
                GlobalConjurConfiguration.class)) {
            String authWebServiceId = "";
            String errorMsg = "Auth WebService Id should not be empty";
            getConfigMockStatic.when(() -> config.doCheckAuthWebServiceId(abstractItem, authWebServiceId))
                    .thenReturn(FormValidation.error(errorMsg));
            String actualErrorMessage = config.doCheckAuthWebServiceId(abstractItem, authWebServiceId).getMessage();

            assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
        }
    }


    @Test
    void testDoCheckAuthWebServiceIdEmpty() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertNotNull(result);
        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertEquals("Auth WebService Id should not be empty", result.getMessage());
    }

    @Test
    void testDoCheckAuthWebServiceIdBlank() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "   ";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertNotNull(result);
        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertEquals("Auth WebService Id should not be empty", result.getMessage());
    }

    @Test
    void testDoCheckAuthWebServiceIdValid() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "jenkinsValidId";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertEquals(FormValidation.ok(), result);
    }

    @Test
    void testSetAuthWebServiceId() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String testAuthWebServiceId = "test-Auth-id";
        getConfigMockStatic.setAuthWebServiceId(testAuthWebServiceId);

        assertEquals(testAuthWebServiceId, getConfigMockStatic.getAuthWebServiceId());
    }

    @Test
    void testSetKeyLifetimeInMinutes() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        long expectedLifetime = 120L;
        getConfigMockStatic.setKeyLifetimeInMinutes(expectedLifetime);

        assertEquals(expectedLifetime, getConfigMockStatic.getKeyLifetimeInMinutes());
    }

    @Test
    void testSetTokenDurationInSeconds() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        long tokenDurationInSeconds = 120L;
        getConfigMockStatic.setTokenDurationInSeconds(tokenDurationInSeconds);

        assertEquals(tokenDurationInSeconds, getConfigMockStatic.getTokenDurationInSeconds());
    }

    @Test
    void testSetSelectAuthenticator() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String auth = "JWT";
        getConfigMockStatic.setSelectAuthenticator(auth);

        assertEquals("JWT", getConfigMockStatic.getSelectAuthenticator());
    }

    @Test
    void testDoObtainJwtToken() {
        try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(GlobalConjurConfiguration.class)) {
            ModelObject item = mock(ModelObject.class);
            FormValidation expectedValidation = FormValidation.ok("JWT Token: { \"test\": \"value\" }");
            getConfigMockStatic.when(() -> config.doObtainJwtToken(item))
                    .thenReturn(expectedValidation);
            FormValidation result = config.doObtainJwtToken(item);

            assertEquals(expectedValidation, result);
        }
    }

    @Test
    void testDoObtainJwtTokenWithoutMock() {
        GlobalConjurConfiguration globalConfig = GlobalConjurConfiguration.get();
        JwtToken mockToken = mock(JwtToken.class);
        Item mockItem = mock(Item.class);
        try (MockedStatic<JwtToken> token = mockStatic(JwtToken.class)) {
            token.when(() -> JwtToken.getUnsignedToken("pluginAction", mockItem, globalConfig)).thenReturn(mockToken);
        }
        GlobalConjurConfiguration descriptor = new GlobalConjurConfiguration();

        assertEquals(FormValidation.ok().kind, descriptor.doObtainJwtToken(mockItem).kind);
    }

    @Test
    void testDoObtainJwtTokenWithoutMockToken() {
        GlobalConjurConfiguration globalConfig = GlobalConjurConfiguration.get();
        Item mockItem = mock(Item.class);
        try (MockedStatic<JwtToken> token = mockStatic(JwtToken.class)) {
            token.when(() -> JwtToken.getUnsignedToken("pluginAction", mockItem, globalConfig)).thenReturn(null);
        }
        GlobalConjurConfiguration descriptor = new GlobalConjurConfiguration();

        assertEquals(FormValidation.ok().kind, descriptor.doObtainJwtToken(mockItem).kind);
    }

}




	

