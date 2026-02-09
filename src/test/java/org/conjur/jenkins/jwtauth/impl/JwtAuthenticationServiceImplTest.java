package org.conjur.jenkins.jwtauth.impl;

import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationServiceImplTest {

    private JwtAuthenticationServiceImpl service;

    @BeforeEach
    void beforeEach() {
        service = new JwtAuthenticationServiceImpl();
    }

    @Test
    void testGetJwkSetReturnsJwkSet() throws Exception {
        GlobalConjurConfiguration mockConfig = mock(GlobalConjurConfiguration.class);
        ExtensionList<GlobalConfiguration> extensionListMock = mock(ExtensionList.class);
        MockedStatic<GlobalConfiguration> globalConfigMockedStatic = mockStatic(GlobalConfiguration.class);
        globalConfigMockedStatic.when(GlobalConfiguration::all).thenReturn(extensionListMock);
        when(extensionListMock.get(GlobalConjurConfiguration.class)).thenReturn(mockConfig);
        JSONObject mockJwkSet = new JSONObject();
        mockJwkSet.put("key", "value");
        MockedStatic<JwtToken> jwtTokenMockedStatic = mockStatic(JwtToken.class);
        jwtTokenMockedStatic.when(JwtToken::getJwkset).thenReturn(mockJwkSet);

        String result = service.getJwkSet();

        assertNotNull(result);
        assertTrue(result.contains("\"key\""));
        assertTrue(result.contains("\"value\""));

        globalConfigMockedStatic.close();
        jwtTokenMockedStatic.close();
    }

    @Test
    void testGetJwkSetReturnsNullWhenNoConfig() throws Exception {
        ExtensionList<GlobalConfiguration> extensionListMock = mock(ExtensionList.class);
        MockedStatic<GlobalConfiguration> globalConfigMockedStatic = mockStatic(GlobalConfiguration.class);
        globalConfigMockedStatic.when(GlobalConfiguration::all).thenReturn(extensionListMock);
        when(extensionListMock.get(GlobalConjurConfiguration.class)).thenReturn(null);

        String result = service.getJwkSet();

        assertNull(result);

        globalConfigMockedStatic.close();
    }

    @Test
    void testGetIconFileNameReturnsNull() {
        assertNull(service.getIconFileName());
    }

    @Test
    void testGetDisplayNameReturnsValue() {
        assertEquals("Conjur JWT endpoint", service.getDisplayName());
    }

    @Test
    void testGetJwkSetHandlesException() throws Exception {
        GlobalConjurConfiguration mockConfig = mock(GlobalConjurConfiguration.class);
        ExtensionList<GlobalConfiguration> extensionListMock = mock(ExtensionList.class);
        MockedStatic<GlobalConfiguration> globalConfigMockedStatic = mockStatic(GlobalConfiguration.class);
        globalConfigMockedStatic.when(GlobalConfiguration::all).thenReturn(extensionListMock);
        when(extensionListMock.get(GlobalConjurConfiguration.class)).thenReturn(mockConfig);
        MockedStatic<JwtToken> jwtTokenMockedStatic = mockStatic(JwtToken.class);
        jwtTokenMockedStatic.when(JwtToken::getJwkset)
                .thenThrow(new RuntimeException("Simulated exception"));

        String result = service.getJwkSet();

        assertNull(result);

        globalConfigMockedStatic.close();
        jwtTokenMockedStatic.close();
    }

}
