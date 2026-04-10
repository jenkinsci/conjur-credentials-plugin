package org.conjur.jenkins.api;

import lombok.Getter;
import lombok.Setter;
import org.conjur.jenkins.configuration.ConjurConfiguration;

@Getter
@Setter
public class ConjurAuthnInfo {
    /**
     * static constructor to set the Conjur Auth Configuration Info
     */
        private ConjurConfiguration conjurConfiguration;
        private String applianceUrl;
        private String authnPath;
        private String account;
        private String login;    // used to hold login to Conjur
        private byte[] apiKey;   // used to hold apikey

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
