package org.conjur.jenkins.disco.discovery;

/**
 * Result of a successful Service Discovery API call.
 * Mirrors the fields extracted from DiscoveryResponse in jetstack-secure.
 */
public class DiscoveryServiceResult {

    private final String resolvedUrl;
    private final String tenantId;
    private final String identityBaseUrl;
    private final String discoveryContextBaseUrl;

    public DiscoveryServiceResult(String resolvedUrl, String tenantId, String identityBaseUrl,
                                  String discoveryContextBaseUrl) {
        this.resolvedUrl = resolvedUrl;
        this.tenantId = tenantId;
        this.identityBaseUrl = identityBaseUrl;
        this.discoveryContextBaseUrl = discoveryContextBaseUrl;
    }

    public String getResolvedUrl() { return resolvedUrl; }
    public String getTenantId() { return tenantId; }
    public String getIdentityBaseUrl() { return identityBaseUrl; }

    /** Raw base URL of the discoverycontext service, used to build the JWKS endpoint. */
    public String getDiscoveryContextBaseUrl() { return discoveryContextBaseUrl; }
}
