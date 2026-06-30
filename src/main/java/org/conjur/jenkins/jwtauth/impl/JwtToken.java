package org.conjur.jenkins.jwtauth.impl;

import hudson.model.*;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.CjplCode;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.JwtException;
import org.jose4j.jws.AlgorithmIdentifiers;

import static org.conjur.jenkins.CjplCode.*;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to generate JWT Token and sign the request based on the JWT Token
 */
public class JwtToken {
    private static final Logger LOGGER = Logger.getLogger(JwtToken.class.getName());
    private static final int DEFAULT_NOT_BEFORE_IN_SEC = 30;
    private static final String IDENTITY_FIELD_NAME_PATTERN = "^[a-zA-Z0-9\\-_\\\"]*$";
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("MMddkkmmss").withZone(ZoneId.systemDefault());
    private static final ConcurrentLinkedQueue<JwtRsaDigitalSignatureKey> keysQueue = new ConcurrentLinkedQueue<JwtRsaDigitalSignatureKey>();

    /**
     * JWT Claim
     */
    public final JSONObject claim = new JSONObject();

    /**
     * Generates base64 representation of JWT token sign using "RS256" algorithm
     * <p>
     * getHeader().toBase64UrlEncode() + "." + getClaim().toBase64UrlEncode() + "."
     * + sign
     *
     * @return base64 representation of JWT token
     */
    public String sign() {
        try {
            JsonWebSignature jsonWebSignature = new JsonWebSignature();
            JwtRsaDigitalSignatureKey key = getCurrentSigningKey(this);
            jsonWebSignature.setPayload(claim.toString());
            jsonWebSignature.setKey(key.toSigningKey());
            jsonWebSignature.setKeyIdHeaderValue(key.getId());
            jsonWebSignature.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jsonWebSignature.setHeader(HeaderParameterNames.TYPE, "JWT");

            return jsonWebSignature.getCompactSerialization();
        } catch (JoseException e) {
            throw new JwtException(JWT_SIGN_FAILED.format(e.getMessage()), e);
        }

    }

    /**
     * retrun the JWT Token for the context
     *
     * @param context      Context to which JWT will be generated
     * @param globalConfig ConjurGlobalConfiguration
     * @return JWT Token as string
     */
    public static synchronized String getToken(Object context, GlobalConjurConfiguration globalConfig) {
        return getToken("SecretRetrieval", context, globalConfig);

    }

    /**
     * return the JWT Token for the pluginAction and Context
     *
     * @param pluginAction action name
     * @param context      Context to which JWT will be created
     * @param globalConfig GlobalConjurConfiguration
     * @return JWT Token as String
     */
    public static synchronized String getToken(String pluginAction, Object context, GlobalConjurConfiguration globalConfig) {
        JwtToken unsignedToken = getUnsignedToken(pluginAction, context, globalConfig);
        if (unsignedToken != null) {
            return unsignedToken.sign();
        }
        return null;
    }

    /**
     * generates a new JWT token
     *
     * @param pluginAction
     * @param context
     * @return JWTToken
     */
    public synchronized static JwtToken getUnsignedToken(String pluginAction, Object context, GlobalConjurConfiguration globalConfig) {
        if (context == null) {
            LOGGER.log(Level.SEVERE, JWT_NULL_CONTEXT.format());
            return null;
        }

        if (globalConfig == null) {
            LOGGER.log(Level.SEVERE, JWT_NO_GLOBAL_CONFIG.format());
            return null;
        }

        @SuppressWarnings("deprecation") Authentication authentication = Jenkins.getAuthentication();

        String userId = authentication.getName();

        User user = User.get(userId, false, Collections.emptyMap());
        String fullName = null;
        if (user != null) {
            fullName = user.getFullName();
        }
        String issuer = ConjurAPIUtils.getJenkinsIssuer();
        LOGGER.log(Level.FINEST, "RootURL => {0}", Jenkins.get().getRootUrl());

        JwtToken jwtToken = new JwtToken();
        jwtToken.claim.put("jti", UUID.randomUUID().toString().replace("-", ""));
        jwtToken.claim.put("aud", globalConfig.getJwtAudience());
        jwtToken.claim.put("iss", issuer);
        jwtToken.claim.put("name", fullName);
        long currentTime = System.currentTimeMillis() / 1000;
        jwtToken.claim.put("iat", currentTime);
        jwtToken.claim.put("exp", currentTime + globalConfig.getTokenDurationInSeconds());
        jwtToken.claim.put("nbf", currentTime - DEFAULT_NOT_BEFORE_IN_SEC);

        ModelObject contextObject = (ModelObject) context;

        if (contextObject instanceof Run) {
            Run<?, ?> run = (Run<?, ?>) contextObject;

            jwtToken.claim.put("jenkins_build_number", run.getNumber());
            contextObject = run.getParent();
        }

        if (contextObject instanceof AbstractItem) {
            if (contextObject instanceof Job) {
                Job<?, ?> job = (Job<?, ?>) contextObject;
                jwtToken.claim.put("jenkins_pronoun", job.getPronoun());
            } else {
                jwtToken.claim.put("jenkins_pronoun", ((AbstractItem) contextObject).getPronoun());
            }

            AbstractItem item = (AbstractItem) contextObject;

            jwtToken.claim.put("jenkins_full_name", item.getFullName());
            jwtToken.claim.put("jenkins_name", item.getName());
            // change later
            //jwtToken.claim.put("jenkins_name", item.getFullName());
            jwtToken.claim.put("jenkins_task_noun", item.getTaskNoun());
            if (item instanceof ItemGroup) {
                ItemGroup<?> itemGroup = (ItemGroup<?>) item;
                jwtToken.claim.put("jenkins_url_child_prefix", itemGroup.getUrlChildPrefix());
            }
            if (item instanceof Job) {
                Job<?, ?> job = (Job<?, ?>) item;

                jwtToken.claim.put("jenkins_job_buildir", job.getBuildDir().getAbsolutePath());
            }

            ItemGroup<?> parent = item.getParent();
            if (parent instanceof AbstractItem) {
                item = (AbstractItem) parent;
                jwtToken.claim.put("jenkins_parent_full_name", item.getFullName());
                jwtToken.claim.put("jenkins_parent_name", item.getName());
                jwtToken.claim.put("jenkins_parent_task_noun", item.getTaskNoun());
                if (item instanceof ItemGroup) {
                    ItemGroup<?> itemGroup = (ItemGroup<?>) item;
                    jwtToken.claim.put("jenkins_parent_url_child_prefix", itemGroup.getUrlChildPrefix());
                }
                if (item instanceof Job) {
                    Job<?, ?> job = (Job<?, ?>) item;
                    jwtToken.claim.put("jenkins_parent_pronoun", job.getPronoun());
                }
            }

            // based ont eh checkbox selection
            // if checkbox is enabled its "sub", "identityformatfields"
            // if checkbox is disabled its 'identity' as old code hold good
            boolean isEnabled = globalConfig.getEnableIdentityFormatFieldsFromToken();
            String identityFieldName = "";
            String separator = "";
            if (!isEnabled) {
                LOGGER.log(Level.FINE, "Disable JWT Simplified");
                // Add identity field
                List<String> identityFields = Arrays.asList(globalConfig.getIdentityFormatFieldsFromToken().split(","));
                String fieldSeparator = globalConfig.getSelectIdentityFieldsSeparator();
                List<String> identityValues = new ArrayList<>(identityFields.size());
                for (String identityField : identityFields) {
                    String identityFieldValue = jwtToken.claim.has(identityField) ? jwtToken.claim.getString(identityField) : "";
                    identityValues.add(identityFieldValue);
                    LOGGER.log(Level.FINE, "getUnsignedToken() *** processed identity field:" + identityField + " and value:" + identityFieldValue);
                }
                identityFieldName = processIdentityFieldName(globalConfig.getidentityFieldName());
                LOGGER.log(Level.FINE, "end of processIdentityFieldName()) identityFieldName : " + identityFieldName);
                final String identityFieldValue = StringUtils.join(identityValues, fieldSeparator);
                jwtToken.claim.put(identityFieldName, identityFieldValue);
                jwtToken.claim.put("sub", identityFieldValue);

            } else {
                LOGGER.log(Level.FINE, "Enable JWT Simplified");
                // Add identity field default to Sub
                List<String> identityFields = Arrays.asList(globalConfig.getSelectIdentityFormatToken().split("[-,+,|,:,.]"));
                List<String> identityValues = new ArrayList<>(identityFields.size());
                String token = globalConfig.getSelectIdentityFormatToken();
                String parentField = identityFields.get(0);
                if (token.length() > parentField.length() + 1) {
                    separator = token.substring(parentField.length(), parentField.length() + 1);
                } else {
                    identityFields = Collections.singletonList(token);  //containing a single element
                }
                for (String identityField : identityFields) {

                    String identityFieldValue = jwtToken.claim.has(identityField) ? jwtToken.claim.getString(identityField) : "";
                    identityValues.add(identityFieldValue);
                    LOGGER.log(Level.FINE, "getUnsignedToken() *** processed identity field:" + identityField + " and value:" + identityFieldValue);
                }
                jwtToken.claim.put("sub", StringUtils.join(identityValues, separator));
            }

        } else if (contextObject instanceof Hudson) {
            jwtToken.claim.put("jenkins_pronoun", "Global");    // this have to be in policy
            jwtToken.claim.put("jenkins_task_noun", "Build");
            jwtToken.claim.put("jenkins_parent_name", "/");
            jwtToken.claim.put("jenkins_name", "GlobalCredentials");
            jwtToken.claim.put("jenkins_full_name", "GlobalCredentials");
            jwtToken.claim.put("jenkins_parent_name", "/");
            jwtToken.claim.put("sub", "GlobalCredentials");
        }
        LOGGER.log(Level.FINEST, String.format("Claim : %s", jwtToken.claim.toString()));
        return jwtToken;
    }

    /**
     * Returns the JWT "sub" claim value that would be assigned for a given Jenkins context object,
     * using the same logic as {@link #getUnsignedToken}. Returns an empty string when the context
     * produces no sub claim (e.g. null or unknown type).
     */
    public static String computeSubClaim(Object context, GlobalConjurConfiguration globalConfig) {
        if (context == null || globalConfig == null) return "";
        ModelObject contextObject = (ModelObject) context;
        if (contextObject instanceof Run) {
            contextObject = ((Run<?, ?>) contextObject).getParent();
        }
        if (contextObject instanceof Hudson) {
            return "GlobalCredentials";
        }
        if (!(contextObject instanceof AbstractItem)) return "";

        AbstractItem item = (AbstractItem) contextObject;
        // Build the same intermediate claims used for sub resolution
        JwtToken probe = new JwtToken();
        probe.claim.put("jenkins_full_name", item.getFullName());
        probe.claim.put("jenkins_name", item.getName());
        probe.claim.put("jenkins_task_noun", item.getTaskNoun());
        if (item instanceof ItemGroup) {
            probe.claim.put("jenkins_url_child_prefix", ((ItemGroup<?>) item).getUrlChildPrefix());
        }
        if (item instanceof Job) {
            probe.claim.put("jenkins_job_buildir", ((Job<?, ?>) item).getBuildDir().getAbsolutePath());
        }
        ItemGroup<?> parent = item.getParent();
        if (parent instanceof AbstractItem) {
            AbstractItem parentItem = (AbstractItem) parent;
            probe.claim.put("jenkins_parent_full_name", parentItem.getFullName());
            probe.claim.put("jenkins_parent_name", parentItem.getName());
            probe.claim.put("jenkins_parent_task_noun", parentItem.getTaskNoun());
        }

        boolean isEnabled = globalConfig.getEnableIdentityFormatFieldsFromToken();
        if (!isEnabled) {
            List<String> identityFields = Arrays.asList(globalConfig.getIdentityFormatFieldsFromToken().split(","));
            String fieldSeparator = globalConfig.getSelectIdentityFieldsSeparator();
            List<String> identityValues = new ArrayList<>(identityFields.size());
            for (String identityField : identityFields) {
                identityValues.add(probe.claim.has(identityField) ? probe.claim.getString(identityField) : "");
            }
            return StringUtils.join(identityValues, fieldSeparator);
        } else {
            List<String> identityFields = Arrays.asList(globalConfig.getSelectIdentityFormatToken().split("[-,+,|,:,.]"));
            String token = globalConfig.getSelectIdentityFormatToken();
            String parentField = identityFields.get(0);
            String separator = "";
            if (token.length() > parentField.length() + 1) {
                separator = token.substring(parentField.length(), parentField.length() + 1);
            } else {
                identityFields = Collections.singletonList(token);
            }
            List<String> identityValues = new ArrayList<>(identityFields.size());
            for (String identityField : identityFields) {
                identityValues.add(probe.claim.has(identityField) ? probe.claim.getString(identityField) : "");
            }
            return StringUtils.join(identityValues, separator);
        }
    }

    private static String processIdentityFieldName(String inputIdentityFiledName) {
        LOGGER.log(Level.FINE, "Start of processIdentityFieldName())");
        // Check if input matches the pattern
        if (inputIdentityFiledName.matches(IDENTITY_FIELD_NAME_PATTERN)) {
            // If input matches, return the input itself
            return inputIdentityFiledName;
        } else {
            // If input does not match, replace special characters with an empty string
            return inputIdentityFiledName.replaceAll("[^a-zA-Z0-9\\-_\\\"]", "");
        }
    }

    /**
     * retrieves the CurrentSigningKey for the JWT Token
     *
     * @param jwtToken
     * @return key based on JwtRsaDigitalSignatureKey
     */
    protected static synchronized JwtRsaDigitalSignatureKey getCurrentSigningKey(JwtToken jwtToken) {

        JwtRsaDigitalSignatureKey result = null;
        long currentTime = System.currentTimeMillis() / 1000;
        long maxKeyTimeInSec = GlobalConjurConfiguration.get().getKeyLifetimeInMinutes() * 60;

        // access via Queue Iterator list
        Iterator<JwtRsaDigitalSignatureKey> iterator = keysQueue.iterator();

        while (iterator.hasNext()) {
            JwtRsaDigitalSignatureKey key = iterator.next();
            if (key != null) {
                if (currentTime - key.getCreationTime() < maxKeyTimeInSec) {

                    if (key.getCreationTime() + maxKeyTimeInSec > jwtToken.claim.getLong("exp")) {
                        result = key;
                        break;
                    }
                } else {
                    LOGGER.log(Level.FINEST, "getCurrentSigningKey() expired key lifetime ");
                    result = null;
                    iterator.remove();// Safe removal using iterator
                }
            } else {
                LOGGER.log(Level.FINEST, "getCurrentSigningKey() Empty key or key without public key ");
                result = null;
                iterator.remove(); // Remove invalid key or key without public key
            }
        }
        if (result == null) {
            String id = ID_FORMAT.format(Instant.now());
            result = new JwtRsaDigitalSignatureKey(id);
            keysQueue.add(result);
        }
        return result;
    }

    /**
     * check for the key creation time is < max_key_time_in_sec,if true then
     * generate new JwkSet
     *
     * @return JwkSet as JSONObject
     */
    protected static synchronized JSONObject getJwkset() {

        JSONObject jwks = new JSONObject();
        JSONArray keys = new JSONArray();

        long currentTime = System.currentTimeMillis() / 1000;
        try {
            long maxKeyTimeInSec = GlobalConjurConfiguration.get().getKeyLifetimeInMinutes() * 60;

            // access via Queue Iterator
            Iterator<JwtRsaDigitalSignatureKey> iterator = keysQueue.iterator();
            while (iterator.hasNext()) {
                JwtRsaDigitalSignatureKey key = iterator.next();
                if (key != null && key.getPublicKey() != null) {
                    if (currentTime - key.getCreationTime() < maxKeyTimeInSec) {
                        JSONObject jwk = new JSONObject();
                        jwk.put("kty", "RSA");
                        jwk.put("alg", AlgorithmIdentifiers.RSA_USING_SHA256);
                        jwk.put("kid", key.getId());
                        jwk.put("use", "sig");
                        jwk.put("key_ops", Collections.singleton("verify"));
                        jwk.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(key.getPublicKey().getModulus().toByteArray()));
                        jwk.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(key.getPublicKey().getPublicExponent().toByteArray()));
                        keys.put(jwk);

                    } else {
                        LOGGER.log(Level.FINEST, "getJwkset() after expire key lifetime ");
                        iterator.remove();// Safe removal using iterator
                    }
                } else {
                    LOGGER.log(Level.FINEST, "getJwkset() Empty key or key without public key ");
                    iterator.remove(); // Remove invalid key or key without public key
                }
            }

            jwks.put("keys", keys);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        }
        return jwks;
    }
}
