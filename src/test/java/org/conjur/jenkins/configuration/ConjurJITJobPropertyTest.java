package org.conjur.jenkins.configuration;

import hudson.model.Item;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)

public class ConjurJITJobPropertyTest {

    @Mock
    private ConjurConfiguration conjurConfiguration;

    @Mock
    private ConjurJITJobProperty<Job<?, ?>> conjurJITJobProperty;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testSetters() {
        ConjurJITJobProperty<Job<?, ?>> property = new ConjurJITJobProperty<>(conjurConfiguration);
        property.setConjurConfiguration(null);
        assertNull(property.getConjurConfiguration());

        ConjurJITJobProperty<Job<?, ?>> property1 = new ConjurJITJobProperty<>(conjurConfiguration);
        property1.setInheritFromParent(false);
        assertFalse(property1.getInheritFromParent());

        property1.setAuthWebServiceId("testAuthWebServiceId");
        assertEquals("testAuthWebServiceId", property1.getAuthWebServiceId());

        property1.setHostPrefix("testHostPrefix");
        assertEquals("testHostPrefix", property1.getHostPrefix());
    }

    @Test
    public void testGetInheritFromParent() {
        ConjurConfiguration newConjurConfiguration = spy(new ConjurConfiguration());
        doReturn(null).when(newConjurConfiguration).getInheritFromParent();
        ConjurJITJobProperty jobProperty = new ConjurJITJobProperty(newConjurConfiguration);

        assertTrue(jobProperty.getInheritFromParent());
    }

    @Test
    public void testConjurConfigurationGetterAndSetter() {
        ConjurConfiguration newConjurConfiguration = new ConjurConfiguration();
        conjurJITJobProperty.setConjurConfiguration(newConjurConfiguration);

        assertNull(conjurJITJobProperty.getConjurConfiguration());
    }

    @Test
    public void testJobPropertyDescriptorDisplayName() {
        ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl descriptor = new ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl();

        assertEquals("Conjur Just-In-Time Access", descriptor.getDisplayName());
    }

    @Test
    public void testJobPropertyDescriptorApplicable() {
        ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl descriptor = new ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl();

        assertTrue(descriptor.isApplicable(Job.class));
    }

    @Test
    public void testGetItem() throws NoSuchFieldException, IllegalAccessException {
        ConjurJITJobProperty jobProperty = new ConjurJITJobProperty(conjurConfiguration);
        Jenkins mockJenkins = mock(Jenkins.class);
        Item mockItem = mock(Item.class);
        Job<?, ?> mockOwner = mock(Job.class);
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