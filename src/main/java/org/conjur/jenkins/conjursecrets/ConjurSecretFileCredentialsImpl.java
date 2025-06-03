package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.model.ModelObject;
import hudson.util.Secret;
import org.conjur.jenkins.api.ConjurAPI;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.lang.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConjurSecretFileCredentialsImpl extends BaseStandardCredentials implements ConjurSecretFileCredentials {

    private static final long serialVersionUID = 1L;
    private final String variableId;
    private transient ModelObject context;
    private transient ModelObject inheritedObjectContext;
    private boolean storedInConjurStorage = false;

    @DataBoundConstructor
    public ConjurSecretFileCredentialsImpl(CredentialsScope scope, String id, String description, String variableId) {
        super(scope, id, description);
        this.variableId = variableId;
    }

    @NonNull
    @Override
    public String getFileName() {
        return "conjur-file";
    }

    @NonNull
    @Override
    public InputStream getContent() throws IOException {
        Secret secret = getSecret();
        if (secret == null) {
            throw new IOException("Can't retrieve secret for variableId: " + this.variableId);
        }
        return new ByteArrayInputStream(secret.getPlainText().getBytes());
    }

    @Override
    public Secret getSecret() {
        Secret retSecret = null;
        if (storedInConjurStorage) {
            retSecret = ConjurAPI.getSecretFromConjur(this.context, this.inheritedObjectContext, this.variableId);
        } else {
            retSecret = ConjurAPI.getSecretFromConjurWithInheritance(this.context, this, this.variableId);
        }
        return retSecret;
    }

    @Override
    public String getDisplayName() {
        return "ConjurSecretFile:" + this.variableId;
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
    public void setStoredInConjurStorage(boolean storedInConjurStorage) {
        this.storedInConjurStorage = storedInConjurStorage;
    }

    @Override
    public boolean storedInConjurStorage() {
        return this.storedInConjurStorage;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Conjur Secret File";
        }
    }
}