
package org.conjur.jenkins.conjursecrets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.CredentialsScope;
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
	private ModelObject mockStoreContext;

	@Mock
	private ConjurConfiguration mockConjurConfiguration;

	@InjectMocks
	private ConjurSecretCredentialsImpl conjurSecretCredentials;

	@BeforeEach
	public void init() {
		mockContext = jenkinsRule.jenkins.getInstance();
		mockStoreContext = jenkinsRule.jenkins.getInstance();
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

	@Test
	public void testGetSecretContextNotNullAndStoreContextNotNull() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");
		conjurSecretCredentials.setConjurConfiguration(conjurConfiguration);

		conjurSecretCredentials.setContext(mockStoreContext);
		conjurSecretCredentials.setStoreContext(mockContext);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			conjurAPIMockedStatic
					.when(() -> ConjurAPI.getAuthorizationToken(mockHttpClient, conjurConfiguration, mockContext))
					.thenReturn(authToken);

			// Mock response from ConjurAPI.getSecret
			String expectedSecret = "mockedSecret";
			conjurAPIMockedStatic.when(() -> ConjurAPI.getSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariablePath())).thenReturn(expectedSecret);

			// Invoke getSecret and assert result
			Secret secret = conjurSecretCredentials.getSecret();
			assertNotNull(secret);
			assertEquals(expectedSecret, secret.getPlainText());
		}
	}

	@Test
	public void testGetSecretContextNotNullAndStoreContextNull() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");
		conjurSecretCredentials.setConjurConfiguration(conjurConfiguration);

		conjurSecretCredentials.setContext(mockContext);
		conjurSecretCredentials.setStoreContext(null);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			conjurAPIMockedStatic
					.when(() -> ConjurAPI.getAuthorizationToken(mockHttpClient, conjurConfiguration, mockContext))
					.thenReturn(authToken);

			// Mock response from ConjurAPI.getSecret
			String expectedSecret = "mockedSecret";
			conjurAPIMockedStatic.when(() -> ConjurAPI.getSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariablePath())).thenReturn(expectedSecret);

			// Invoke getSecret and assert result
			Secret secret = conjurSecretCredentials.getSecret();
			assertNotNull(secret);
			assertEquals(expectedSecret, secret.getPlainText());
		}
	}

	@Test
	public void testGetSecretContextNullAndStoreContextNotNull() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");
		conjurSecretCredentials.setConjurConfiguration(conjurConfiguration);

		conjurSecretCredentials.setContext(null);
		conjurSecretCredentials.setStoreContext(mockStoreContext);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			conjurAPIMockedStatic
					.when(() -> ConjurAPI.getAuthorizationToken(mockHttpClient, conjurConfiguration, mockStoreContext))
					.thenReturn(authToken);

			// Mock response from ConjurAPI.getSecret
			String expectedSecret = "mockedSecret";
			conjurAPIMockedStatic.when(() -> ConjurAPI.getSecret(mockHttpClient, conjurConfiguration, authToken,
					conjurSecretCredentials.getVariablePath())).thenReturn(expectedSecret);

			// Invoke getSecret and assert result
			Secret secret = conjurSecretCredentials.getSecret();
			assertNotNull(secret);
			assertEquals(expectedSecret, secret.getPlainText());
		}
	}

	@Test
	public void testGetSecretContextNullAndStoreContextNull() throws Exception {
		String authToken = "mockedAuthToken";
		// Setup Conjur Configuration
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration();
		conjurConfiguration.setAccount("myConjurAccount");
		conjurConfiguration.setApplianceURL("http://localhost:8083");
		conjurConfiguration.setCredentialID("jenkins-app/dbPassword");
		conjurConfiguration.setCertificateCredentialID("certificateId");

		conjurSecretCredentials = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "testPipeline", "DevTeam-1",
				"Test pipeline");
		conjurSecretCredentials.setConjurConfiguration(conjurConfiguration);

		conjurSecretCredentials.setContext(null);
		conjurSecretCredentials.setStoreContext(null);

		// Mock OkHttpClient and static methods
		try (MockedStatic<ConjurAPIUtils> conjurAPIUtilsMockedStatic = Mockito.mockStatic(ConjurAPIUtils.class);
				MockedStatic<ConjurAPI> conjurAPIMockedStatic = Mockito.mockStatic(ConjurAPI.class)) {

			OkHttpClient mockHttpClient = mock(OkHttpClient.class);
			conjurAPIUtilsMockedStatic.when(() -> ConjurAPIUtils.getHttpClient(conjurConfiguration))
					.thenReturn(mockHttpClient);

			conjurAPIMockedStatic.when(() -> ConjurAPI.getAuthorizationToken(mockHttpClient, conjurConfiguration, null))
					.thenReturn(null);
			// Act
			conjurAPIMockedStatic.when(() -> ConjurAPI.getSecret(mockHttpClient, conjurConfiguration, null,
					conjurSecretCredentials.getVariablePath())).thenReturn(null);
			Secret secret = conjurSecretCredentials.getSecret();
			assertEquals("Expected secret to be empty", Secret.fromString("").getPlainText(), secret.getPlainText());
		}
	}

}