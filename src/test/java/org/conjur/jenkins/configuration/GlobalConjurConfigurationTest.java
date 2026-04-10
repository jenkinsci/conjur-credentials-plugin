
package org.conjur.jenkins.configuration;

import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;


@RunWith(MockitoJUnitRunner.class)
public class GlobalConjurConfigurationTest {

    @Mock
    private GlobalConjurConfiguration config;

    @Mock
    private AbstractItem abstractItem;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetConjurConfiguration() {
        assertNull(config.getConjurConfiguration());
    }

    @Test
    public void testGetAuthWebServiceId() {
        assertNull(config.getAuthWebServiceId());
    }

    @Test
    public void testGetJwtAudience() {
        assertNull(config.getJwtAudience());
    }

    @Test
    public void testKeyLifetimeInMinutes() {
        assertEquals(0, config.getKeyLifetimeInMinutes());
    }


    @Test
    public void testGetJwtAudienceReturnsValue() {
        GlobalConjurConfiguration conf = new GlobalConjurConfiguration();
        assertEquals("cyberark-conjur", conf.getJwtAudience());
    }

    @Test
    public void testSetConjurConfiguration() {
        ConjurConfiguration mockConjurConfig = mock(ConjurConfiguration.class);
        GlobalConjurConfiguration globalConfig = new GlobalConjurConfiguration();
        globalConfig.setConjurConfiguration(mockConjurConfig);

        assertEquals(mockConjurConfig, globalConfig.getConjurConfiguration());
    }

    @Test
    public void doCheckAuthWebServiceId() {
        try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
                GlobalConjurConfiguration.class)) {
            String authWebServiceId = "jenkins";
            getConfigMockStatic.when(() -> config.doCheckAuthWebServiceId(abstractItem, authWebServiceId))
                    .thenReturn(FormValidation.ok());

            assertEquals(FormValidation.ok(), config.doCheckAuthWebServiceId(abstractItem, authWebServiceId));
        }
    }

    @Test
    public void doCheckAuthWebServiceIdEmpty() {
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
    public void testDoCheckAuthWebServiceIdEmpty() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertNotNull(result);
        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertEquals("Auth WebService Id should not be empty", result.getMessage());
    }

    @Test
    public void testDoCheckAuthWebServiceIdBlank() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "   ";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertNotNull(result);
        assertEquals(FormValidation.Kind.ERROR, result.kind);
        assertEquals("Auth WebService Id should not be empty", result.getMessage());
    }

    @Test
    public void testDoCheckAuthWebServiceIdValid() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String authWebServiceId = "jenkinsValidId";
        FormValidation result = getConfigMockStatic.doCheckAuthWebServiceId(abstractItem, authWebServiceId);

        assertEquals(FormValidation.ok(), result);
    }

    @Test
    public void testSetAuthWebServiceId() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String testAuthWebServiceId = "test-Auth-id";
        getConfigMockStatic.setAuthWebServiceId(testAuthWebServiceId);

        assertEquals(testAuthWebServiceId, getConfigMockStatic.getAuthWebServiceId());
    }

    @Test
    public void testSetKeyLifetimeInMinutes() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        long expectedLifetime = 120L;
        getConfigMockStatic.setKeyLifetimeInMinutes(expectedLifetime);

        assertEquals(expectedLifetime, getConfigMockStatic.getKeyLifetimeInMinutes());
    }

    @Test
    public void testSetTokenDurationInSeconds() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        long tokenDurationInSeconds = 120L;
        getConfigMockStatic.setTokenDurationInSeconds(tokenDurationInSeconds);

        assertEquals(tokenDurationInSeconds, getConfigMockStatic.getTokenDurationInSeconds());
    }

    @Test
    public void testSetSelectAuthenticator() {
        GlobalConjurConfiguration getConfigMockStatic = new GlobalConjurConfiguration();
        String auth = "JWT";
        getConfigMockStatic.setSelectAuthenticator(auth);

        assertEquals("JWT", getConfigMockStatic.getSelectAuthenticator());
    }

    @Test
    public void testDoObtainJwtToken() {
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
    public void testDoObtainJwtTokenWithoutMock() {
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
    public void testDoObtainJwtTokenWithoutMockToken() {
        GlobalConjurConfiguration globalConfig = GlobalConjurConfiguration.get();
        Item mockItem = mock(Item.class);
        try (MockedStatic<JwtToken> token = mockStatic(JwtToken.class)) {
            token.when(() -> JwtToken.getUnsignedToken("pluginAction", mockItem, globalConfig)).thenReturn(null);
        }
        GlobalConjurConfiguration descriptor = new GlobalConjurConfiguration();

        assertEquals(FormValidation.ok().kind, descriptor.doObtainJwtToken(mockItem).kind);
    }

}




	

