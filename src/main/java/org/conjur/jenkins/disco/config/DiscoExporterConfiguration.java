package org.conjur.jenkins.disco.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.disco.discovery.DiscoveryOrchestrator;
import org.conjur.jenkins.disco.model.DiscoveryRunResult;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import org.conjur.jenkins.disco.DiscoCode;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.conjur.jenkins.disco.DiscoCode.*;

@Extension
public class DiscoExporterConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DiscoExporterConfiguration.class.getName());

    /** @deprecated Use {@link DiscoEnvironment#resolve()} — kept only for binary compatibility. */
    @Deprecated
    public static final String DISCOVERY_BASE_URL = DiscoEnvironment.PRODUCTION.getBaseUrl();
    private static final Pattern SUBDOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");
    public static final int MIN_EXPORT_INTERVAL_HOURS = 1;
    public static final int MAX_EXPORT_INTERVAL_HOURS = 24;
    public static final int DEFAULT_EXPORT_INTERVAL_HOURS = 12;

    /**
     * How DisCo authenticates the export request.
     *
     * USERNAME_PASSWORD  — a single UsernamePasswordCredentials entry.
     * TWO_SECRETS        — two separate StringCredentials: one for username, one for password.
     */
    public enum AuthMode { USERNAME_PASSWORD, TWO_SECRETS }

    // Connection
    private String subdomain = "";

    // Auth — shared
    private AuthMode authMode = AuthMode.USERNAME_PASSWORD;

    // Auth — USERNAME_PASSWORD mode
    private String conjurCredentialId = "";

    // Auth — TWO_SECRETS mode
    private String discoUsernameCredentialId = "";
    private String discoPasswordCredentialId = "";

    // Schedule
    private int exportIntervalHours = DEFAULT_EXPORT_INTERVAL_HOURS;

    // Behaviour
    private boolean exportSecretValues = true;
    private long lastExportTimestamp = 0L;

    public DiscoExporterConfiguration() {
        load();
    }

    public static DiscoExporterConfiguration get() {
        return GlobalConfiguration.all().get(DiscoExporterConfiguration.class);
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getSubdomain() { return subdomain; }
    @DataBoundSetter public void setSubdomain(String v) {
        if (!subdomain.equals(v) && !subdomain.isBlank() && v != null && !v.isBlank()) {
            LOGGER.severe(SUBDOMAIN_CHANGED.format(subdomain, v));
        }
        subdomain = v;
        save();
    }

    /**
     * Returns the Conjur appliance URL from the global ConjurConfiguration.
     * DisCo does not own this value — it is configured on the main Conjur settings page.
     */
    public String getConjurUrl() {
        try {
            GlobalConjurConfiguration global = GlobalConjurConfiguration.get();
            if (global != null && global.getConjurConfiguration() != null) {
                return global.getConjurConfiguration().getApplianceURL();
            }
        } catch (Exception e) {
            LOGGER.warning("Could not read Conjur URL from GlobalConjurConfiguration: " + e.getMessage());
        }
        return "";
    }

    public AuthMode getAuthMode() { return authMode; }
    @DataBoundSetter public void setAuthMode(AuthMode v) { authMode = v; save(); }

    public String getConjurCredentialId() { return conjurCredentialId; }
    @DataBoundSetter public void setConjurCredentialId(String v) { conjurCredentialId = v; save(); }

    public String getDiscoUsernameCredentialId() { return discoUsernameCredentialId; }
    @DataBoundSetter public void setDiscoUsernameCredentialId(String v) { discoUsernameCredentialId = v; save(); }

    public String getDiscoPasswordCredentialId() { return discoPasswordCredentialId; }
    @DataBoundSetter public void setDiscoPasswordCredentialId(String v) { discoPasswordCredentialId = v; save(); }

    public int getExportIntervalHours() { return exportIntervalHours; }
    @DataBoundSetter public void setExportIntervalHours(int v) {
        exportIntervalHours = Math.max(MIN_EXPORT_INTERVAL_HOURS, Math.min(MAX_EXPORT_INTERVAL_HOURS, v)); save();
    }

    public boolean isExportSecretValues() { return exportSecretValues; }
    @DataBoundSetter public void setExportSecretValues(boolean v) { exportSecretValues = v; save(); }

    /** Derived from the active DisCo environment — true when the env var points to a non-production environment. */
    public boolean isTestEnvironment() { return !DiscoEnvironment.resolve().isProduction(); }

    public long getLastExportTimestamp() { return lastExportTimestamp; }
    public void setLastExportTimestamp(long v) { lastExportTimestamp = v; save(); }

    /**
     * Returns the Platform Discovery API URL for the active environment.
     * Determined by the {@code CYBERARK_DISCO_ENV} environment variable;
     * defaults to Production when the variable is absent or unrecognised.
     */
    public String getPlatformDiscoveryUrl() { return DiscoEnvironment.resolve().getPlatformDiscoveryUrl(); }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    public boolean isRateLimitActive() {
        if (isTestEnvironment()) return false;
        return (System.currentTimeMillis() - lastExportTimestamp) < 3_600_000L;
    }

    public String getJwksUri() {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl == null) return "";
        return rootUrl + "jwtauth/conjur-jwk-set";
    }

    public long getMillisUntilNextRun() {
        if (lastExportTimestamp == 0) return 0;
        long elapsed = System.currentTimeMillis() - lastExportTimestamp;
        return Math.max(0, (long) exportIntervalHours * 3_600_000L - elapsed);
    }

    /**
     * Resolves the effective username for the current auth mode.
     * Returns null if the credential cannot be found.
     */
    public String resolveUsername() {
        if (authMode == AuthMode.USERNAME_PASSWORD) {
            StandardCredentials c = lookupById(conjurCredentialId);
            if (c instanceof StandardUsernamePasswordCredentials up) return up.getUsername();
            return null;
        }
        // TWO_SECRETS — username secret contains the username string as the secret value
        StandardCredentials c = lookupById(discoUsernameCredentialId);
        if (c instanceof StringCredentials sc) return sc.getSecret().getPlainText();
        return null;
    }

    /**
     * Resolves the effective password for the current auth mode.
     * Returns null if the credential cannot be found.
     */
    public String resolvePassword() {
        if (authMode == AuthMode.USERNAME_PASSWORD) {
            StandardCredentials c = lookupById(conjurCredentialId);
            if (c instanceof StandardUsernamePasswordCredentials up) return up.getPassword().getPlainText();
            return null;
        }
        StandardCredentials c = lookupById(discoPasswordCredentialId);
        if (c instanceof StringCredentials sc) return sc.getSecret().getPlainText();
        return null;
    }

    /**
     * Resolves the effective password as a UTF-8 byte array.
     * Callers must zero the array with {@code Arrays.fill(bytes, (byte) 0)} after use.
     * Returns null if the credential cannot be found.
     */
    public byte[] resolvePasswordBytes() {
        String plain = resolvePassword();
        if (plain == null) return null;
        return plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Stapler actions
    // -------------------------------------------------------------------------

    @POST
    public void doLaunchNow(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (StringUtils.isBlank(subdomain)) {
            rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            rsp.getWriter().write("{\"error\":\"" + SUBDOMAIN_NOT_CONFIGURED_UI.format() + "\"}");
            return;
        }
        if (isRateLimitActive()) {
            rsp.setStatus(429);
            rsp.getWriter().write("{\"error\":\"" + RATE_LIMIT_ACTIVE_UI.format() + "\"}");
            return;
        }

        DiscoveryOrchestrator.getInstance().runAsync(DiscoveryOrchestrator.TriggerType.MANUAL);
        rsp.setStatus(HttpServletResponse.SC_ACCEPTED);
        rsp.getWriter().write("{\"status\":\"STARTED\"}");
    }

    public void doProgress(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        DiscoveryRunResult result = DiscoveryOrchestrator.getInstance().getCurrentResult();
        rsp.setContentType("application/json");
        rsp.getWriter().write(new com.google.gson.Gson().toJson(result));
    }

    // -------------------------------------------------------------------------
    // Form validation
    // -------------------------------------------------------------------------

    public FormValidation doCheckSubdomain(@QueryParameter String subdomain) {
        if (StringUtils.isBlank(subdomain)) return FormValidation.error("Subdomain is required");
        if (!SUBDOMAIN_PATTERN.matcher(subdomain).matches())
            return FormValidation.error("Subdomain must match ^[a-zA-Z0-9-]+$");
        return FormValidation.ok();
    }

    public FormValidation doCheckExportIntervalHours(@QueryParameter int exportIntervalHours) {
        if (exportIntervalHours < MIN_EXPORT_INTERVAL_HOURS || exportIntervalHours > MAX_EXPORT_INTERVAL_HOURS)
            return FormValidation.error("Interval must be between " + MIN_EXPORT_INTERVAL_HOURS + " and " + MAX_EXPORT_INTERVAL_HOURS + " hours");
        return FormValidation.ok();
    }

    public FormValidation doCheckConjurCredentialId(@QueryParameter String conjurCredentialId) {
        if (StringUtils.isBlank(conjurCredentialId))
            return FormValidation.warning("A credential is required for authenticated export");
        return FormValidation.ok();
    }

    public FormValidation doCheckDiscoUsernameCredentialId(@QueryParameter String discoUsernameCredentialId) {
        if (StringUtils.isBlank(discoUsernameCredentialId))
            return FormValidation.warning("Username secret credential is required");
        return FormValidation.ok();
    }

    public FormValidation doCheckDiscoPasswordCredentialId(@QueryParameter String discoPasswordCredentialId) {
        if (StringUtils.isBlank(discoPasswordCredentialId))
            return FormValidation.warning("Password secret credential is required");
        return FormValidation.ok();
    }

    // -------------------------------------------------------------------------
    // Dropdown fillers
    // -------------------------------------------------------------------------

    public ListBoxModel doFillConjurCredentialIdItems(@QueryParameter String conjurCredentialId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(ACL.SYSTEM, Jenkins.get(),
                        StandardUsernamePasswordCredentials.class,
                        Collections.emptyList(), CredentialsMatchers.always())
                .includeCurrentValue(conjurCredentialId);
    }

    public ListBoxModel doFillDiscoUsernameCredentialIdItems(@QueryParameter String discoUsernameCredentialId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(ACL.SYSTEM, Jenkins.get(),
                        StringCredentials.class,
                        Collections.emptyList(), CredentialsMatchers.always())
                .includeCurrentValue(discoUsernameCredentialId);
    }

    public ListBoxModel doFillDiscoPasswordCredentialIdItems(@QueryParameter String discoPasswordCredentialId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(ACL.SYSTEM, Jenkins.get(),
                        StringCredentials.class,
                        Collections.emptyList(), CredentialsMatchers.always())
                .includeCurrentValue(discoPasswordCredentialId);
    }

    public ListBoxModel doFillAuthModeItems() {
        ListBoxModel m = new ListBoxModel();
        m.add("Username + Password credential", AuthMode.USERNAME_PASSWORD.name());
        m.add("Two separate Secret Text credentials", AuthMode.TWO_SECRETS.name());
        return m;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        // authMode arrives as a string — convert before binding
        if (json.containsKey("authMode")) {
            try {
                authMode = AuthMode.valueOf(json.getString("authMode"));
            } catch (IllegalArgumentException ignored) {}
        }
        req.bindJSON(this, json);
        save();
        return true;
    }

    // -------------------------------------------------------------------------

    private StandardCredentials lookupById(String id) {
        if (StringUtils.isBlank(id)) return null;
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.withId(id));
    }
}
