package org.conjur.jenkins;

/**
 * Structured message catalogue for the Conjur Credentials Jenkins Plugin.
 *
 * Every SEVERE/WARNING log statement and exception message outside the disco package
 * uses one of these constants. Each constant owns its unique event code (CJPL_NNN) and
 * its full message template, so the dictionary is the single source of truth for both.
 *
 * Usage:
 * <pre>
 *   import static org.conjur.jenkins.CjplCode.*;
 *   ...
 *   throw new IOException(CONJUR_SECRET_FETCH_ERROR.format(response.code(), response.message(), body));
 *   LOGGER.log(Level.SEVERE, MISSING_CONJUR_CONFIG.format());
 *   LOGGER.log(Level.SEVERE, AUTH_FAILED_FOR_CONTEXT.format(context.getDisplayName()));
 * </pre>
 */
public enum CjplCode {

    // ── Configuration validation ───────────────────────────────────────────────

    /** Conjur configuration is missing entirely. */
    MISSING_CONJUR_CONFIG               ("CJPL_001", "Missing configuration for Conjur Plugin"),
    /** Account field is blank in the resolved configuration. */
    MISSING_ACCOUNT_FIELD               ("CJPL_002", "Conjur Plugin missing Account field to be configured"),
    /** Appliance URL field is blank in the resolved configuration. */
    MISSING_APPLIANCE_URL               ("CJPL_003", "Conjur Plugin require ConjurURL field to be configured"),
    /** API key mode selected but no credential ID is set. */
    MISSING_APIKEY_CREDENTIALS          ("CJPL_004", "Credentials not set for APIKey authenticator"),
    /** Certificate ID is null when resolving TLS configuration. */
    CERTIFICATE_ID_NULL                 ("CJPL_005", "CertificationID is null"),
    /** Auth web service ID is blank. */
    AUTH_WEB_SERVICE_ID_EMPTY           ("CJPL_006", "Auth WebService Id should not be empty"),
    /** GlobalConjurConfiguration could not be retrieved. */
    GLOBAL_CONFIG_RETRIEVAL_FAILED      ("CJPL_007", "Failed to retrieve GlobalConjurConfiguration"),
    /** variableId field is required but was not set. */
    VARIABLE_ID_REQUIRED                ("CJPL_008", "FAILED variableId field is required"),
    /** Username and credentialID fields are required but were not set. */
    USERNAME_CREDENTIAL_ID_REQUIRED     ("CJPL_009", "FAILED username,credentialID fields is required"),
    /** Username, passphrase, and credentialID fields are required but were not set (SSH key). */
    USERNAME_PASSPHRASE_CREDENTIAL_REQUIRED ("CJPL_010", "FAILED username,passphrase,credentialID fields is required"),
    /** All Docker certificate fields (clientKey, clientCert, caCert) are required. */
    DOCKER_CERT_FIELDS_REQUIRED         ("CJPL_011", "All certificate fields are required"),

    // ── HTTP / TLS setup ──────────────────────────────────────────────────────

    /** TLS/certificate configuration failed while building the HTTP client. */
    CERT_CONFIG_ERROR                   ("CJPL_012", "Error configuring server certificates."),
    /** Job path extracted from the referer URL is not a valid /job/ path. */
    INVALID_JOB_PATH                    ("CJPL_013", "Invalid job path: %s"),

    // ── Conjur API authentication ─────────────────────────────────────────────

    /** Authentication info could not be assembled for the given context. */
    AUTHN_INFO_FAILED                   ("CJPL_014", "Cannot generate AuthnInfo. Exception: %s"),
    /** Authentication succeeded but could not obtain a token (general). */
    AUTH_FAILED_FOR_CONTEXT             ("CJPL_015", "Authentication failed. Cannot get token from Conjur for context: %s"),
    /** SSL peer verification failed during authentication. */
    AUTH_SSL_PEER_UNVERIFIED            ("CJPL_016", "Cannot get authentication token from Conjur. SSL Peer Unverified url: %s"),
    /** Unexpected exception during authentication. */
    AUTH_EXCEPTION                      ("CJPL_017", "Cannot get authentication token from Conjur. Exception: %s"),
    /** HTTP call could not be created for API key authentication. */
    APIKEY_HTTP_CALL_FAILED             ("CJPL_018", "Cannot create http call. Authentication failed."),
    /** HTTP call could not be created for JWT authentication. */
    JWT_HTTP_CALL_FAILED                ("CJPL_019", "Cannot create http call. JWTAuthentication failed."),
    /** Number of username/password credentials found for a given ID (debug). */
    APIKEY_CREDENTIALS_COUNT            ("CJPL_020", "UsernamePasswordCredentials found %d for ID %s"),

    // ── Conjur secret fetch ────────────────────────────────────────────────────

    /** Secret fetch returned a non-2xx response code. */
    CONJUR_SECRET_FETCH_ERROR           ("CJPL_021", "Error fetching secret from Conjur [%d - %s] %s"),
    /** Variable batch fetch returned a non-2xx response code. */
    CONJUR_VARIABLE_FETCH_ERROR         ("CJPL_022", "Error fetching variables from Conjur [%d - %s] : %s"),
    /** Variable fetch failed with no additional context. */
    CONJUR_VARIABLE_FETCH_FAILED        ("CJPL_023", "Error fetching variables from Conjur"),
    /** Secret retrieval failed with an exception. */
    SECRET_RETRIEVAL_FAILED             ("CJPL_024", "FAILED to retrieve secret! Exception: %s"),
    /** Secret retrieval failed with no exception detail. */
    SECRET_RETRIEVAL_FAILED_PLAIN       ("CJPL_025", "FAILED to retrieve secret!"),
    /** Secret retrieval failed for a specific variableId. */
    SECRET_RETRIEVAL_FAILED_VAR         ("CJPL_026", "Can't retrieve secret for variableId: %s"),
    /** Secret validated and retrieved successfully. */
    SECRET_RETRIEVAL_SUCCESS            ("CJPL_027", "Successfully retrieved secret string"),
    /** Secret fetch failed with detailed error and config hint. */
    SECRET_RETRIEVAL_FAILED_DETAIL      ("CJPL_028", "FAILED to retrieve secret: \n%s\nPlease check Conjur configuration or add credentials from credentials page"),

    // ── Credential binding / lookup ───────────────────────────────────────────

    /** No Conjur credential was found for the current build context. */
    NO_CREDENTIALS_FOR_BUILD            ("CJPL_029", "No credentials found for: %s"),
    /** Credential ID not found in the Jenkins store. */
    CREDENTIAL_ID_NOT_FOUND             ("CJPL_030", "Could not find credentials entry with ID '%s'"),
    /** Credential found but is of the wrong type. */
    CREDENTIAL_WRONG_TYPE               ("CJPL_031", "Credentials '%s' not found '%s' where '%s' was expected"),

    // ── Credential provider / store ───────────────────────────────────────────

    /** Getting credentials from the provider failed with an exception. */
    CREDENTIALS_PROVIDER_FAILED         ("CJPL_032", "Getting credentials failed. Exception: %s"),
    /** A problem occurred with the credential store keyed by object hash. */
    CREDENTIAL_STORE_PROBLEM            ("CJPL_033", "There is a problem with Storage: %s"),
    /** Credentials supplier returned an exception. */
    CREDENTIALS_SUPPLIER_EXCEPTION      ("CJPL_034", "EXCEPTION: ConjurCredentialsSupplier: returned %s"),
    /** A temp file created for secret file credentials could not be deleted. */
    TEMP_FILE_DELETE_FAILED             ("CJPL_035", "Can't delete temp file: %s"),

    // ── JWT / JWKS ────────────────────────────────────────────────────────────

    /** JWT token signing failed. */
    JWT_SIGN_FAILED                     ("CJPL_036", "Failed to sign JWT token: %s"),
    /** JWT token cannot be generated because context is null. */
    JWT_NULL_CONTEXT                    ("CJPL_037", "Cannot get token for null context!"),
    /** JWT token cannot be generated because global config is not set. */
    JWT_NO_GLOBAL_CONFIG                ("CJPL_038", "Cannot get token because globalConfig is not set"),
    /** JWKS endpoint request method not supported. */
    JWKS_METHOD_NOT_SUPPORTED           ("CJPL_039", "conjur-jwk-set"),

    // ── Telemetry / plugin version ────────────────────────────────────────────

    /** Could not locate the class resource to read the manifest. */
    MANIFEST_RESOURCE_NOT_FOUND         ("CJPL_040", "Could not locate class resource to find manifest."),
    /** Plugin version read from the manifest. */
    PLUGIN_VERSION_FROM_MANIFEST        ("CJPL_041", "Plugin version from manifest: %s"),
    /** Could not read Plugin-Version from the manifest. */
    MANIFEST_VERSION_READ_FAILED        ("CJPL_042", "Could not read Plugin-Version from manifest: %s");

    // ── Infrastructure ────────────────────────────────────────────────────────

    private final String code;
    private final String template;

    CjplCode(String code, String template) {
        this.code = code;
        this.template = template;
    }

    /** Returns the numeric code string, e.g. {@code "CJPL_021"}. */
    public String getCode() { return code; }

    /** Returns the raw message template (with {@code %s} / {@code %d} placeholders). */
    public String getTemplate() { return template; }

    /**
     * Returns the fully-formatted message: {@code "CJPL_NNN: <message>"}.
     *
     * Pass format arguments when the template contains {@code %s} / {@code %d} placeholders.
     * Omit arguments for static (no-placeholder) messages.
     *
     * @param args optional {@link String#format} arguments
     */
    public String format(Object... args) {
        return code + ": " + (args.length == 0 ? template : String.format(template, args));
    }

    /** Returns just the code string, e.g. {@code "CJPL_021"}. */
    @Override
    public String toString() {
        return code;
    }
}
