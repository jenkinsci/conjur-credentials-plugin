package org.conjur.jenkins.disco;

/**
 * Structured message catalogue for the DisCo Discovery Pipeline.
 *
 * Every log statement and exception message in the disco package uses one of
 * these constants. Each constant owns its unique event code (DISCO_NNN) and its
 * full message template, so the dictionary is the single source of truth for both.
 *
 * Usage:
 * <pre>
 *   import static org.conjur.jenkins.disco.DiscoCode.*;
 *   ...
 *   throw new IOException(INVALID_DISCOVERY_URL.format(platformDiscoveryBaseUrl));
 *   LOGGER.warning(CONTEXT_SCAN_FAILED.format(scopePath));
 *   LOGGER.log(Level.WARNING, JOB_SCAN_FAILED.format(), exception);
 * </pre>
 */
public enum DiscoCode {

    // ── Discovery lifecycle / Platform Discovery service ─────────────────────

    /** Pipeline started — logged once per run. */
    DISCOVERY_STARTED                  ("DISCO_001", "Discovery process started. Trigger: %s"),
    /** CyberArk Identity login succeeded. */
    IDENTITY_LOGIN_SUCCESS             ("DISCO_002", "CyberArk Identity login successful for user: %s"),
    /** Cached bearer token is still valid — skipping re-authentication. */
    IDENTITY_TOKEN_CACHED              ("DISCO_065", "Using cached CyberArk Identity token."),
    /** Pre-flight GET to the Platform Discovery service. */
    DISCOVERY_SERVICE_CALLED           ("DISCO_003", "Calling Platform Discovery Service: %s"),
    /** All endpoints successfully resolved for the tenant subdomain. */
    DISCOVERY_RESOLVED                 ("DISCO_004", "Resolved for subdomain [%s]: tenantId=%s identity=%s snapshotLinks=%s discoveryContext=%s"),
    /** The Platform Discovery base URL cannot be parsed as a valid URL. */
    INVALID_DISCOVERY_URL              ("DISCO_005", "Invalid platform discovery URL: %s"),
    /** Platform Discovery returned an unexpected HTTP status code. */
    DISCOVERY_HTTP_ERROR               ("DISCO_006", "Platform Discovery returned HTTP %d for subdomain: %s"),
    /** Platform Discovery returned an empty body. */
    DISCOVERY_EMPTY_RESPONSE           ("DISCO_007", "Platform Discovery returned empty response"),
    /** Platform Discovery response body is not valid JSON. */
    DISCOVERY_RESPONSE_NOT_JSON        ("DISCO_008", "Platform Discovery response is not valid JSON"),
    /** Platform Discovery response is missing the tenant_id field. */
    DISCOVERY_MISSING_TENANT_ID        ("DISCO_009", "Platform Discovery response missing tenant_id for subdomain: %s"),
    /** Platform Discovery response is missing the identity_administration service entry. */
    DISCOVERY_MISSING_IDENTITY         ("DISCO_010", "Platform Discovery response missing identity_administration endpoint"),
    /** Platform Discovery response is missing the discoverycontext service entry. */
    DISCOVERY_MISSING_DISCOVERYCONTEXT ("DISCO_011", "Platform Discovery response missing discoverycontext endpoint"),
    /** Scheduler-triggered discovery run was initiated. */
    SCHEDULER_TRIGGERED                ("DISCO_012", "Scheduled DisCo discovery triggered."),

    // ── Discovery aborted ─────────────────────────────────────────────────────

    /** A second run was requested while one is already active. */
    ABORTED_ALREADY_RUNNING            ("DISCO_013", "Discovery aborted — process already running."),
    /** Manual trigger blocked because the rate-limit window is still active. */
    ABORTED_RATE_LIMIT                 ("DISCO_014", "Discovery aborted — rate limit active."),
    /** Short status message set on the result object when rate-limited. */
    RATE_LIMIT_ACTIVE                  ("DISCO_015", "Rate limit active."),
    /** Error body returned to the UI when a manual trigger is rate-limited. */
    RATE_LIMIT_ACTIVE_UI               ("DISCO_016", "Rate limit active. Try again later."),

    // ── Configuration invalid ─────────────────────────────────────────────────

    /** DiscoExporterConfiguration bean is null (should never happen in normal operation). */
    CONFIG_NOT_AVAILABLE               ("DISCO_017", "DiscoExporterConfiguration not available."),
    /** Same condition detected in the scheduler — run is skipped. */
    CONFIG_NOT_AVAILABLE_SCHEDULER     ("DISCO_018", "DiscoExporterConfiguration not available, skipping scheduled run."),
    /** Subdomain field is blank — export is blocked. */
    SUBDOMAIN_NOT_CONFIGURED           ("DISCO_019", "subdomain is not configured"),
    /** Subdomain field is blank — scheduler skips the run. */
    SUBDOMAIN_NOT_CONFIGURED_SCHEDULER ("DISCO_020", "Subdomain not configured, skipping scheduled DisCo discovery."),
    /** Subdomain is present but deemed invalid before network calls. */
    SUBDOMAIN_INVALID                  ("DISCO_021", "Configuration invalid: subdomain. Export blocked."),
    /** Platform Discovery returned HTTP 404 for the given subdomain. */
    SUBDOMAIN_NOT_FOUND                ("DISCO_022", "Subdomain not found: %s"),
    /** Error body returned to the UI when subdomain is not configured. */
    SUBDOMAIN_NOT_CONFIGURED_UI        ("DISCO_023", "subdomain is not configured"),

    // ── Credential scan ───────────────────────────────────────────────────────

    /** A credential context (scope) was scanned and N credentials were found. */
    CONTEXT_SCANNED                    ("DISCO_024", "Scanning context: %s. Found %d credentials."),

    // ── UsageTracker ──────────────────────────────────────────────────────────

    /** UsageTracker failed while scanning jobs. */
    JOB_SCAN_FAILED                    ("DISCO_025", "UsageTracker job scan failed"),
    /** UsageTracker failed while scanning folders. */
    FOLDER_SCAN_FAILED                 ("DISCO_026", "UsageTracker folder scan failed"),

    // ── Encryption keys ───────────────────────────────────────────────────────

    /** RSA/ECC public keys fetched; selected kid is logged. */
    KEYS_FETCHED                       ("DISCO_027", "Keys fetched. Selected kid: %s"),

    // ── Export lifecycle ──────────────────────────────────────────────────────

    /** Both pipeline steps completed successfully. */
    EXPORT_SUCCESSFUL                  ("DISCO_028", "Export successful."),
    /** Payload size and SHA-256 digest logged before upload. */
    PAYLOAD_INFO                       ("DISCO_029", "Payload size=%d bytes  sha256=%s"),
    /** Presigned S3 URL obtained from the snapshot-links endpoint. */
    PRESIGNED_URL_OBTAINED             ("DISCO_030", "Presigned URL obtained."),
    /** S3 PUT returned a successful HTTP status code. */
    S3_UPLOAD_SUCCESSFUL               ("DISCO_031", "S3 upload successful (HTTP %d)."),

    // ── Export / network failure ──────────────────────────────────────────────

    /** StartAuthentication returned a non-2xx HTTP status. */
    START_AUTH_HTTP_ERROR              ("DISCO_032", "StartAuthentication failed with HTTP %d"),
    /** StartAuthentication response contained no Result object. */
    START_AUTH_NO_RESULT               ("DISCO_033", "StartAuthentication returned no Result body"),
    /** StartAuthentication response contained no UP (password) mechanism. */
    START_AUTH_NO_UP_MECHANISM         ("DISCO_034", "No password (UP) mechanism in StartAuthentication response"),
    /** AdvanceAuthentication returned a non-2xx HTTP status. */
    ADVANCE_AUTH_HTTP_ERROR            ("DISCO_035", "AdvanceAuthentication failed with HTTP %d"),
    /** AdvanceAuthentication response contained no Token field. */
    ADVANCE_AUTH_NO_TOKEN              ("DISCO_036", "AdvanceAuthentication returned no auth token"),
    /** JWKS endpoint replied with a redirect — bearer token was likely rejected. */
    JWKS_REDIRECT                      ("DISCO_037", "JWKS endpoint redirected (HTTP %d) — bearer token rejected or wrong base URL."),
    /** JWKS fetch returned a non-2xx status. */
    JWKS_FETCH_FAILED                  ("DISCO_038", "Failed to fetch keys from DisCo, status: %d %s"),
    /** encryptValue() was called before fetchLatestKeys(). */
    NO_PUBLIC_KEY_LOADED               ("DISCO_039", "No public key loaded. Call fetchLatestKeys() first."),
    /** JWKS response contained no parseable keys. */
    NO_VALID_KEYS                      ("DISCO_040", "No valid public keys returned by DisCo"),
    /** Key entry in JWKS response uses an unsupported format. */
    UNSUPPORTED_KEY_FORMAT             ("DISCO_041", "Unsupported key format in DisCo response"),
    /** Export step failed (wraps the ExportException message). */
    EXPORT_FAILED                      ("DISCO_042", "Export failed: %s"),
    /** Unexpected exception in the discovery pipeline (wraps the exception message). */
    PIPELINE_FAILED                    ("DISCO_043", "Discovery pipeline failed: %s"),
    /** snapshot-links endpoint rejected the bearer token (HTTP 401/403). */
    SNAPSHOT_LINKS_AUTH_REJECTED       ("DISCO_044", "snapshot-links rejected HTTP %d — check bearer token / credentials."),
    /** snapshot-links endpoint returned an unexpected HTTP status. */
    SNAPSHOT_LINKS_HTTP_ERROR          ("DISCO_045", "snapshot-links returned HTTP %d"),
    /** snapshot-links response body exceeds the safety limit — possible spoofing. */
    SNAPSHOT_LINKS_RESPONSE_TOO_LARGE  ("DISCO_046", "snapshot-links response too large — possible spoofing."),
    /** snapshot-links response JSON is missing the 'url' field. */
    SNAPSHOT_LINKS_MISSING_URL         ("DISCO_047", "snapshot-links response missing 'url' field."),
    /** Network I/O failure on the snapshot-links call. */
    SNAPSHOT_LINKS_IO_FAILURE          ("DISCO_048", "snapshot-links I/O failure: %s"),
    /** S3 PUT rejected the payload as too large (HTTP 413). */
    S3_PUT_PAYLOAD_TOO_LARGE           ("DISCO_049", "Payload rejected — too large (HTTP 413)."),
    /** S3 PUT returned an unexpected HTTP status. */
    S3_PUT_HTTP_ERROR                  ("DISCO_050", "S3 PUT failed with HTTP %d"),
    /** Network I/O failure on the S3 PUT call. */
    S3_PUT_IO_FAILURE                  ("DISCO_051", "S3 PUT I/O failure: %s"),
    /** SHA-256 computation failed (should never happen on standard JREs). */
    SHA256_FAILED                      ("DISCO_052", "SHA-256 computation failed: %s"),
    /** Hex-to-base64 conversion failed. */
    HEX_TO_BASE64_FAILED               ("DISCO_053", "hex-to-base64 conversion failed: %s"),

    // ── Auth configuration ────────────────────────────────────────────────────

    /** Username or password credential could not be resolved from the Jenkins store. */
    CREDENTIALS_NOT_RESOLVED           ("DISCO_054", "Could not resolve DisCo credentials — check auth mode configuration."),
    /** JWKS data retrieval from the local Jenkins endpoint failed (non-fatal). */
    JWKS_DATA_FAILED                   ("DISCO_055", "Could not retrieve JWKS data: %s"),

    // ── Access denied / rate limited ──────────────────────────────────────────

    /** Platform Discovery returned HTTP 403 for the given subdomain. */
    PLATFORM_DISCOVERY_ACCESS_DENIED   ("DISCO_056", "Access denied by Platform Discovery for subdomain '%s'."),
    /** snapshot-links endpoint returned HTTP 429 (remote rate limit). */
    SNAPSHOT_LINKS_RATE_LIMITED        ("DISCO_057", "Rate limit on snapshot-links. Retry-After: %ss"),

    // ── Credential context scan ───────────────────────────────────────────────

    /** Exception while scanning a credential context (ItemGroup). */
    CONTEXT_SCAN_FAILED                ("DISCO_058", "Failed scanning context %s"),
    /** Exception while scanning a credential context (Item). */
    ITEM_CONTEXT_SCAN_FAILED           ("DISCO_059", "Failed scanning item context %s"),

    // ── Credential field reflection ───────────────────────────────────────────

    /** Reflection could not access a specific field on a credential object. */
    FIELD_ACCESS_FAILED                ("DISCO_060", "Cannot access field %s"),
    /** General reflection failure while reading a credential's fields. */
    CREDENTIAL_RETRIEVAL_FAILED        ("DISCO_061", "Failed to retrieve credential %s: %s"),

    /** DisCo own authentication credential skipped to prevent circular exfiltration (C4). */
    DISCO_AUTH_CREDENTIAL_SKIPPED      ("DISCO_062", "Skipping DisCo authentication credential from scan to prevent circular exfiltration: %s"),
    /** Subdomain tenant binding changed by an administrator (M3). */
    SUBDOMAIN_CHANGED                  ("DISCO_063", "Subdomain tenant binding changed by administrator: [%s] -> [%s]. Verify the target tenant is correct."),
    /** Resolved service host is outside the trusted domain suffix (H4). */
    DISCOVERY_UNTRUSTED_HOST           ("DISCO_064", "Resolved host [%s] is not within trusted domain [%s]. Aborting."),

    // ── UsageTracker per-item failures ────────────────────────────────────────

    /** UsageTracker failed while scanning a single job (scan continues for remaining jobs). */
    JOB_ITEM_SCAN_FAILED               ("DISCO_066", "UsageTracker failed scanning job: %s"),
    /** UsageTracker could not read the pipeline script for a specific job. */
    PIPELINE_SCRIPT_READ_FAILED        ("DISCO_067", "Could not read pipeline script for job: %s"),
    /** UsageTracker failed while scanning a single folder (scan continues for remaining folders). */
    FOLDER_ITEM_SCAN_FAILED            ("DISCO_068", "UsageTracker failed scanning folder: %s");

    // ── Infrastructure ────────────────────────────────────────────────────────

    private final String code;
    private final String template;

    DiscoCode(String code, String template) {
        this.code = code;
        this.template = template;
    }

    /** Returns the numeric code string, e.g. {@code "DISCO_044"}. */
    public String getCode() { return code; }

    /** Returns the raw message template (with {@code %s} / {@code %d} placeholders). */
    public String getTemplate() { return template; }

    /**
     * Returns the fully-formatted message: {@code "DISCO_NNN: <message>"}.
     *
     * Pass format arguments when the template contains {@code %s} / {@code %d} placeholders.
     * Omit arguments for static (no-placeholder) messages.
     *
     * @param args optional {@link String#format} arguments
     */
    public String format(Object... args) {
        return code + ": " + (args.length == 0 ? template : String.format(template, args));
    }

    /** Returns just the code string, e.g. {@code "DISCO_044"}. */
    @Override
    public String toString() {
        return code;
    }
}
