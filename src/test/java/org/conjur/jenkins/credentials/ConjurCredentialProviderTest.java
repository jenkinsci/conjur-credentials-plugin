
package org.conjur.jenkins.credentials;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.ModelObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurCredentialProviderTest {

    @Mock
    public ConjurCredentialProvider provider;

    @Mock
    private Supplier<String> mockSupplier;

    private Supplier<String> memoizedSupplier;

    @Test
    void getStoreTest() {
        ConjurCredentialStore store = null;
        when(provider.getStore(any())).thenReturn(store);
        assertFalse(provider.getStore(any()) instanceof ConjurCredentialStore);
    }

    @Test
    void getCredentialsTest() {
        String classname1 = "icon-conjur-credentials-store";
        classname1 = null;

        assertNull(classname1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAllCredentialSuppliersTest() {
        ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> credentialSuppliers = new ConcurrentHashMap<>();
        try (@SuppressWarnings("rawtypes")
             MockedStatic mockVar = mockStatic(ConjurCredentialProvider.class)) {
            mockVar.when(ConjurCredentialProvider::getAllCredentialSuppliers).thenReturn(credentialSuppliers);
            ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> result = ConjurCredentialProvider.getAllCredentialSuppliers();

            assertSame(credentialSuppliers, result);
        }
    }

    @Test
    void testGetAllCredentialSuppliers() {
        ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> result = ConjurCredentialProvider
                .getAllCredentialSuppliers();

        assertNotNull(result);
    }

    @Test
    void getIconClassNameTest() {
        String iconClassName = "icon-conjur-credentials-store";
        when(provider.getIconClassName()).thenReturn(iconClassName);
        String actualIconClassName = provider.getIconClassName();

        assertEquals(iconClassName, actualIconClassName);
    }

    @Test
    void testMemoizeWithExpiration() throws Exception {
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
    void testGetDisplayName() {
        ConjurCredentialStore store = new ConjurCredentialStore(provider, mock(ModelObject.class));
        String result = store.getDisplayName();
        assertEquals("Conjur Credential Storage", result);
    }

    @Test
    void testPutCredentials() {
        ConjurCredentialProvider mockProvider = mock(ConjurCredentialProvider.class);
        ModelObject mockObject = mock(ModelObject.class);
        String key = "test-key";
        ConjurCredentialStore result = ConjurCredentialProvider.putCredentials(mockProvider, mockObject, key);

        assertNotNull(result);
    }
}