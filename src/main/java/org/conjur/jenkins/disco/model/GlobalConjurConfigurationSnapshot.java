package org.conjur.jenkins.disco.model;

import org.conjur.jenkins.configuration.GlobalConjurConfiguration;

public class GlobalConjurConfigurationSnapshot {

    private String applianceURL;
    private String account;
    private String credentialID;
    private String certificateCredentialID;
    private Boolean inheritFromParent;
    private String authWebServiceId;
    private String jwtAudience;
    private long keyLifetimeInMinutes;
    private long tokenDurationInSeconds;
    private String selectAuthenticator;
    private String selectIdentityFormatToken;
    private String selectIdentityFieldsSeparator;
    private String identityFormatFieldsFromToken;
    private String identityFieldName;

    public static GlobalConjurConfigurationSnapshot from(GlobalConjurConfiguration cfg) {
        if (cfg == null) return null;
        GlobalConjurConfigurationSnapshot s = new GlobalConjurConfigurationSnapshot();
        s.authWebServiceId = cfg.getAuthWebServiceId();
        s.jwtAudience = cfg.getJwtAudience();
        s.keyLifetimeInMinutes = cfg.getKeyLifetimeInMinutes();
        s.tokenDurationInSeconds = cfg.getTokenDurationInSeconds();
        s.selectAuthenticator = cfg.getSelectAuthenticator();
        s.selectIdentityFormatToken = cfg.getSelectIdentityFormatToken();
        s.selectIdentityFieldsSeparator = cfg.getSelectIdentityFieldsSeparator();
        s.identityFormatFieldsFromToken = cfg.getIdentityFormatFieldsFromToken();
        s.identityFieldName = cfg.getidentityFieldName();
        if (cfg.getConjurConfiguration() != null) {
            s.applianceURL = cfg.getConjurConfiguration().getApplianceURL();
            s.account = cfg.getConjurConfiguration().getAccount();
            s.credentialID = cfg.getConjurConfiguration().getCredentialID();
            s.certificateCredentialID = cfg.getConjurConfiguration().getCertificateCredentialID();
            s.inheritFromParent = cfg.getConjurConfiguration().getInheritFromParent();
        }
        return s;
    }

    public String getApplianceURL() { return applianceURL; }
    public String getAccount() { return account; }
    public String getCredentialID() { return credentialID; }
    public String getCertificateCredentialID() { return certificateCredentialID; }
    public Boolean getInheritFromParent() { return inheritFromParent; }
    public String getAuthWebServiceId() { return authWebServiceId; }
    public String getJwtAudience() { return jwtAudience; }
    public long getKeyLifetimeInMinutes() { return keyLifetimeInMinutes; }
    public long getTokenDurationInSeconds() { return tokenDurationInSeconds; }
    public String getSelectAuthenticator() { return selectAuthenticator; }
    public String getSelectIdentityFormatToken() { return selectIdentityFormatToken; }
    public String getSelectIdentityFieldsSeparator() { return selectIdentityFieldsSeparator; }
    public String getIdentityFormatFieldsFromToken() { return identityFormatFieldsFromToken; }
    public String getIdentityFieldName() { return identityFieldName; }
}
