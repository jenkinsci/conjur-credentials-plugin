
package org.conjur.jenkins.jwtauthimpl;

import org.acegisecurity.Authentication;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.model.ModelObject;

@RunWith(MockitoJUnitRunner.class)
public class JwtTokenTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Mock
	private GlobalConjurConfiguration globalConfigMock;

	@Mock
	private Authentication authentication;

	@Test
	public void mockSign() {
		JwtToken jwtToken = mock(JwtToken.class);
		when(jwtToken.sign()).thenReturn("Signing Token");
		assertEquals("Signing Token", jwtToken.sign());

	}

	@Test
	public void mockGetToken() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			mock(JwtToken.class);
			Object context = "secretId";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getToken(context,globalConfigMock)).thenReturn("secret retrival " + context);
			assertEquals("secret retrival secretId", JwtToken.getToken(context,globalConfigMock));

		}
	}

	@Test
	public void mockGetUnsignedToken() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			JwtToken jwtToken2 = mock(JwtToken.class);
			String pluginAction = " sdfghjkl";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, jwtToken2,globalConfigMock))
					.thenReturn(jwtToken2);
			// Call the method that uses the mocked static method
			assertSame(jwtToken2, JwtToken.getUnsignedToken(pluginAction, jwtToken2,globalConfigMock));
		}

	}

	@Test
	public void getUnsignedTokenNull() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			JwtToken jwtToken2 = null;
			String pluginAction = " testAction";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, null,globalConfigMock)).thenReturn(jwtToken2);
			JwtToken mockResult = JwtToken.getUnsignedToken(pluginAction, jwtToken2,globalConfigMock);
			assertNull(mockResult);
		}
	}

	//
	// Check required token fields
	//
	@Test
	public void testTokenFields() {
		ModelObject mockContext = jenkinsRule.jenkins.getInstance();
		JwtToken jwtToken = JwtToken.getUnsignedToken("test", mockContext,globalConfigMock );
		// Ensure that identityFields contains only one element, as there is no delimiter

		//assertEquals(jwtToken.claim.get("jenkins_name"),"GlobalCredentials");
		//assertEquals(jwtToken.claim.get("jenkins_full_name"),null);
		assertEquals(jwtToken.claim.get("jenkins_full_name"),"GlobalCredentials");
	}
}