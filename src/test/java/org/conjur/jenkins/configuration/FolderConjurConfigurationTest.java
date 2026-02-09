package org.conjur.jenkins.configuration;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.model.Item;
import hudson.util.DescribableList.Owner;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WithJenkins
class FolderConjurConfigurationTest {

    @Mock
    private Jenkins mockJenkins;

    @Mock
    private Item mockItem;

    @SuppressWarnings("deprecation")
    @Mock
    private Owner mockOwner;

    private JenkinsRule j;

    private ConjurConfiguration testConjurConfig;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        testConjurConfig = new ConjurConfiguration("https://example.com", "test-account");
    }

    @Test
    void testConstructorAndGetter() {
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);
        assertEquals(testConjurConfig, folderConfig.getConjurConfiguration());
    }

    @Test
    void testSetConjurConfiguration() {
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);
        ConjurConfiguration newConfig = new ConjurConfiguration("https://new-url.com", "new-account");

        folderConfig.setConjurConfiguration(newConfig);
        assertEquals(newConfig, folderConfig.getConjurConfiguration());
    }

    @Test
    void testGetInheritFromParentWhenNullDefaultsToTrue() {
        testConjurConfig.setInheritFromParent(null);
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);

        assertTrue(folderConfig.getInheritFromParent(), "Expected inheritFromParent to default to true when null");
    }

    @Test
    void testGetInheritFromParentWhenSetToFalse() {
        testConjurConfig.setInheritFromParent(false);
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);

        assertFalse(folderConfig.getInheritFromParent());
    }

    @Test
    void testGetInheritFromParentWhenSetToTrue() {
        testConjurConfig.setInheritFromParent(true);
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);

        assertTrue(folderConfig.getInheritFromParent());
    }

    @Test
    void testSetInheritFromParent() {
        FolderConjurConfiguration folderConfig = new FolderConjurConfiguration(testConjurConfig);
        folderConfig.setInheritFromParent(false);
        assertFalse(testConjurConfig.getInheritFromParent());

        folderConfig.setInheritFromParent(true);
        assertTrue(testConjurConfig.getInheritFromParent());
    }

    @Test
    void testDescriptorImplExists() {
        FolderConjurConfiguration.DescriptorImpl descriptor = new FolderConjurConfiguration.DescriptorImpl();
        assertNotNull(descriptor, "DescriptorImpl should be instantiated without exceptions");
    }

    @Test
    void testGetItem() throws Exception {
        ConjurConfiguration conjurConfiguration = mock(ConjurConfiguration.class);
        FolderConjurConfiguration jobProperty = new FolderConjurConfiguration(conjurConfiguration);
        Jenkins mockJenkins = mock(Jenkins.class);
        Item mockItem = mock(Item.class);

        AbstractFolder mockOwner = mock(AbstractFolder.class);
        when(mockOwner.getFullName()).thenReturn("project/path");

        Field ownerField = jobProperty.getClass().getSuperclass().getDeclaredField("owner");
        ownerField.setAccessible(true);
        ownerField.set(jobProperty, mockOwner);
        try (MockedStatic<Jenkins> jenkinsStatic = mockStatic(Jenkins.class)) {
            jenkinsStatic.when(Jenkins::get).thenReturn(mockJenkins);
            when(mockJenkins.getItemByFullName("project/path")).thenReturn(mockItem);
            Item result = jobProperty.getItem();

            assertEquals(mockItem, result);
        }
    }

}