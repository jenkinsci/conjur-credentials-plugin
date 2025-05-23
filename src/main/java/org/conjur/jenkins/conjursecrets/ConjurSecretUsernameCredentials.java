package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;

/**
 *Interface to get the DispalyName,Context, secret
 */
public interface ConjurSecretUsernameCredentials extends StandardUsernamePasswordCredentials, ConjurSecretCredentials {

	String getDisplayName();

	Secret getSecret( );
}
