
package org.conjur.jenkins.credentials;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;

import hudson.model.Item;
import hudson.security.ACL;
import jenkins.model.Jenkins;

@RunWith(MockitoJUnitRunner.class)
public class ConjurCredentialStoreTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Mock
	private Jenkins mockJenkins;

	@Mock
	private Authentication authentication;

	@Mock
	private ACL mockAcl;

	@Mock
	private ACL folderAcl;

	@Mock
	private Jenkins jenkinsMock;

	@Mock
	private Item currentItem;

	@Mock
	private StaplerRequest mockStaplerRequest;

	@InjectMocks
	private ConjurCredentialStore conjurCredentialStoreMock;

	@Mock
	private org.springframework.security.core.Authentication springAuthentication;

	@Test
	public void mockAddCredential() throws IOException {

		try (MockedStatic<ConjurCredentialStore> conjurCredentialStoreMockedStatic = mockStatic(
				ConjurCredentialStore.class)) {
			ConjurCredentialStore conjurCredentialStore = mock(ConjurCredentialStore.class);

			ConjurSecretCredentialsImpl conjurSecretCredentialsImplAdd = new ConjurSecretCredentialsImpl(
					CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");

			conjurCredentialStoreMockedStatic
					.when(() -> conjurCredentialStore.addCredentials(Domain.global(), conjurSecretCredentialsImplAdd))
					.thenReturn(true);

			assertTrue(conjurCredentialStore.addCredentials(Domain.global(), conjurSecretCredentialsImplAdd));

		}

	}

	@Test
	public void mockRemoveCredential() throws IOException {
		mock(ConjurCredentialStore.class);
		ConjurCredentialStore conjurCredentialStore1Remove = mock(ConjurCredentialStore.class);

		ConjurSecretCredentialsImpl conjurSecretCredentialsImplRemove = new ConjurSecretCredentialsImpl(
				CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");
		conjurCredentialStore1Remove.removeCredentials(Domain.global(), conjurSecretCredentialsImplRemove);

	}

	@Test
	public void mockUpdateCredential() throws IOException {
		mock(ConjurCredentialStore.class);
		ConjurCredentialStore conjurCredentialStoreUpdate = mock(ConjurCredentialStore.class);

		ConjurSecretCredentialsImpl conjurSecretCredentialsImplUpdate = new ConjurSecretCredentialsImpl(
				CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");
		ConjurSecretCredentialsImpl conjurSecretCredentialsImplUpdate2 = new ConjurSecretCredentialsImpl(
				CredentialsScope.GLOBAL, "DB_SECRET1", "db/db_password", "Conjur Secret");
		conjurCredentialStoreUpdate.updateCredentials(Domain.global(), conjurSecretCredentialsImplUpdate,
				conjurSecretCredentialsImplUpdate2);

	}

	@Test
	public void testAdminWithCredentialsView() {
		when(authentication.getName()).thenReturn("admin");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// Mocking Jenkins class
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(true);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(true);

		// Mock the static Jenkins.get() method to return the mocked Jenkins instance
		try (MockedStatic<Jenkins> jenkinsStaticMock = Mockito.mockStatic(Jenkins.class)) {
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			// Call the mocked method
			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.VIEW);
			assertTrue(result);
		}
	}

	@Test
	public void testAdminWithoutCredentialsView() {
		when(authentication.getName()).thenReturn("admin");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// Mocking Jenkins class
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(true);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(false);

		// Mock the static Jenkins.get() method to return the mocked Jenkins instance
		try (MockedStatic<Jenkins> jenkinsStaticMock = Mockito.mockStatic(Jenkins.class)) {
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			// Call the mocked method
			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.VIEW);
			assertTrue(result);
		}
	}

	@Test
	public void testNonAdminWithCredentialsView() {
		when(authentication.getName()).thenReturn("user");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// Mocking Jenkins class
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(false);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(true);
		when(mockStaplerRequest.findAncestorObject(Item.class)).thenReturn(currentItem);

		// Mock the static Jenkins.get() method to return the mocked Jenkins instance
		try (MockedStatic<Jenkins> jenkinsStaticMock = Mockito.mockStatic(Jenkins.class)) {
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			// Call the mocked method
			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.VIEW);
			assertTrue(result);
		}
	}

	@Test
	public void testNonAdminWithoutCredentialsView() {
		when(authentication.getName()).thenReturn("user");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// Mocking Jenkins class
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(false);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(false);
		when(mockStaplerRequest.findAncestorObject(Item.class)).thenReturn(null);

		// Mock the static Jenkins.get() method to return the mocked Jenkins instance
		try (MockedStatic<Jenkins> jenkinsStaticMock = Mockito.mockStatic(Jenkins.class)) {
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			// Call the mocked method
			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.CREATE);
			assertFalse(result);
		}
	}

	@Test
	public void testUserWithFolderLevelPermission() {
		when(authentication.getName()).thenReturn("user");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(false);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(false);

		// Mock static Stapler and Jenkins methods
		try (MockedStatic<Stapler> staplerStaticMock = mockStatic(Stapler.class);
				MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class)) {
			// Set up Stapler mock
			staplerStaticMock.when(Stapler::getCurrentRequest).thenReturn(mockStaplerRequest);
			when(mockStaplerRequest.findAncestorObject(Item.class)).thenReturn(currentItem);
			when(currentItem.getFullName()).thenReturn("test-folder");

			// Set up folder-level ACL mock
			when(currentItem.getACL()).thenReturn(folderAcl);
			when(folderAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(true);
			// Set up Jenkins mock
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.VIEW);
			assertTrue(result);
		}
	}

	@Test
	public void testUserWithoutFolderLevelPermission() {
		when(authentication.getName()).thenReturn("user");
		springAuthentication = authentication.toSpring();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		when(jenkinsMock.getACL()).thenReturn(mockAcl);
		when(mockAcl.hasPermission2(springAuthentication, Jenkins.ADMINISTER)).thenReturn(false);
		when(mockAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(false);

		// Mock static Stapler and Jenkins methods
		try (MockedStatic<Stapler> staplerStaticMock = mockStatic(Stapler.class);
				MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class)) {
			// Set up Stapler mock
			staplerStaticMock.when(Stapler::getCurrentRequest).thenReturn(mockStaplerRequest);
			when(mockStaplerRequest.findAncestorObject(Item.class)).thenReturn(currentItem);
			when(currentItem.getFullName()).thenReturn("test-folder");

			// Set up folder-level ACL mock
			when(currentItem.getACL()).thenReturn(folderAcl);
			when(folderAcl.hasPermission2(springAuthentication, CredentialsProvider.VIEW)).thenReturn(false);
			// Set up Jenkins mock
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			boolean result = conjurCredentialStoreMock.hasPermission(authentication, CredentialsProvider.CREATE);
			assertFalse(result);
		}
	}
}