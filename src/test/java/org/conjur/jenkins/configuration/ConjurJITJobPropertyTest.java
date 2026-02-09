package org.conjur.jenkins.configuration;

import hudson.model.Item;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurJITJobPropertyTest {

    @Mock
    private ConjurConfiguration conjurConfiguration;

    @Mock
    private ConjurJITJobProperty<Job<?, ?>> conjurJITJobProperty;

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testSetters() {
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
    void testGetInheritFromParent() {
        ConjurConfiguration newConjurConfiguration = spy(new ConjurConfiguration());
        doReturn(null).when(newConjurConfiguration).getInheritFromParent();
        ConjurJITJobProperty jobProperty = new ConjurJITJobProperty(newConjurConfiguration);

        assertTrue(jobProperty.getInheritFromParent());
    }

    @Test
    void testConjurConfigurationGetterAndSetter() {
        ConjurConfiguration newConjurConfiguration = new ConjurConfiguration();
        conjurJITJobProperty.setConjurConfiguration(newConjurConfiguration);

        assertNull(conjurJITJobProperty.getConjurConfiguration());
    }

    @Test
    void testJobPropertyDescriptorDisplayName() {
        ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl descriptor = new ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl();

        assertEquals("Conjur Just-In-Time Access", descriptor.getDisplayName());
    }

    @Test
    void testJobPropertyDescriptorApplicable() {
        ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl descriptor = new ConjurJITJobProperty.ConjurJITJobPropertyDescriptorImpl();

        assertTrue(descriptor.isApplicable(Job.class));
    }

    @Test
    void testGetItem() throws Exception {
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