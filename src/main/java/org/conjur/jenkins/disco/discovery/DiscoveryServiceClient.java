package org.conjur.jenkins.disco.discovery;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.conjur.jenkins.disco.DiscoCode;
import org.conjur.jenkins.disco.config.DiscoEnvironment;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Calls the CyberArk Platform Discovery API to resolve all service endpoints
 * for a given subdomain. Mirrors DiscoverServices() in jetstack-secure.
 *
 * API: GET https://platform-discovery.{domain}/api/public/tenant-discovery?bySubdomain={subdomain}
 *
 * Extracts:
 *   - tenant_id            → used in S3 upload tagging
 *   - identity_administration API URL → used for CyberArk Identity login
 *   - discoverycontext API URL        → used as the snapshot-links base URL
 */
public class DiscoveryServiceClient {

    private static final Logger LOGGER = Logger.getLogger(DiscoveryServiceClient.class.getName());

    private static final String IDENTITY_SERVICE   = "identity_administration";
    private static final String DISCOVERY_SERVICE  = "discoverycontext";
    private static final String ENDPOINT_TYPE_MAIN = "main";
    private static final String SNAPSHOT_LINKS_PATH = "/ingestions/jenkins/snapshot-links";

    private final OkHttpClient httpClient;

    public DiscoveryServiceClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Resolves all required service endpoints for the given subdomain.
     *
     * @param platformDiscoveryBaseUrl base URL of the Platform Discovery API
     *                                 (e.g. https://platform-discovery.cyberark.cloud)
     * @param subdomain                the tenant subdomain (e.g. "acme")
     * @return resolved service endpoints and tenant metadata
     * @throws IOException on network failure or unexpected response
     */
    public DiscoveryServiceResult resolve(String platformDiscoveryBaseUrl, String subdomain)
            throws IOException {

        HttpUrl url = HttpUrl.parse(platformDiscoveryBaseUrl.replaceAll("/$", "")
                + "/api/public/tenant-discovery");
        if (url == null) {
            throw new IOException(INVALID_DISCOVERY_URL.format(platformDiscoveryBaseUrl));
        }

        HttpUrl requestUrl = url.newBuilder()
                .addQueryParameter("bySubdomain", subdomain)
                .addQueryParameter("allEndpoints", "false")
                .addQueryParameter("selectedFields", "tenant_id,services")
                .addQueryParameter("selectedServices", IDENTITY_SERVICE + "," + DISCOVERY_SERVICE)
                .build();

        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .build();

        LOGGER.info(DISCOVERY_SERVICE_CALLED.format(requestUrl));

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 403) {
                throw new IOException(PLATFORM_DISCOVERY_ACCESS_DENIED.format(subdomain));
            }
            if (response.code() == 404) {
                throw new IOException(SUBDOMAIN_NOT_FOUND.format(subdomain));
            }
            if (!response.isSuccessful()) {
                throw new IOException(DISCOVERY_HTTP_ERROR.format(response.code(), subdomain));
            }
            String body = response.body() != null ? response.body().string() : "";
            if (body.isEmpty()) {
                throw new IOException(DISCOVERY_EMPTY_RESPONSE.format());
            }
            return parse(body, subdomain);
        }
    }

    private DiscoveryServiceResult parse(String body, String subdomain) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException(DISCOVERY_RESPONSE_NOT_JSON.format());
        }

        String tenantId = root.has("tenant_id") ? root.get("tenant_id").getAsString() : "";
        if (tenantId.isBlank()) {
            throw new IOException(DISCOVERY_MISSING_TENANT_ID.format(subdomain));
        }

        String identityBaseUrl = "";
        String discoveryContextUrl = "";

        JsonArray services = root.has("services") ? root.getAsJsonArray("services") : new JsonArray();
        for (int i = 0; i < services.size(); i++) {
            JsonObject svc = services.get(i).getAsJsonObject();
            String name = svc.has("service_name") ? svc.get("service_name").getAsString() : "";

            if (!IDENTITY_SERVICE.equals(name) && !DISCOVERY_SERVICE.equals(name)) continue;

            JsonArray endpoints = svc.has("endpoints") ? svc.getAsJsonArray("endpoints") : new JsonArray();
            for (int j = 0; j < endpoints.size(); j++) {
                JsonObject ep = endpoints.get(j).getAsJsonObject();
                boolean active = ep.has("is_active") && ep.get("is_active").getAsBoolean();
                String type = ep.has("type") ? ep.get("type").getAsString() : "";
                if (!active || !ENDPOINT_TYPE_MAIN.equals(type)) continue;

                String api = ep.has("api") ? ep.get("api").getAsString() : "";
                if (IDENTITY_SERVICE.equals(name)) identityBaseUrl = api;
                if (DISCOVERY_SERVICE.equals(name)) discoveryContextUrl = api;
            }
        }

        if (identityBaseUrl.isBlank()) {
            throw new IOException(DISCOVERY_MISSING_IDENTITY.format());
        }
        if (discoveryContextUrl.isBlank()) {
            throw new IOException(DISCOVERY_MISSING_DISCOVERYCONTEXT.format());
        }

        String trustedSuffix = DiscoEnvironment.resolve().getTrustedDomainSuffix();
        validateHost(identityBaseUrl, trustedSuffix);
        validateHost(discoveryContextUrl, trustedSuffix);

        String discoveryContextBaseUrl = discoveryContextUrl.replaceAll("/$", "");
        String snapshotLinksUrl = discoveryContextBaseUrl + SNAPSHOT_LINKS_PATH;

        LOGGER.info(DISCOVERY_RESOLVED.format(subdomain, tenantId, identityBaseUrl,
                snapshotLinksUrl, discoveryContextBaseUrl));

        return new DiscoveryServiceResult(snapshotLinksUrl, tenantId, identityBaseUrl, discoveryContextBaseUrl);
    }

    private void validateHost(String url, String trustedSuffix) throws IOException {
        try {
            String host = new URI(url).getHost();
            if (host == null || (!host.equals(trustedSuffix) && !host.endsWith("." + trustedSuffix))) {
                throw new IOException(DISCOVERY_UNTRUSTED_HOST.format(host, trustedSuffix));
            }
        } catch (java.net.URISyntaxException e) {
            throw new IOException(DISCOVERY_UNTRUSTED_HOST.format(url, trustedSuffix));
        }
    }
}
