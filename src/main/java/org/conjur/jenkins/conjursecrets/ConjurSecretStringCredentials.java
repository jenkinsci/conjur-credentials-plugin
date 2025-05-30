package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 *
 */
@NameWith(value = ConjurSecretStringCredentials.NameProvider.class, priority = 32)
public interface ConjurSecretStringCredentials extends StringCredentials, ConjurSecretCredentials {
    String getDisplayName();

    String getNameTag();

    Secret getSecret( );

    /**
     *
     */
    class NameProvider extends CredentialsNameProvider<ConjurSecretStringCredentials> {

        @NonNull
        @Override
        public String getName(ConjurSecretStringCredentials cyberARKVaultCredentials) {
            String description = Util.fixEmpty(cyberARKVaultCredentials.getDescription());
            return cyberARKVaultCredentials.getDisplayName() + (description == null ? ""
                    : " (" + description + ")");
        }
    }
}
