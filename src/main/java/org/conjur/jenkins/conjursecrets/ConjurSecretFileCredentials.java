package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;

@NameWith(value = ConjurSecretFileCredentials.NameProvider.class, priority = 32)
public interface ConjurSecretFileCredentials extends FileCredentials, ConjurSecretCredentials {
    String getDisplayName();

    String getNameTag();

    class NameProvider extends CredentialsNameProvider<ConjurSecretFileCredentials> {
        @NonNull
        @Override
        public String getName(ConjurSecretFileCredentials credentials) {
            String description = Util.fixEmpty(credentials.getDescription());
            return credentials.getDisplayName() + (description == null ? "" : " (" + description + ")");
        }
    }
}