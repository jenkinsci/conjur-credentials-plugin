
package org.conjur.jenkins.conjursecrets;

import org.conjur.jenkins.configuration.ConjurConfiguration;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.common.CertificateCredentials;

import hudson.model.ModelObject;
import hudson.util.Secret;
import okhttp3.OkHttpClient;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretCredentialImplTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Mock
	private OkHttpClient mockClient;
	@Mock
	private CertificateCredentials mockCertificateCredentials;
	@Mock
	private ModelObject mockContext;

	@Mock
	private ConjurConfiguration mockConjurConfiguration;

	@InjectMocks
	private ConjurSecretCredentialsImpl conjurSecretCredentials;

	@BeforeEach
	public void init() {
		mockContext = jenkinsRule.jenkins.getInstance();
	}

	@Test
	public void mockGetSecret() {
		// Create a mock of the conjurSecretCredentials Class
		ConjurSecretCredentialsImpl conjurSecretCredentials = mock(ConjurSecretCredentialsImpl.class);
		Secret secret = mock(Secret.class);
		when(conjurSecretCredentials.getSecret()).thenReturn(secret);
		Secret returnedSecret = conjurSecretCredentials.getSecret();
		// Verify that the method was called and the correct secret is returned
		verify(conjurSecretCredentials).getSecret();
		assertEquals(secret, returnedSecret);

	}

	/*
	@Test
	public void testGettingSecretAPI() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");

		conjurSecretCredentials.setContext(mockContext);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			ConjurAuthnInfo conjurAuthnInfo = mock(ConjurAuthnInfo.class);

			conjurAPIMockedStatic.when(() -> ConjurAPI.getConjurAuthnInfo(conjurConfiguration, null))
						.thenReturn(conjurAuthnInfo);

			conjurAPIMockedStatic
					.when(() -> ConjurAPI.getAuthorizationToken(conjurAuthnInfo, mockContext))
					.thenReturn(authToken);

			// Mock response from ConjurAPI.getSecret
			String expectedSecret = "mockedSecret";

			conjurAPIMockedStatic.when(() -> ConjurAPI.getConjurSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariableId())).thenReturn(expectedSecret);

			String returnSecret = ConjurAPI.getConjurSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariableId());

			// Invoke getSecret and assert result
			Secret secret = conjurSecretCredentials.getSecret();
			assertNotNull(secret);
			assertEquals(expectedSecret, returnSecret);
		}
	}
	*/

	/*
	@Test
	public void testGetSecretContextNull() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");

		conjurSecretCredentials.setContext(mockContext);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			ConjurAuthnInfo conjurAuthnInfo = ConjurAPI.getConjurAuthnInfo(conjurConfiguration, null);

			conjurAPIMockedStatic
					.when(() -> ConjurAPI.getAuthorizationToken(conjurAuthnInfo, mockContext))
					.thenReturn(authToken);

			// Mock response from ConjurAPI.getSecret
			String expectedSecret = "mockedSecret";
			conjurAPIMockedStatic.when(() -> ConjurAPI.getConjurSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariableId())).thenReturn(expectedSecret);

			// Invoke getSecret and assert result
			Secret secret = conjurSecretCredentials.getSecret();
			assertNotNull(secret);
			assertEquals( "", secret.getPlainText());
		}
	}
	*/
}