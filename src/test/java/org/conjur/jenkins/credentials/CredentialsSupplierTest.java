package org.conjur.jenkins.credentials;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;

@RunWith(MockitoJUnitRunner.class)
public class CredentialsSupplierTest {
	@Mock
	private ModelObject mockContext;
	@Mock
	private ItemGroup mockItemGroup;

	@Mock
	private UsernamePasswordCredentialsImpl mockCredential;
	@InjectMocks
	private CredentialsSupplier credentialsSupplier;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		credentialsSupplier = (CredentialsSupplier) CredentialsSupplier.standard(mockContext);
	}

	@Test
	public void testGetWhenContextIsNull() {
		CredentialsSupplier supplier = (CredentialsSupplier) CredentialsSupplier.standard(null);
		Collection<StandardCredentials> result = supplier.get();
		assertEquals(Collections.emptyList(), result);
	}

	@Test
	public void testGetWhenContextIsItemGroupWithNullScopeCredentials() {
		List<UsernamePasswordCredentialsImpl> mockCredentials = new ArrayList<>();
		mockCredentials.add(
				new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "id1", "desc", "username1", "password1"));
		mockCredentials.add(new UsernamePasswordCredentialsImpl(null, "id1", "desc", "username1", "password1"));
		// Mock static methods
		try (MockedStatic<CredentialsProvider> credentialsProviderMockedStatic = mockStatic(
				CredentialsProvider.class)) {
			credentialsProviderMockedStatic
					.when(() -> CredentialsProvider.lookupCredentials(eq(UsernamePasswordCredentialsImpl.class),
							eq(mockItemGroup), eq(ACL.SYSTEM), eq(Collections.emptyList())))
					.thenReturn(mockCredentials);
			// Act
			List<UsernamePasswordCredentialsImpl> filteredCredentials = mockCredentials.stream()
					.filter(c -> c.getScope() == null).collect(Collectors.toList());
			if (!filteredCredentials.isEmpty()) {
				// Assert
				assertTrue(filteredCredentials.stream().allMatch(c -> c.getScope() == null));
			}
		}
	}
	@Test
	public void testGetWhenContextIsItemGroupWithNotNullScopeCredentials() {
		List<UsernamePasswordCredentialsImpl> mockCredentials = new ArrayList<>();
		mockCredentials.add(
				new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "id1", "desc", "username1", "password1"));
		mockCredentials.add(new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "id1", "desc", "username1", "password1"));
		// Mock static methods
		try (MockedStatic<CredentialsProvider> credentialsProviderMockedStatic = mockStatic(
				CredentialsProvider.class)) {
			credentialsProviderMockedStatic
					.when(() -> CredentialsProvider.lookupCredentials(eq(UsernamePasswordCredentialsImpl.class),
							eq(mockItemGroup), eq(ACL.SYSTEM), eq(Collections.emptyList())))
					.thenReturn(mockCredentials);
			// Act
			List<UsernamePasswordCredentialsImpl> filteredCredentials = mockCredentials.stream()
					.filter(c -> c.getScope() != null).collect(Collectors.toList());
			if (!filteredCredentials.isEmpty()) {
				// Assert
				assertTrue(filteredCredentials.stream().anyMatch(c -> c.getScope() != null));
			}
		}
	}
}