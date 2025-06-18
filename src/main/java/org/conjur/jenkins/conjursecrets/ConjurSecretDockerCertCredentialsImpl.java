package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ConjurSecretDockerCertCredentialsImpl extends ConjurSecretDockerCertCredentials {

    private static final long serialVersionUID = 1L;

    private final String clientKeyId;
    private final String clientCertificateId;
    private final String caCertificateId;
    private transient ModelObject context;
    private transient ModelObject inheritedObjectContext;
    private boolean storedInConjurStorage = false;

    @DataBoundConstructor
    public ConjurSecretDockerCertCredentialsImpl(CredentialsScope scope, String id, String description,
                                                 String clientKeyId, String clientCertificateId,
                                                 String caCertificateId) {
        super(scope, id, description);
        this.clientKeyId = clientKeyId;
        this.clientCertificateId = clientCertificateId;
        this.caCertificateId = caCertificateId;
    }

    @Override
    public Secret getSecret() {
        return this.getClientKeySecret();
    }

    @Override
    public String getClientKeyId() {
        return clientKeyId;
    }

    @Override
    public String getClientCertificateId() {
        return clientCertificateId;
    }

    @Override
    public String getCaCertificateId() {
        return caCertificateId;
    }

    @Override
    public String getDisplayName() {
        return "ConjurSecretDockerCert:" + getId();
    }

    @Override
    public String getNameTag() {
        return "";
    }

    @Override
    public void setContext(ModelObject context) {
        this.context = context;
    }

    @Override
    public ModelObject getContext() {
        return this.context;
    }

    @Override
    public void setInheritedContext(ModelObject context) {
        this.inheritedObjectContext = context;
    }

    @Override
    public ModelObject getInheritedContext() {
        return this.inheritedObjectContext;
    }

    @Override
    public void setStoredInConjurStorage(boolean stored) {
        this.storedInConjurStorage = stored;
    }

    @Override
    public boolean storedInConjurStorage() {
        return this.storedInConjurStorage;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return "Conjur Secret Docker Client Certificate";
        }

        public FormValidation doTestConnection(
                @AncestorInPath ItemGroup<Item> context,
                @QueryParameter("clientKeyId") String clientKeyId,
                @QueryParameter("clientCertificateId") String clientCertificateId,
                @QueryParameter("caCertificateId") String caCertificateId) {

            if (clientKeyId == null || clientCertificateId == null || caCertificateId == null) {
                return FormValidation.error("All certificate fields are required");
            }

            ConjurSecretDockerCertCredentialsImpl credential = new ConjurSecretDockerCertCredentialsImpl(
                    CredentialsScope.GLOBAL, "test", "desc",
                    clientKeyId, clientCertificateId, caCertificateId);

            return ConjurAPIUtils.validateCredential(context, credential);
        }
    }
}