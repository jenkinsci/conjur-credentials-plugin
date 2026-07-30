package org.conjur.jenkins.disco.e2e.helpers;

public class ExpectedCredentialPathBuilder {
    public static String buildExpectedSecretPath(String folderName, String credentialName) {
        return folderName + ":" + credentialName;
    }

    public static String buildExpectedGlobalSecretPath(String credentialName) {
        return "Global:" + credentialName;
    }
}
