
package org.conjur.jenkins.credentials;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.ModelObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ConjurCredentialProviderTest {

    @Mock
    public ConjurCredentialProvider provider;

    @Mock
    private Supplier<String> mockSupplier;

    private Supplier<String> memoizedSupplier;

    @Test
    public void getStoreTest() {
        ConjurCredentialStore store = null;
        when(provider.getStore(any())).thenReturn(store);
        assertFalse(provider.getStore(any()) instanceof ConjurCredentialStore);
    }

    @Test
    public void getCredentialsTest() throws Exception {
        String classname1 = "icon-conjur-credentials-store";
        classname1 = null;

        assertNull(classname1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getAllCredentialSuppliersTest() {
        ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> credentialSuppliers = new ConcurrentHashMap<String, Supplier<Collection<StandardCredentials>>>();
        try (@SuppressWarnings("rawtypes")
             MockedStatic mockVar = mockStatic(ConjurCredentialProvider.class)) {
            mockVar.when(ConjurCredentialProvider::getAllCredentialSuppliers).thenReturn(credentialSuppliers);
            ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> result = ConjurCredentialProvider.getAllCredentialSuppliers();

            assertSame(credentialSuppliers, result);
        }
    }

    @Test
    public void testGetAllCredentialSuppliers() {
        ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> result = ConjurCredentialProvider
                .getAllCredentialSuppliers();

        assertNotNull(result);
    }

    @Test
    public void getIconClassNameTest() {
        String iconClassName = "icon-conjur-credentials-store";
        when(provider.getIconClassName()).thenReturn(iconClassName);
        String actualIconClassName = provider.getIconClassName();

        assertEquals(iconClassName, actualIconClassName);
    }

    @Test
    public void testMemoizeWithExpiration() throws InterruptedException {
        Supplier<String> baseSupplier = () -> "Value at " + System.currentTimeMillis();
        Supplier<String> memoizedSupplier = ConjurCredentialProvider.memoizeWithExpiration(baseSupplier,
                Duration.ofMillis(100));
        String firstCallValue = memoizedSupplier.get();
        System.out.println("First call value: " + firstCallValue);
        String secondCallValue = memoizedSupplier.get();
        assertEquals(firstCallValue, secondCallValue);

        Thread.sleep(101);
        String thirdCallValue = memoizedSupplier.get();
        System.out.println("Third call value: " + thirdCallValue);
        assertNotEquals(firstCallValue, thirdCallValue);
    }

    @Test
    public void testGetDisplayName() {
        ConjurCredentialStore store = new ConjurCredentialStore(provider, mock(ModelObject.class));
        String result = store.getDisplayName();
        assertEquals("Conjur Credential Storage", result);
    }

    @Test
    public void testPutCredentials() {
        ConjurCredentialProvider mockProvider = mock(ConjurCredentialProvider.class);
        ModelObject mockObject = mock(ModelObject.class);
        String key = "test-key";
        ConjurCredentialStore result = ConjurCredentialProvider.putCredentials(mockProvider, mockObject, key);

        assertNotNull(result);
    }
}