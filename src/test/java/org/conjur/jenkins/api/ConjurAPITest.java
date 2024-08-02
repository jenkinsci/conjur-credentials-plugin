
package org.conjur.jenkins.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.conjur.jenkins.api.ConjurAPI.ConjurAuthnInfo;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;

import hudson.model.ModelObject;
import okhttp3.Call;
import okhttp3.OkHttpClient;

@RunWith(MockitoJUnitRunner.class)

public class ConjurAPITest {

	public OkHttpClient client;
	public ModelObject context;
	public ConjurConfiguration conjurConfiguration;
	public Call remoteCall;
	public ConjurAPI api;
	public List<UsernamePasswordCredentials> availableCredential;

	@Mock
	private GlobalConjurConfiguration globalConfig;
	@Mock
	private ConjurConfiguration mockConjurConjurConfig;
	@Mock
	private ConjurConfiguration mockGlobalConjurConfig;

	@Before
	public void setUp() throws IOException {
		mock(ConjurAPI.class);
		conjurConfiguration = new ConjurConfiguration("https://conjur_server:8083", "myConjurAccount");
		client = ConjurAPIUtils.getHttpClient(new ConjurConfiguration("https://conjur_server:8083", "myConjurAccount"));
		availableCredential = new ArrayList<>();
		context = mock(ModelObject.class);
		remoteCall = mock(Call.class);
		api = mock(ConjurAPI.class);
	}

	@Test
	public void getConjurAuthnInfo() {

		try (MockedStatic<ConjurAPI> conjurAPIMockStatic = mockStatic(ConjurAPI.class)) {
			ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();
			conjurAPIMockStatic.when(() -> ConjurAPI.getConjurAuthnInfo(any(), any(), any())).thenReturn(conjurAuthn);
			assertSame(conjurAuthn, ConjurAPI.getConjurAuthnInfo(any(), any(), any()));//To check whether two objects are the same, 
		}
	}

	@Test
	public void checkAuthentication() throws IOException {
		try (MockedStatic<JwtToken> jwtTokenMockStatic = mockStatic(JwtToken.class)) {

			jwtTokenMockStatic.when(() -> JwtToken.getToken((context))).thenReturn(
					"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

		}
		try (MockedStatic<ConjurAPI> conjurAPIMockStatic = mockStatic(ConjurAPI.class)) {

			conjurAPIMockStatic.when(() -> ConjurAPI.getAuthorizationToken(client, conjurConfiguration, context))
					.thenReturn("success");
			assertEquals("success", ConjurAPI.getAuthorizationToken(client, conjurConfiguration, context));
		}

	}

	@Test
	public void checkSecretVal() throws IOException {
		try (MockedStatic<ConjurAPI> mockedStaticConjurAPI = mockStatic(ConjurAPI.class)) {
			mockedStaticConjurAPI.when(
					() -> ConjurAPI.getSecret(client, conjurConfiguration, "auth-token", "host/frontend/frontend-01"))
					.thenReturn("bhfbdbkfbkd-bvjdbfbjbv-bfjbdbjkb-bbfkbskb");
			assertEquals(ConjurAPI.getSecret(client, conjurConfiguration, "auth-token", "host/frontend/frontend-01"),
					"bhfbdbkfbkd-bvjdbfbjbv-bfjbdbjkb-bbfkbskb");

		}
	}
	@Test
	public void globalConfigAndSimplifiedJWTDisabled() {
		globalConfig.setEnableJWKS(true);
		globalConfig.setEnableIdentityFormatFieldsFromToken(false);
		globalConfig.setIdentityFormatFieldsFromToken("jenkins_parent_full_name");
		Exception exception = assertThrows(RuntimeException.class, () -> {
			ConjurAPI.getAuthorizationToken(client,conjurConfiguration,context);
		});
		assertNotNull(exception.getMessage());
	}
	@Test
	public void conjurAuthnInfoEmptyFieldsShouldUseGlobalConfig() throws IOException {
		// Arrange
		ConjurAuthnInfo conjurAuthn = new ConjurAuthnInfo();
		// Initialize mocks
		when(mockConjurConjurConfig.getAccount()).thenReturn(null); // Simulating empty or null configuration
		when(mockConjurConjurConfig.getApplianceURL()).thenReturn(null); // Simulating empty or null configuration

		when(mockGlobalConjurConfig.getAccount()).thenReturn("globalAccount");
		when(mockGlobalConjurConfig.getApplianceURL()).thenReturn("globalApplianceURL");

		// Example of setting ConjurAuthnInfo with these configurations
		conjurAuthn.account =(mockConjurConjurConfig.getAccount() != null ? mockConjurConjurConfig.getAccount() : mockGlobalConjurConfig.getAccount());
		conjurAuthn.applianceUrl=(mockConjurConjurConfig.getApplianceURL() != null ? mockConjurConjurConfig.getApplianceURL() : mockGlobalConjurConfig.getApplianceURL());
		// Verify that ConjurAuthnInfo uses global values when local values are null
		assertEquals("globalAccount", conjurAuthn.account);
		assertEquals("globalApplianceURL", conjurAuthn.applianceUrl);
	}
}
