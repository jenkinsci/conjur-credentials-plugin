package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.configuration.ConjurConfiguration;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;


public interface ConjurDirectCredentials extends StandardCredentials {

    static Logger getLogger() {
        return Logger.getLogger(ConjurDirectCredentials.class.getName());
    }

    class NameProvider extends CredentialsNameProvider<ConjurDirectCredentials> {

        @Override
        public String getName(ConjurDirectCredentials c) {
            return c.getDisplayName() + c.getNameTag();
        }

    }

    String getDisplayName();

    String getNameTag();

    Secret getSecret(String path);


    void setConjurConfiguration(ConjurConfiguration conjurConfiguration);

    void setContext(Run<?, ?> context);

    static ConjurDirectCredentials credentialFromContextIfNeeded(ConjurDirectCredentials credential, String credentialID, Run<?, ?> context) {
        if (credential == null && context != null) {
            getLogger().log(Level.INFO, "NOT FOUND at Jenkins Instance Level!");
            Item folder = Jenkins.get().getItemByFullName(context.getParent().getParent().getFullName());
            return CredentialsMatchers
                    .firstOrNull(
                            CredentialsProvider.lookupCredentials(ConjurDirectCredentials.class, folder, ACL.SYSTEM,
                                    Collections.<DomainRequirement>emptyList()),
                            CredentialsMatchers.withId(credentialID));
        }
        return credential;
    }


}
