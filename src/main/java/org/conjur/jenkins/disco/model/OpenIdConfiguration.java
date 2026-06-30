package org.conjur.jenkins.disco.model;

public class OpenIdConfiguration {
    private String issuer;
    private String jwksUri;
    private Object jwksData;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public Object getJwksData() { return jwksData; }
    public void setJwksData(Object jwksData) { this.jwksData = jwksData; }
}