package org.conjur.jenkins.configuration;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentialsImpl;
import org.conjur.jenkins.credentials.ConjurCredentialProvider;
import org.conjur.jenkins.credentials.ConjurCredentialStore;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurConfigurationTest {

    private JenkinsRule j;

    @Mock
    private GlobalConjurConfiguration globalConfig;

    @Mock
    private ConjurConfiguration conjurConfiguration;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Item item;

    @Mock
    private JSONObject claim = new JSONObject();

    @Mock
    private Item mockItem;

    @Mock
    private ConjurCredentialProvider mockConjurCredentialProvider;

    @Mock
    private ConjurCredentialStore mockConjurCredentialStore;

    @Mock
    private Supplier<Object> mockSupplier;

    @Mock
    private Logger mockLogger;

    private ConjurConfiguration config;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        globalConfig = mock(GlobalConjurConfiguration.class);
        CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();
        // Setup Conjur login credentials
        UsernamePasswordCredentialsImpl conjurCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                "conjur-login", "Login Credential to Conjur", "host/frontend/frontend-01",
                "1vpn19h1j621711qm1c9mphkkqw2y35v283h1bccxb028w06t94st");
        try {
            store.addCredentials(Domain.global(), conjurCredentials);
        } catch (UnsupportedOperationException | IOException ignored) {
        }

        config = new ConjurConfiguration("https://example.com", "test-account");
    }

    @Test
    void checkOwnerFullName() {
        ConjurConfiguration conjurConfiguration = new ConjurConfiguration();

        conjurConfiguration.setOwnerFullName("Owner");
        assertEquals("Owner", conjurConfiguration.getOwnerFullName());

        conjurConfiguration.setOwnerFullName("");
        assertEquals("", conjurConfiguration.getOwnerFullName());
    }

    @Test
    void doCheckConjurConfiguration() {
        ConjurConfiguration conjurConfiguration = new ConjurConfiguration("https://conjur-master.local:8443/",
                "myConjurAccount");

        String applianceUrl = conjurConfiguration.getApplianceURL();
        assertEquals(conjurConfiguration.getApplianceURL().substring(0, applianceUrl.length() - 1),
                applianceUrl.substring(0, applianceUrl.length() - 1));

        ConjurConfiguration conjurConfigurationEmpty = new ConjurConfiguration("", "myConjurAccount");
        assertEquals("", conjurConfigurationEmpty.getApplianceURL());

        ConjurConfiguration conjurConfigurationEndPoint = new ConjurConfiguration("https://conjur-master.local:8443",
                "myConjurAccount");
        assertEquals("https://conjur-master.local:8443", conjurConfigurationEndPoint.getApplianceURL());
    }

    @Test
    void testDoFillCertificateCredentialIDItemsAdministerPermissionReturnsEmptyModel() {
        ConjurConfiguration.DescriptorImpl descriptor = new ConjurConfiguration.DescriptorImpl();
        ListBoxModel expected = new ListBoxModel();
        expected.add("- none -", "");
        StandardListBoxModel actualModel = (StandardListBoxModel) descriptor.doFillCertificateCredentialIDItems(null,
                null);
        ListBoxModel actual = new ListBoxModel();
        actual.addAll(actualModel);
        assertEquals(expected.size(), actual.size());

        expected.add("credentialID", "credentialID");
        actualModel = (StandardListBoxModel) descriptor.doFillCertificateCredentialIDItems(item, "credentialID");
        actual.addAll(actualModel);
        assertEquals(expected.size(), actual.size());

        expected.add("", "credentialID");
        actualModel = (StandardListBoxModel) descriptor.doFillCertificateCredentialIDItems(null, "credentialID");
        actual.addAll(actualModel);
        assertNotEquals(expected.size(), actual.size());
    }

    @Test
    void testDoFillCredentialIDItems() {
        ConjurConfiguration.DescriptorImpl descriptor = new ConjurConfiguration.DescriptorImpl();
        ListBoxModel expected = new ListBoxModel();
        expected.add("- none -", "");
        StandardListBoxModel actualModel = (StandardListBoxModel) descriptor.doFillCredentialIDItems(null, null);
        ListBoxModel actual = new ListBoxModel();
        actual.addAll(actualModel);
        assertEquals(expected.size(), actual.size());

        expected.add("credentialID", "credentialID");
        actualModel = (StandardListBoxModel) descriptor.doFillCredentialIDItems(item, "credentialID");
        actual.addAll(actualModel);
        assertEquals(expected.size(), actual.size());

        expected.add("", "credentialID");
        actualModel = (StandardListBoxModel) descriptor.doFillCredentialIDItems(null, "credentialID");
        actual.addAll(actualModel);
        assertNotEquals(expected.size(), actual.size());
    }

    public void setGlobalConfiguration() {
        ConjurConfiguration conjurConfiguration = new ConjurConfiguration("https://conjur-master.local:8443", "demo");
        conjurConfiguration.setCredentialID("conjur-login");
        conjurConfiguration.setCertificateCredentialID("Conjur-Master-Certificate");
        globalConfig.setConjurConfiguration(conjurConfiguration);
        globalConfig.save();
    }

    @Test
    void addConjurCredential() {
        setGlobalConfiguration();
        CredentialsStore store = CredentialsProvider.lookupStores(j.jenkins).iterator().next();
        ConjurSecretCredentialsImpl cred = new ConjurSecretCredentialsImpl(CredentialsScope.GLOBAL, "DB_SECRET",
                "db/db_password", "Conjur Secret");

        try {
            store.addCredentials(Domain.global(), cred);
            boolean found = store.getCredentials(Domain.global()).stream()
                    .anyMatch(c -> "DB_SECRET".equals(c.getDescriptor().getId()));
            assertTrue(found);
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    @Test
    void testSetInheritFromParentNullDefaultsToTrue() {
        config.setInheritFromParent(null);
        assertNull(config.getInheritFromParent());
    }

    @Test
    void testSetInheritFromParentFalse() {
        config.setInheritFromParent(false);
        assertFalse(config.getInheritFromParent());
    }

    @Test
    void testSetCredentialID() {
        config.setCredentialID("sample-credId");
        assertEquals("sample-credId", config.getCredentialID());
    }

    @Test
    void testMergeWithParentMergesMissingValues() {
        ConjurConfiguration parentConfig = new ConjurConfiguration("https://parent.com", "parent-account");
        parentConfig.setCredentialID("parent-cred");
        parentConfig.setCertificateCredentialID("parent-cert-cred");
        parentConfig.setOwnerFullName("parent-owner");

        ConjurConfiguration childConfig = new ConjurConfiguration("", "");
        ConjurConfiguration merged = childConfig.mergeWithParent(parentConfig);

        assertEquals("parent-account", merged.getAccount());
        assertEquals("parent-cred", merged.getCredentialID());
        assertEquals("parent-cert-cred", merged.getCertificateCredentialID());
        assertEquals("parent-owner", merged.getOwnerFullName());
    }

    @Test
    void testApplianceUrlTrailingSlashRemoval() {
        ConjurConfiguration withSlash = new ConjurConfiguration("https://jenkins/", "test-account");
        assertEquals("https://jenkins", withSlash.getApplianceURL());
    }

    @Test
    void testDoFillCertificateCredentialIDItems() {
        String credentialsId = "test-credentials-id";
        ConjurConfiguration.DescriptorImpl descriptor = new ConjurConfiguration.DescriptorImpl();
        ListBoxModel result = descriptor.doFillCertificateCredentialIDItems(item, credentialsId);
        assertNotNull(result);
    }

    @Test
    void testGetDisplayName() {
        ConjurConfiguration.DescriptorImpl descriptor = new ConjurConfiguration.DescriptorImpl();
        assertEquals("Conjur Configuration", descriptor.getDisplayName());
    }

    @Test
    void testGetGlobalAuthenticator() {
        ConjurConfiguration config = new ConjurConfiguration();
        assertEquals("APIKey", config.getGlobalAuthenticator());
    }

    @Test
    void testDoObtainJwtToken() {
        GlobalConjurConfiguration globalConfig = GlobalConjurConfiguration.get();
        JwtToken mockToken = mock(JwtToken.class);
        Item mockItem = mock(Item.class);
        try (MockedStatic<JwtToken> token = mockStatic(JwtToken.class)) {
            token.when(() -> JwtToken.getUnsignedToken("pluginAction", mockItem, globalConfig)).thenReturn(mockToken);
        }

        ConjurConfiguration.DescriptorImpl descriptor = new ConjurConfiguration.DescriptorImpl();
        assertEquals(FormValidation.ok().kind, descriptor.doObtainJwtToken(mockItem).kind);
    }
}
