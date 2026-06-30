package org.conjur.jenkins.disco.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Two-step CyberArk Identity login:
 *   1. StartAuthentication  — sends username, receives sessionId + mechanismId
 *   2. AdvanceAuthentication — sends password, receives bearer token
 *
 * Token is cached for 15 minutes. Password bytes are zeroed immediately after use.
 */
public class CyberArkIdentityClient {

    private static final Logger LOGGER = Logger.getLogger(CyberArkIdentityClient.class.getName());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long TOKEN_TTL_MS = 15 * 60 * 1000L;

    private final OkHttpClient httpClient;
    private volatile byte[] cachedToken;
    private volatile long tokenFetchedAt = 0L;

    public CyberArkIdentityClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Logs in and returns a bearer token as UTF-8 bytes. Caches result for 15 minutes.
     * Callers must zero the returned array with {@code Arrays.fill(token, (byte) 0)} after use.
     *
     * @param identityBaseUrl e.g. https://tenant.id.cyberark.cloud
     * @param username        login username
     * @param password        password bytes — zeroed after use regardless of outcome
     */
    public synchronized byte[] login(String identityBaseUrl, String username, byte[] password)
            throws Exception {
        return login(identityBaseUrl, "", username, password);
    }

    /**
     * Logs in with an explicit tenant ID and returns a bearer token as UTF-8 bytes.
     * Caches result for 15 minutes. Callers must zero the returned array after use.
     *
     * @param identityBaseUrl e.g. https://aoj5620.id.integration-cyberark.cloud
     * @param tenantId        CyberArk tenant UUID (e.g. 3ac63bfb-fd47-433a-bb93-2f290e5388b8)
     * @param username        login username (User field, not Username)
     * @param password        password bytes — zeroed after use regardless of outcome
     */
    public synchronized byte[] login(String identityBaseUrl, String tenantId,
                                     String username, byte[] password)
            throws Exception {
        if (cachedToken != null && (System.currentTimeMillis() - tokenFetchedAt) < TOKEN_TTL_MS) {
            LOGGER.log(Level.FINE, IDENTITY_TOKEN_CACHED.format());
            return Arrays.copyOf(cachedToken, cachedToken.length);
        }
        byte[] freshToken = null;
        try {
            String[] sessionAndMechanism = startAuthentication(identityBaseUrl, tenantId, username);
            freshToken = advanceAuthentication(identityBaseUrl, tenantId, sessionAndMechanism[0],
                    sessionAndMechanism[1], password);
            if (cachedToken != null) Arrays.fill(cachedToken, (byte) 0);
            cachedToken = freshToken;
            freshToken = null; // ownership transferred to cachedToken
            tokenFetchedAt = System.currentTimeMillis();
            LOGGER.info(IDENTITY_LOGIN_SUCCESS.format(username));
            return Arrays.copyOf(cachedToken, cachedToken.length);
        } catch (Exception e) {
            if (freshToken != null) Arrays.fill(freshToken, (byte) 0);
            throw e;
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }

    // -------------------------------------------------------------------------

    private String[] startAuthentication(String identityBaseUrl, String tenantId,
                                         String username) throws Exception {
        String url = identityBaseUrl.replaceAll("/$", "") + "/Security/StartAuthentication";

        JsonObject body = new JsonObject();
        body.addProperty("TenantId", tenantId != null ? tenantId : "");
        body.addProperty("Version", "1.0");
        // Use "User" field — required by some Identity pod versions
        body.addProperty("User", username);

        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("X-IDAP-NATIVE-CLIENT", "true")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(START_AUTH_HTTP_ERROR.format(response.code()));
            }
            JsonObject parsed = JsonParser.parseString(respBody).getAsJsonObject();
            JsonObject result = parsed.has("Result") ? parsed.getAsJsonObject("Result") : null;
            if (result == null) {
                throw new IOException(START_AUTH_NO_RESULT.format());
            }
            if (!result.has("SessionId") || result.get("SessionId").isJsonNull()) {
                throw new IOException(START_AUTH_NO_RESULT.format());
            }
            String sessionId = result.get("SessionId").getAsString();

            JsonArray challenges = result.has("Challenges")
                    ? result.getAsJsonArray("Challenges") : new JsonArray();
            for (var challenge : challenges) {
                JsonObject challengeObj = challenge.getAsJsonObject();
                JsonArray mechanisms = challengeObj.has("Mechanisms")
                        ? challengeObj.getAsJsonArray("Mechanisms") : new JsonArray();
                for (var mech : mechanisms) {
                    JsonObject mechObj = mech.getAsJsonObject();
                    String name = mechObj.has("Name") && !mechObj.get("Name").isJsonNull()
                            ? mechObj.get("Name").getAsString() : "";
                    if ("UP".equals(name)) {
                        if (!mechObj.has("MechanismId") || mechObj.get("MechanismId").isJsonNull()) {
                            throw new IOException(START_AUTH_NO_UP_MECHANISM.format());
                        }
                        return new String[]{sessionId, mechObj.get("MechanismId").getAsString()};
                    }
                }
            }
            throw new IOException(START_AUTH_NO_UP_MECHANISM.format());
        }
    }

    private byte[] advanceAuthentication(String identityBaseUrl, String tenantId,
                                          String sessionId, String mechanismId,
                                          byte[] passwordBytes) throws Exception {
        String url = identityBaseUrl.replaceAll("/$", "") + "/Security/AdvanceAuthentication";
        String password = new String(passwordBytes, StandardCharsets.UTF_8);

        JsonObject body = new JsonObject();
        body.addProperty("TenantId", tenantId != null ? tenantId : "");
        body.addProperty("SessionId", sessionId);
        body.addProperty("MechanismId", mechanismId);
        body.addProperty("Action", "Answer");
        body.addProperty("Answer", password);
        body.addProperty("PersistentLogin", true);

        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("X-IDAP-NATIVE-CLIENT", "true")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(ADVANCE_AUTH_HTTP_ERROR.format(response.code()));
            }
            JsonObject parsed = JsonParser.parseString(respBody).getAsJsonObject();
            JsonObject result = parsed.has("Result") ? parsed.getAsJsonObject("Result") : null;
            if (result == null || !result.has("Token")) {
                throw new IOException(ADVANCE_AUTH_NO_TOKEN.format());
            }
            return result.get("Token").getAsString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
