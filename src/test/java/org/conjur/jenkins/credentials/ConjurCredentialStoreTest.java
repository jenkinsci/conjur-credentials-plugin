
package org.conjur.jenkins.credentials;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentialsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConjurCredentialStoreTest {

    @Mock
    private ConjurCredentialProvider provider;

    @Mock
    private ModelObject context;

    @Mock
    private CredentialsStore store;

    @Mock
    private Jenkins jenkinsMock;

    @Mock
    private Domain domainMock;

    @Mock
    private Credentials credentialsMock;

    @Mock
    private Authentication authMock;

    @Mock
    private Item mockItem;

    @Mock
    private ACL aclMock;

    @Mock
    private StaplerRequest mockRequest;

    private static final Logger LOGGER = Logger.getLogger(ConjurCredentialStore.class.getName());
    private TestLogHandler handler;


    @BeforeEach
    void beforeEach() {
        store = mock(ConjurCredentialStore.class);
        authMock = mock(Authentication.class);
        aclMock = mock(ACL.class);
        handler = new TestLogHandler();
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.FINEST);
    }

    @AfterEach
    void afterEach() {
        LOGGER.removeHandler(handler);
    }

    @Test
    void mockAddCredential() {
        mockStatic(ConjurCredentialStore.class);
        ConjurCredentialStore conjurCredentialStore = mock(ConjurCredentialStore.class);
        ConjurSecretCredentialsImpl conjurSecretCredentialsImplAdd = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");
        when(conjurCredentialStore.addCredentials(Domain.global(), conjurSecretCredentialsImplAdd))
                .thenReturn(true);

        assertTrue(conjurCredentialStore.addCredentials(Domain.global(), conjurSecretCredentialsImplAdd));
    }

    @Test
    void getContext() {
        ConjurCredentialStore conjurCreStore = new ConjurCredentialStore(provider, context);
        ModelObject actualContext = conjurCreStore.getContext();

        assertEquals(context, actualContext);
        actualContext = null;
        assertNull(actualContext);
    }


    @Test
    void mockRemoveCredential() {
        mock(ConjurCredentialStore.class);
        ConjurCredentialStore conjurCredentialStore = mock(ConjurCredentialStore.class);
        ConjurSecretCredentialsImpl conjurSecretCredentialsImplRemove = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");
        conjurCredentialStore.removeCredentials(Domain.global(), conjurSecretCredentialsImplRemove);

        verify(conjurCredentialStore).removeCredentials(Domain.global(), conjurSecretCredentialsImplRemove);
    }

    @Test
    void mockUpdateCredential() {
        mock(ConjurCredentialStore.class);
        ConjurCredentialStore conjurCredentialStoreUpdate = mock(ConjurCredentialStore.class);
        ConjurSecretCredentialsImpl oldCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "DB_SECRET", "db/db_password", "Conjur Secret");
        ConjurSecretCredentialsImpl newCredentials = new ConjurSecretCredentialsImpl(
                CredentialsScope.GLOBAL, "DB_SECRET1", "db/db_password", "Conjur Secret");
        conjurCredentialStoreUpdate.updateCredentials(Domain.global(), oldCredentials,
                newCredentials);

        verify(conjurCredentialStoreUpdate).updateCredentials(Domain.global(), oldCredentials, newCredentials);
    }

    @Test
    void testIsVisibleStoreActionNull() {
        CredentialsStoreAction action = new CredentialsStoreAction() {
            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public CredentialsStore getStore() {
                // TODO Auto-generated method stub
                return null;
            }
        };
        CredentialsStore storeMock = mock(CredentialsStore.class);
        storeMock = null;

        assertNull(storeMock);
        assertTrue(action.isVisible());
        assertNotNull(action.getIconFileName());
    }

    @Test
    void testIsVisibleTrueStoreActionNotNull() {
        CredentialsStoreAction action = new CredentialsStoreAction() {
            private CredentialsStore store;

            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public CredentialsStore getStore() {
                // TODO Auto-generated method stub
                return store;
            }
        };

        CredentialsStore storeMock = mock(CredentialsStore.class);

        assertNull(storeMock.getStoreAction());
        assertNotNull(action.getIconFileName());
        assertTrue(action.isVisible());
    }

    @Test
    void testIsVisibleStoreActionNotNull() {
        CredentialsStoreAction action = new CredentialsStoreAction() {
            @Override
            public boolean isVisible() {
                return false;
            }

            @Override
            public CredentialsStore getStore() {
                // TODO Auto-generated method stub
                return null;
            }
        };

        CredentialsStore storeMock = mock(CredentialsStore.class);

        assertNull(storeMock.getStoreAction());
        assertNull(action.getIconFileName());
        assertFalse(action.isVisible());
    }

    @Test
    void testIsVisibleNoViewPermission() {
        CredentialsStoreAction action = new CredentialsStoreAction() {
            private CredentialsStore store;

            @Override
            public boolean isVisible() {
                return false;
            }

            @Override
            public CredentialsStore getStore() {
                // TODO Auto-generated method stub
                return store;
            }
        };

        CredentialsStore store = mock(CredentialsStore.class);

        assertFalse(store.hasPermission(CredentialsProvider.VIEW));
        assertFalse(action.isVisible());
    }


    @Test
    void testGetStoreAction() {
        ConjurCredentialStore conjurCreStore = new ConjurCredentialStore(provider, context);
        CredentialsStoreAction result = conjurCreStore.getStoreAction();

        assertNotNull(result);
    }

    @Test
    void testGetDisplayName() {
        store = new ConjurCredentialStore(provider, context);
        String result = store.getDisplayName();

        assertEquals("Conjur Credential Storage", result);
    }

    @Test
    void testAddCredentialThrowsException() {
        store = new ConjurCredentialStore(provider, context);
        assertThrows(UnsupportedOperationException.class, () -> store.addCredentials(domainMock, credentialsMock));
    }

    @Test
    void testRemoveCredentialThrowsException() {
        store = new ConjurCredentialStore(provider, context);
        assertThrows(UnsupportedOperationException.class, () -> store.removeCredentials(domainMock, credentialsMock));
    }

    @Test
    void testUpdateCredentialsThrowsException() {
        store = new ConjurCredentialStore(provider, context);
        assertThrows(UnsupportedOperationException.class, () -> store.updateCredentials(domainMock, credentialsMock, mock(Credentials.class)));
    }

    @Test
    void testHasPermission2AdminAccessReturnsTrue() {
        store = new ConjurCredentialStore(provider, context);
        try (MockedStatic<Jenkins> mockStaticJenkins = mockStatic(Jenkins.class)) {

            when(jenkinsMock.getACL()).thenReturn(aclMock);
            when(aclMock.hasPermission2(authMock, Jenkins.ADMINISTER)).thenReturn(true);
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(false);
            when(authMock.getName()).thenReturn("test-auth");
            mockStaticJenkins.when(Jenkins::get).thenReturn(jenkinsMock);

            assertTrue(store.hasPermission2(authMock, CredentialsProvider.VIEW));
        }
    }


    @Test
    void testHasPermission2NonViewAccessReturnsTrue() {
        store = new ConjurCredentialStore(provider, context);
        try (MockedStatic<Jenkins> mockStaticJenkins = mockStatic(Jenkins.class)) {
            when(jenkinsMock.getACL()).thenReturn(aclMock);
            when(aclMock.hasPermission2(authMock, Jenkins.ADMINISTER)).thenReturn(true);
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(false);
            when(authMock.getName()).thenReturn("test-auth");
            mockStaticJenkins.when(Jenkins::get).thenReturn(jenkinsMock);

            assertFalse(store.hasPermission2(authMock, Jenkins.ADMINISTER));
        }
    }

    @Test
    void testHasPermission2NonAdminWithAccessReturnsTrue() {
        store = new ConjurCredentialStore(provider, context);
        try (MockedStatic<Jenkins> mockStaticJenkins = mockStatic(Jenkins.class);
             MockedStatic<Stapler> mockStaticStapler = mockStatic(Stapler.class)) {
            when(jenkinsMock.getACL()).thenReturn(aclMock);
            when(aclMock.hasPermission2(authMock, Jenkins.ADMINISTER)).thenReturn(false);
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(false);
            when(authMock.getName()).thenReturn("test-auth");
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(true);
            mockStaticJenkins.when(Jenkins::get).thenReturn(jenkinsMock);
            mockStaticStapler.when(Stapler::getCurrentRequest).thenReturn(mockRequest);

            assertTrue(store.hasPermission2(authMock, CredentialsProvider.VIEW));
        }
    }

    @Test
    void testHasPermission2NonAdminWithoutAccessReturnsTrue() {
        store = new ConjurCredentialStore(provider, context);
        try (MockedStatic<Jenkins> mockStaticJenkins = mockStatic(Jenkins.class);
             MockedStatic<Stapler> mockStaticStapler = mockStatic(Stapler.class)) {
            when(jenkinsMock.getACL()).thenReturn(aclMock);
            when(aclMock.hasPermission2(authMock, Jenkins.ADMINISTER)).thenReturn(false);
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(false);
            when(authMock.getName()).thenReturn("test-auth");
            when(mockRequest.findAncestorObject(Item.class)).thenReturn(mockItem);
            when(mockItem.getFullName()).thenReturn("pipelineJob");
            when(mockItem.getACL()).thenReturn(aclMock);
            when(aclMock.hasPermission2(authMock, CredentialsProvider.VIEW)).thenReturn(false);
            mockStaticJenkins.when(Jenkins::get).thenReturn(jenkinsMock);
            mockStaticStapler.when(Stapler::getCurrentRequest).thenReturn(mockRequest);

            assertFalse(store.hasPermission2(authMock, CredentialsProvider.VIEW));
        }
    }

    @Test
    void testGetCredentialsUserLacksCredentialViewPermission() {
        try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class)) {
            jenkinsStaticMock.when(Jenkins::getAuthentication2).thenReturn(authMock);
            when(authMock.getName()).thenReturn("user1");
            ConjurCredentialStore store = spy(new ConjurCredentialStore(provider, context));
            doReturn(false).when(store).hasPermission2(authMock, CredentialsProvider.VIEW);
            List<Credentials> result = store.getCredentials(domainMock);

            assertTrue(result.isEmpty());
            assertTrue(handler.getLog().contains("User: user1 does not have permission to view credentials."));
        }
    }

    @Test
    void testGetCredentialsReturnsGlobalCredentials() {
        try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class);
             MockedStatic<Stapler> mockStaticStapler = mockStatic(Stapler.class)) {
            jenkinsStaticMock.when(Jenkins::getAuthentication2).thenReturn(authMock);
            ConjurCredentialStore store = spy(new ConjurCredentialStore(provider, context));
            doReturn(true).when(store).hasPermission2(authMock, CredentialsProvider.VIEW);
            mockStaticStapler.when(Stapler::getCurrentRequest).thenReturn(mockRequest);
            when(mockRequest.findAncestorObject(Item.class)).thenReturn(null);
            when(provider.getCredentials(Credentials.class, Jenkins.get())).thenReturn(Collections.emptyList());
            List<Credentials> result = store.getCredentials(domainMock);

            assertTrue(result.isEmpty());
            assertTrue(handler.getLog().contains("ConjurCredentialStore: Global credentials found!"));
        }
    }


    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testGetCredentials() {
        AbstractFolder<?> folderContext = mock(AbstractFolder.class);
        AbstractFolder<?> parentFolder = mock(AbstractFolder.class);
        Item mockItem = mock(Item.class);
        List<Credentials> expectedCredentials = List.of(mock(Credentials.class));

        try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class);
             MockedStatic<Stapler> mockStaticStapler = mockStatic(Stapler.class);
             MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class)) {
            jenkinsStaticMock.when(Jenkins::getAuthentication2).thenReturn(authMock);
            mockStaticStapler.when(Stapler::getCurrentRequest).thenReturn(mockRequest);
            when(mockRequest.findAncestorObject(Item.class)).thenReturn(mockItem);
            when(mockItem.getFullName()).thenReturn("folder1/folder2/job");
            when(folderContext.getFullName()).thenReturn("folder1");
            ConjurCredentialStore store = spy(new ConjurCredentialStore(provider, folderContext));
            doReturn(true).when(store).hasPermission2(authMock, CredentialsProvider.VIEW);
            doReturn(folderContext).when(store).getContext();

            // Parent relationship for loop
            when(mockItem.getParent()).thenReturn((ItemGroup) parentFolder);
            when(parentFolder.getFullName()).thenReturn("folder1"); // must match storePath
            when(parentFolder.getFullDisplayName()).thenReturn("parent display");
            when(parentFolder.getFullName()).thenReturn("folder1");

            // Inheritance
            conjurApiMock.when(() -> ConjurAPI.isInheritanceOn(mockItem)).thenReturn(true);
            conjurApiMock.when(() -> ConjurAPI.isInheritanceOn(parentFolder)).thenReturn(true);

            // Credentials from folderContext (storePath)
            when(provider.getCredentials(Credentials.class, folderContext)).thenReturn(expectedCredentials);

            // Execute
            List<Credentials> result = store.getCredentials(domainMock);

            assertEquals(expectedCredentials, result);
            String logs = handler.getLog();
            assertTrue(logs.contains("ConjurCredentialStore getCredentials, context:"));
            assertTrue(logs.contains("storepath folder1 actfolder folder1"));
        }
    }

    @Test
    void testGetCredentialsInheritanceOffReturnsEmptyListWithLog() {
        AbstractFolder<?> folderContext = mock(AbstractFolder.class);
        Item mockItem = mock(Item.class);
        try (MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class);
             MockedStatic<Stapler> staplerMock = mockStatic(Stapler.class);
             MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class)) {
            jenkinsMock.when(Jenkins::getAuthentication2).thenReturn(authMock);
            staplerMock.when(Stapler::getCurrentRequest).thenReturn(mockRequest);
            when(mockRequest.findAncestorObject(Item.class)).thenReturn(mockItem);
            conjurApiMock.when(() -> ConjurAPI.isInheritanceOn(mockItem)).thenReturn(false);
            ConjurCredentialStore store = spy(new ConjurCredentialStore(provider, folderContext));
            doReturn(true).when(store).hasPermission2(authMock, CredentialsProvider.VIEW);
            doReturn(folderContext).when(store).getContext();
            List<Credentials> result = store.getCredentials(domainMock);

            assertTrue(result.isEmpty());
        }

    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testGetCredentialsInheritanceOfParentReturnsEmptyListWithLog() {
        AbstractFolder<?> folderContext = mock(AbstractFolder.class);
        AbstractFolder<?> parentFolder = mock(AbstractFolder.class);
        Item mockItem = mock(Item.class);
        try (MockedStatic<Jenkins> jenkinsStaticMock = mockStatic(Jenkins.class);
             MockedStatic<Stapler> mockStaticStapler = mockStatic(Stapler.class);
             MockedStatic<ConjurAPI> conjurApiMock = mockStatic(ConjurAPI.class)) {
            // Setup Jenkins and Auth
            jenkinsStaticMock.when(Jenkins::getAuthentication2).thenReturn(authMock);
            // Stapler Request setup
            mockStaticStapler.when(Stapler::getCurrentRequest).thenReturn(mockRequest);
            when(mockRequest.findAncestorObject(Item.class)).thenReturn(mockItem);
            when(mockItem.getFullName()).thenReturn("folder1/folder2/job");
            // Folder Context Setup
            when(folderContext.getFullName()).thenReturn("folder1");
            ConjurCredentialStore store = spy(new ConjurCredentialStore(provider, folderContext));
            doReturn(true).when(store).hasPermission2(authMock, CredentialsProvider.VIEW);
            doReturn(folderContext).when(store).getContext();

            // Parent relationship for loop
            when(mockItem.getParent()).thenReturn((ItemGroup) parentFolder);
            when(parentFolder.getFullName()).thenReturn("folder1"); // must match storePath
            when(parentFolder.getFullDisplayName()).thenReturn("parent display");
            when(parentFolder.getFullName()).thenReturn("folder1");

            // Inheritance
            conjurApiMock.when(() -> ConjurAPI.isInheritanceOn(mockItem)).thenReturn(true);
            conjurApiMock.when(() -> ConjurAPI.isInheritanceOn(parentFolder)).thenReturn(false);

            List<Credentials> result = store.getCredentials(domainMock);

            assertTrue(result.isEmpty());
            String logs = handler.getLog();
            assertTrue(logs.contains("Cannot deliver credentials from path to which you don't have access or inhertiance is: off"));
        }
    }


    @Test
    void testConjurCredentialStoreAction() {
        ConjurCredentialStore mockStore = mock(ConjurCredentialStore.class);
        ModelObject mockContext = mock(ModelObject.class);
        ConjurCredentialStore.ConjurCredentialStoreAction action = new ConjurCredentialStore.ConjurCredentialStoreAction(mockStore, mockContext);

        assertEquals("Conjur Credential Store", action.getDisplayName());
        assertEquals(mockStore, action.getStore());
    }

    ConjurCredentialStore.ConjurCredentialStoreAction createTestAction(
            ConjurCredentialStore store, ModelObject context) {
        return new ConjurCredentialStore.ConjurCredentialStoreAction(store, context);
    }

    private ConjurCredentialStore.ConjurCredentialStoreAction createActionWithVisibility(
            ConjurCredentialStore store, ModelObject context, boolean visible) {

        ConjurCredentialStore.ConjurCredentialStoreAction action =
                createTestAction(store, context);

        ConjurCredentialStore.ConjurCredentialStoreAction spyAction = spy(action);
        doReturn(visible).when(spyAction).isVisible();

        return spyAction;
    }

    @Test
    void testGetIconFileNameWhenVisible() {
        ConjurCredentialStore store = mock(ConjurCredentialStore.class);
        ModelObject context = mock(ModelObject.class);
        ConjurCredentialStore.ConjurCredentialStoreAction action =
                createActionWithVisibility(store, context, true);

        assertEquals("/plugin/conjur-credentials/images/conjur-credential-store-lg.png",
                action.getIconFileName());
    }

    @Test
    void testGetIconFileNameWhenNotVisible() {
        ConjurCredentialStore store = mock(ConjurCredentialStore.class);
        ModelObject context = mock(ModelObject.class);
        ConjurCredentialStore.ConjurCredentialStoreAction action =
                createActionWithVisibility(store, context, false);

        assertNull(action.getIconFileName());
    }

    @Test
    void testGetIconClassNameWhenVisible() {
        ConjurCredentialStore store = mock(ConjurCredentialStore.class);
        ModelObject context = mock(ModelObject.class);
        ConjurCredentialStore.ConjurCredentialStoreAction action =
                createActionWithVisibility(store, context, true);

        assertEquals("icon-conjur-credentials-store", action.getIconClassName());
    }

    @Test
    void testGetIconClassNameWhenNotVisible() {
        ConjurCredentialStore store = mock(ConjurCredentialStore.class);
        ModelObject context = mock(ModelObject.class);
        ConjurCredentialStore.ConjurCredentialStoreAction action =
                createActionWithVisibility(store, context, false);

        assertNull(action.getIconClassName());
    }


    static class TestLogHandler extends Handler {
        private final StringBuilder logMessages = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().equals(Level.FINEST)) {
                logMessages.append(record.getMessage()).append("\n");
            }
        }

        @Override
        public void flush() {
            // No-op: not needed for test handler
        }

        @Override
        public void close() throws SecurityException {
            // No-op: not needed for test handler
        }

        public String getLog() {
            return logMessages.toString();
        }
    }

}