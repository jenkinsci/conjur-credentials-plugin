package org.conjur.jenkins.credentials;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    public void getCredentialsTest() {
        ConjurCredentialProvider provider = new ConjurCredentialProvider();
        ModelObject mockContext = mock(ModelObject.class);

        List<StandardCredentials> result = provider.getCredentials(StandardCredentials.class, mockContext);

        assertNotNull(result);
        assertTrue(result.isEmpty());
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

    // -------------------------------------------------------------------------
    // getCredentialsFromSupplier ancestor-climb behaviour (dynamic credential listing)
    //
    // This is the mechanism that makes the folder-credentials *picker* itself respect
    // inheritFromParent: it walks ancestor folders one at a time, adds each one's own
    // Conjur-backed credentials, and the moment it hits a folder with inheritFromParent=false
    // it adds that folder's own credentials then stops -- never visiting anything further up --
    // while always adding the global ones at the end regardless.
    //
    // A genuine multi-level version of this (an inheriting child climbing past itself to a
    // non-inheriting grandparent) is deliberately not expressed here as a Mockito test, for the
    // same reason documented in ConjurAPITest's getConjurConfig tests: mocking
    // AbstractFolder#getParent() to return another AbstractFolder mock is unreliable in this
    // codebase's Mockito/JDK combination. This test instead covers the single-level case -- the
    // starting folder itself is already non-inheriting, so the loop breaks in its first iteration
    // before ever evaluating getParent() -- which is both genuinely new coverage (this method had
    // none before) and immune to that limitation. The multi-level case is verified end-to-end
    // against a real Jenkins + Conjur instance.
    // -------------------------------------------------------------------------

    @Test
    public void testGetCredentialsFromSupplier_stopsImmediately_whenStartingFolderNonInheriting() {
        ConjurCredentialProvider realProvider = new ConjurCredentialProvider();
        AbstractFolder<?> folderMock = mock(AbstractFolder.class);

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class);
             MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {

            Jenkins mockJenkins = mock(Jenkins.class);
            jenkinsStatic.when(Jenkins::get).thenReturn(mockJenkins);
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(folderMock)).thenReturn(false);

            List<StandardCredentials> result = realProvider.getCredentials(
                    StandardCredentials.class, (ItemGroup) folderMock, ACL.SYSTEM, Collections.<DomainRequirement>emptyList());

            assertNotNull(result);

            // The folder's own inheritance flag was consulted exactly once -- and the loop never
            // advanced past it to ask for a parent, because it broke out of the loop body before
            // the for-loop's increment (g = g.getParent()) could run.
            conjurApiStatic.verify(() -> ConjurAPI.isInheritanceOn(folderMock), times(1));
            verify(folderMock, never()).getParent();

            // The unconditional "always add global credentials" step after the loop still ran.
            jenkinsStatic.verify(Jenkins::get, atLeastOnce());
        }
    }

    @Test
    public void testGetCredentialsFromSupplier_climbsPastFolder_whenInheritanceStaysOn() {
        ConjurCredentialProvider realProvider = new ConjurCredentialProvider();
        AbstractFolder<?> folderMock = mock(AbstractFolder.class);

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class);
             MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {

            Jenkins mockJenkins = mock(Jenkins.class);
            jenkinsStatic.when(Jenkins::get).thenReturn(mockJenkins);
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(folderMock)).thenReturn(true);
            // Top-level folder: its parent is Jenkins itself, so the loop's own
            // "g instanceof AbstractFolder" guard ends the climb naturally (not a break).
            doReturn(mockJenkins).when(folderMock).getParent();

            List<StandardCredentials> result = realProvider.getCredentials(
                    StandardCredentials.class, (ItemGroup) folderMock, ACL.SYSTEM, Collections.<DomainRequirement>emptyList());

            assertNotNull(result);
            conjurApiStatic.verify(() -> ConjurAPI.isInheritanceOn(folderMock), times(1));
            verify(folderMock, times(1)).getParent();
            jenkinsStatic.verify(Jenkins::get, atLeastOnce());
        }
    }

    // -------------------------------------------------------------------------
    // The Item (job/pipeline) branch is asymmetric with the folder branch above: when the
    // *starting* context is an Item whose own inheritance is off, the method returns
    // immediately with only that item's own credentials -- it skips not just the ancestor
    // climb but the unconditional "always add global credentials" step too. A folder with
    // inheritFromParent=false still gets global; a job with it off gets neither ancestors nor
    // global. Confirmed live against a real Jenkins + Conjur instance
    // (15_comprehensive_breakpoint_test.groovy, scenario 5) before writing this.
    // -------------------------------------------------------------------------

    @Test
    public void testGetCredentialsFromSupplier_jobWithInheritanceOff_skipsGlobalEntirely() {
        ConjurCredentialProvider realProvider = new ConjurCredentialProvider();
        Item jobMock = mock(Item.class);
        ItemGroup<?> parentMock = mock(ItemGroup.class);

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class);
             MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {

            doReturn(parentMock).when(jobMock).getParent();
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(jobMock)).thenReturn(false);

            List<StandardCredentials> result = realProvider.getCredentials(
                    StandardCredentials.class, jobMock, ACL.SYSTEM, Collections.<DomainRequirement>emptyList());

            assertNotNull(result);
            conjurApiStatic.verify(() -> ConjurAPI.isInheritanceOn(jobMock), times(1));

            // Unlike the folder case: no ancestor climb, and no unconditional global add either --
            // the method returns straight after the job's own credentials.
            jenkinsStatic.verify(Jenkins::get, never());
        }
    }

    @Test
    public void testGetCredentialsFromSupplier_jobWithInheritanceOn_stillAddsGlobal() {
        ConjurCredentialProvider realProvider = new ConjurCredentialProvider();
        Item jobMock = mock(Item.class);
        ItemGroup<?> parentMock = mock(ItemGroup.class); // not an AbstractFolder -- climb loop is a no-op

        try (MockedStatic<ConjurAPI> conjurApiStatic = mockStatic(ConjurAPI.class);
             MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {

            Jenkins mockJenkins = mock(Jenkins.class);
            jenkinsStatic.when(Jenkins::get).thenReturn(mockJenkins);
            doReturn(parentMock).when(jobMock).getParent();
            conjurApiStatic.when(() -> ConjurAPI.isInheritanceOn(jobMock)).thenReturn(true);

            List<StandardCredentials> result = realProvider.getCredentials(
                    StandardCredentials.class, jobMock, ACL.SYSTEM, Collections.<DomainRequirement>emptyList());

            assertNotNull(result);
            conjurApiStatic.verify(() -> ConjurAPI.isInheritanceOn(jobMock), times(1));

            // With inheritance left on, the method falls through to the unconditional global add,
            // same as the folder case.
            jenkinsStatic.verify(Jenkins::get, atLeastOnce());
        }
    }
}