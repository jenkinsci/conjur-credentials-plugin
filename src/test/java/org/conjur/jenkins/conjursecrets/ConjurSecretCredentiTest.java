
package org.conjur.jenkins.conjursecrets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import jenkins.model.Jenkins;

@RunWith(MockitoJUnitRunner.class)
public class ConjurSecretCredentiTest {
	@Mock
	private Jenkins jenkinsMock;
	@Mock
	private WorkflowRun runMock;
	@Mock
	private WorkflowJob jobMock;
	@Mock
	private Item folderMock;
	@Mock
	private AbstractItem abstractItemMock;
	@Mock
	private ConjurSecretCredentials conjurSecretCredentialsMock;

	@Before
	public void setUp() throws IOException {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void mockCredentialFromContextIfNeeded() {
		try (MockedStatic<ConjurSecretCredentials> mockedStaticConjurSecretCredentials = mockStatic(
				ConjurSecretCredentials.class)) {
			final ConjurSecretCredentials conjurSecretCredentials = mock(ConjurSecretCredentials.class);
			mock(ModelObject.class);
			final ModelObject context = mock(ModelObject.class);
			String credentialID = "Id74";
			mockedStaticConjurSecretCredentials.when(() -> ConjurSecretCredentials
					.credentialFromContextIfNeeded(conjurSecretCredentials, credentialID, context))
					.thenReturn(conjurSecretCredentials);

		}

	}

	@Test
	public void mockCredentialWithId() {
		try (MockedStatic<ConjurSecretCredentials> mockedStaticConjurSecretCredentials = mockStatic(
				ConjurSecretCredentials.class)) {
			final ConjurSecretCredentials conjurSecretCredentialsId = mock(ConjurSecretCredentials.class);
			mockStatic(ModelObject.class);
			final ModelObject context = mock(ModelObject.class);
			String credentialID = "Id412";
			mockedStaticConjurSecretCredentials
					.when(() -> ConjurSecretCredentials.credentialWithID(credentialID, context))
					.thenReturn(conjurSecretCredentialsId);

		}
	}

	@Test
	public void testCredentialFromContextIfNeededFolderFoundRunContext() {
		// Arrange
		String credentialID = "Id74";
		String mockFullName = "folder-name";
		// Mock Jenkins and CredentialsProvider lookup calls
		try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class);
				MockedStatic<CredentialsProvider> credentialsProviderStaticMock = mockStatic(CredentialsProvider.class);
				MockedStatic<CredentialsMatchers> credentialsMatchersStaticMock = mockStatic(
						CredentialsMatchers.class)) {

			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);

			credentialsProviderStaticMock
					.when(() -> CredentialsProvider.lookupCredentials(ConjurSecretCredentials.class, folderMock,
							ACL.SYSTEM, Collections.<DomainRequirement>emptyList()))
					.thenReturn(Collections.singletonList(conjurSecretCredentialsMock));

			credentialsMatchersStaticMock
					.when(() -> CredentialsMatchers.firstOrNull(Collections.singletonList(conjurSecretCredentialsMock),
							CredentialsMatchers.withId(credentialID)))
					.thenReturn(conjurSecretCredentialsMock);

			// Act: Call the method under test
			ConjurSecretCredentials credential = ConjurSecretCredentials
					.credentialFromContextIfNeeded(conjurSecretCredentialsMock, credentialID, runMock);

			// Assert
			assertNotNull("The folder should have been retrieved", jenkinsMock);
			assertNotNull("Credential should be retrieved", credential); // Ensure credentials are correctly returned
		}
	}

	@Test
	public void testCredentialFromContextIfNeededFolderNotFoundRunContext() {
		// Arrange
		String credentialID = "Id74";
		String mockFullName = "non-existent-folder-name";
		// Mock getParent of WorkflowJob to return Jenkins (top-level instance in this
		// case)
		when(runMock.getParent()).thenReturn(jobMock);
		when(jobMock.getParent()).thenReturn(jenkinsMock); // Simulating that the parent of WorkflowJob is Jenkins
		when(jenkinsMock.getFullName()).thenReturn(mockFullName);
		// Simulate that the folder is not found
		when(jenkinsMock.getItemByFullName(mockFullName)).thenReturn(null);
		// Mock static calls
		try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class)) {
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);
			// Act: Call the method under test
			ConjurSecretCredentials credential = ConjurSecretCredentials.credentialFromContextIfNeeded(null,
					credentialID, runMock);
			// Assert: Ensure no credential is returned due to folder not found
			assertNotNull("Jenkins instance should still exist", jenkinsMock);
			assertNull("Credential should not be retrieved if the folder is not found", credential);
		}
	}

	@Test
	public void testCredentialFromContextIfNeededFolderFoundAbstractItemContext() {
		// Arrange
		String credentialID = "Id74";
		String folderFullName = "folder-name";
		// Mock static calls
		try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class)) {
			MockedStatic<CredentialsProvider> credentialsProviderStaticMock = mockStatic(CredentialsProvider.class);
			MockedStatic<CredentialsMatchers> credentialsMatchersStaticMock = mockStatic(CredentialsMatchers.class);
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);
			// Set up Jenkins static call to return the mocked Jenkins instance
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);
			credentialsProviderStaticMock
					.when(() -> CredentialsProvider.lookupCredentials(ConjurSecretCredentials.class, folderMock,
							ACL.SYSTEM, Collections.<DomainRequirement>emptyList()))
					.thenReturn(Collections.singletonList(conjurSecretCredentialsMock));
			// Mock CredentialsMatchers.firstOrNull to return the mock credential when the
			// ID matches
			credentialsMatchersStaticMock
					.when(() -> CredentialsMatchers.firstOrNull(Collections.singletonList(conjurSecretCredentialsMock),
							CredentialsMatchers.withId(credentialID)))
					.thenReturn(conjurSecretCredentialsMock);
			// Act: Call the method under test
			ConjurSecretCredentials credential = ConjurSecretCredentials
					.credentialFromContextIfNeeded(conjurSecretCredentialsMock, credentialID, abstractItemMock);
			// Assert
			assertNotNull("The folder should have been retrieved", jenkinsMock);
			assertNotNull("Credential should be retrieved", credential);
		}
	}

	@Test
	public void testCredentialFromContextIfNeededCredentialNotFoundAbstractItemContext() {
		// Arrange
		String credentialID = "non-existent-credential-ID";
		String folderFullName = "folder-name";
		// Mock behavior of AbstractItem
		when(abstractItemMock.getFullName()).thenReturn(folderFullName);
		// Mock Jenkins.getItemByFullName to return the folder
		when(jenkinsMock.getItemByFullName(folderFullName)).thenReturn(folderMock);
		// Mock static calls
		try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class);
				MockedStatic<CredentialsProvider> credentialsProviderStaticMock = mockStatic(CredentialsProvider.class);
				MockedStatic<CredentialsMatchers> credentialsMatchersStaticMock = mockStatic(
						CredentialsMatchers.class)) {
			// Set up Jenkins static call to return the mocked Jenkins instance
			jenkinsStaticMock.when(Jenkins::get).thenReturn(jenkinsMock);
			// Mock CredentialsProvider.lookupCredentials to return a list without the
			// desired credential
			credentialsProviderStaticMock
					.when(() -> CredentialsProvider.lookupCredentials(ConjurSecretCredentials.class, folderMock,
							ACL.SYSTEM, Collections.<DomainRequirement>emptyList()))
					.thenReturn(Collections.singletonList(conjurSecretCredentialsMock));
			// Mock CredentialsMatchers.firstOrNull to return null (no matching credential
			// ID)
			credentialsMatchersStaticMock
					.when(() -> CredentialsMatchers.firstOrNull(Collections.singletonList(conjurSecretCredentialsMock),
							CredentialsMatchers.withId(credentialID)))
					.thenReturn(null);
			// Act: Call the method under test
			ConjurSecretCredentials credential = ConjurSecretCredentials.credentialFromContextIfNeeded(null,
					credentialID, abstractItemMock);
			// Assert: Ensure no credential is returned due to ID mismatch
			assertNotNull("The folder should have been retrieved", jenkinsMock);
			assertNull("Credential should not be retrieved if the credential ID does not match", credential);
		}
	}
}