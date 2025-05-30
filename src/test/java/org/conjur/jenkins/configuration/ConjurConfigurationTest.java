package org.conjur.jenkins.configuration;

import java.io.IOException;

import hudson.model.Descriptor;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentialsImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

@RunWith(MockitoJUnitRunner.class)
public class ConjurConfigurationTest {

	@Rule
	public JenkinsRule j = new JenkinsRule();

	@Mock
	private GlobalConjurConfiguration globalConfig;

	@Mock
	private ConjurConfiguration conjurConfiguration;

	@Before
	public void setupConjur() throws Descriptor.FormException {
		globalConfig = mock(GlobalConjurConfiguration.class);
		CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();
		// Setup Conjur login credentials
		UsernamePasswordCredentialsImpl conjurCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
				"conjur-login", "Login Credential to Conjur", "host/frontend/frontend-01",
				"1vpn19h1j621711qm1c9mphkkqw2y35v283h1bccxb028w06t94st");
		try {
			store.addCredentials(Domain.global(), conjurCredentials);
		}catch(UnsupportedOperationException uoe )
		{
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public void setGlobalConfiguration() {
		//GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
		ConjurConfiguration conjurConfiguration = new ConjurConfiguration("https://conjur-master.local:8443", "demo");
		conjurConfiguration.setCredentialID("conjur-login");
		conjurConfiguration.setCertificateCredentialID("Conjur-Master-Certificate");
		globalConfig.setConjurConfiguration(conjurConfiguration);
		globalConfig.save();
	}


	@Test
	public void addConjurCredential() {
		setGlobalConfiguration();
		CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();
		ConjurSecretCredentialsImpl cred = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "DB_SECRET",
				"db/db_password", "Conjur Secret");

		try {
			store.addCredentials(Domain.global(), cred);
			System.out.println("Conjur Credential Added");
		}catch(UnsupportedOperationException uoe )
		{
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
