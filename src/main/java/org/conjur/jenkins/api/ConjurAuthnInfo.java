package org.conjur.jenkins.api;

import org.conjur.jenkins.configuration.ConjurConfiguration;

public class ConjurAuthnInfo {
    /**
     * static constructor to set the Conjur Auth Configuration Info
     */
        public ConjurConfiguration conjurConfiguration;
        public String applianceUrl;
        public String authnPath;
        public String account;
        public String login;    // used to hold login to Conjur
        public byte[] apiKey;   // used to hold apikey

    /**
     *
     * @return ConjutAuthnInfo value as String
     */
    @Override
    public String toString() {
        return "ConjurAuthnInfo{" +
                "\nconjurConfiguration=" + conjurConfiguration +
                ", \napplianceUrl='" + applianceUrl + '\'' +
                ", \nauthnPath='" + authnPath + '\'' +
                ", \naccount='" + account + '\'' +
                ", \nlogin='" + login + '\'' +
                '}';
    }
}
