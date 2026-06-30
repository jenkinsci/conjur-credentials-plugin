package org.conjur.jenkins.disco.discovery;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.conjur.jenkins.disco.config.DiscoExporterConfiguration;
import org.conjur.jenkins.disco.model.CredentialRecord;
import org.conjur.jenkins.disco.security.EncryptionService;

import org.conjur.jenkins.disco.DiscoCode;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Traverses the full Jenkins hierarchy (global → folders → jobs) and maps
 * each credential to a CredentialRecord via reflection and AnnotationMapper.
 */
public class CredentialsDictionaryMapper {

    private static final Logger LOGGER = Logger.getLogger(CredentialsDictionaryMapper.class.getName());

    private final DiscoExporterConfiguration config;
    private final EncryptionService encryptionService;
    private final UsageTracker usageTracker;

    // De-duplication key: credentialId only — lookupCredentials() propagates inherited
    // credentials into every child scope, so keying by (id, scope) would produce one
    // record per visible scope. We want exactly one record per credential, placed in
    // the highest scope where it is first encountered (Global before folders before jobs).
    private final Set<String> seen = new LinkedHashSet<>();

    // Fields already present as top-level CredentialRecord properties — skip from
    // the reflected fields map and encrypted blob to avoid redundant data.
    private static final Set<String> SKIP_FIELDS = Set.of("id", "scope", "description");

    public CredentialsDictionaryMapper(DiscoExporterConfiguration config,
                                       EncryptionService encryptionService,
                                       UsageTracker usageTracker) {
        this.config = config;
        this.encryptionService = encryptionService;
        this.usageTracker = usageTracker;
    }

    public List<CredentialRecord> mapAll() {
        List<CredentialRecord> records = new ArrayList<>();

        Set<String> discoAuthIds = new java.util.HashSet<>();
        if (config.getConjurCredentialId() != null) discoAuthIds.add(config.getConjurCredentialId());
        if (config.getDiscoUsernameCredentialId() != null) discoAuthIds.add(config.getDiscoUsernameCredentialId());
        if (config.getDiscoPasswordCredentialId() != null) discoAuthIds.add(config.getDiscoPasswordCredentialId());

        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            // Global scope — Jenkins itself is an ItemGroup
            records.addAll(mapItemGroup(globalContext(), "Global", discoAuthIds));

            // Folder scopes — AbstractFolder implements both ItemGroup and Item;
            // use the ItemGroup overload to get credentials stored at folder level
            for (AbstractFolder<?> folder : foldersToScan()) {
                records.addAll(mapItemGroup(folder, folder.getFullName(), discoAuthIds));
            }

            // Job/pipeline scopes
            for (Job<?, ?> job : jobsToScan()) {
                records.addAll(mapItem(job, job.getFullName(), discoAuthIds));
            }
        }

        return records;
    }

    /** Returns the global ItemGroup; overridden in tests to avoid Jenkins.get(). */
    protected ItemGroup<?> globalContext() {
        return Jenkins.get();
    }

    /** Returns the folders to scan; overridden in tests. */
    @SuppressWarnings("unchecked")
    protected List<AbstractFolder<?>> foldersToScan() {
        return (List<AbstractFolder<?>>) (List<?>) Jenkins.get().getAllItems(AbstractFolder.class);
    }

    /** Returns the jobs to scan; overridden in tests. */
    @SuppressWarnings("unchecked")
    protected List<Job<?, ?>> jobsToScan() {
        return (List<Job<?, ?>>) (List<?>) Jenkins.get().getAllItems(Job.class);
    }

    /** Fetches credentials for an ItemGroup; overridden in tests. */
    protected List<StandardCredentials> credentialsFor(ItemGroup<?> context) {
        return CredentialsProvider.lookupCredentialsInItemGroup(
                StandardCredentials.class, context, ACL.SYSTEM2, Collections.emptyList());
    }

    /** Fetches credentials for an Item; overridden in tests. */
    protected List<StandardCredentials> credentialsFor(Item context) {
        return CredentialsProvider.lookupCredentialsInItem(
                StandardCredentials.class, context, ACL.SYSTEM2, Collections.emptyList());
    }

    private List<CredentialRecord> mapItemGroup(ItemGroup<?> context, String scopePath,
                                                Set<String> discoAuthIds) {
        List<CredentialRecord> records = new ArrayList<>();
        try {
            // AbstractFolder implements both ItemGroup and Item. FolderCredentialsProvider
            // only vends folder-local credentials through the Item overload of
            // lookupCredentials — calling the ItemGroup overload misses them entirely.
            List<StandardCredentials> creds = (context instanceof Item item)
                    ? credentialsFor(item)
                    : credentialsFor(context);
            LOGGER.info(CONTEXT_SCANNED.format(scopePath, creds.size()));

            for (StandardCredentials cred : creds) {
                if (discoAuthIds.contains(cred.getId())) {
                    LOGGER.fine(DISCO_AUTH_CREDENTIAL_SKIPPED.format(cred.getId()));
                    continue;
                }
                if (!seen.add(cred.getId())) continue;
                records.add(mapCredential(cred, scopePath, context));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, CONTEXT_SCAN_FAILED.format(scopePath), e);
        }
        return records;
    }

    private List<CredentialRecord> mapItem(Item context, String scopePath,
                                           Set<String> discoAuthIds) {
        List<CredentialRecord> records = new ArrayList<>();
        try {
            List<StandardCredentials> creds = credentialsFor(context);
            for (StandardCredentials cred : creds) {
                if (discoAuthIds.contains(cred.getId())) {
                    LOGGER.fine(DISCO_AUTH_CREDENTIAL_SKIPPED.format(cred.getId()));
                    continue;
                }
                if (!seen.add(cred.getId())) continue;
                records.add(mapCredentialFromItem(cred, scopePath));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, ITEM_CONTEXT_SCAN_FAILED.format(scopePath), e);
        }
        return records;
    }

    private CredentialRecord mapCredential(StandardCredentials cred, String scopePath,
                                           ItemGroup<?> context) {
        CredentialRecord record = buildBaseRecord(cred, scopePath);
        record.setAdditionalData(buildAdditionalData(context, scopePath));
        populateFieldMaps(record, cred);
        return record;
    }

    private CredentialRecord mapCredentialFromItem(StandardCredentials cred, String scopePath) {
        CredentialRecord record = buildBaseRecord(cred, scopePath);
        Map<String, String> additional = new LinkedHashMap<>();
        additional.put("storeProvider", "JobCredentialsStore");
        additional.put("storeProviderVersion", resolvePluginVersion("credentials"));
        additional.put("scope", "job");
        additional.put("scopePath", scopePath);
        record.setAdditionalData(additional);
        populateFieldMaps(record, cred);
        return record;
    }

    private CredentialRecord buildBaseRecord(StandardCredentials cred, String scopePath) {
        CredentialRecord record = new CredentialRecord();
        record.setCredentialId(cred.getId());
        record.setName(cred.getId());
        record.setTypeDisplayName(resolveTypeDisplayName(cred));
        record.setOriginId(scopePath + ":" + cred.getId());
        record.setType(cred.getClass().getName());
        record.setLocation(scopePath);
        record.setDescription(cred.getDescription());
        record.setConjurization(
                cred instanceof ConjurSecretCredentials
                        ? AnnotationMapper.map(cred)
                        : null);
        record.setLevelUpdatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        record.setWhereUsed(usageTracker.getWhereUsed(cred.getId()));
        record.setInheritancePath(buildInheritancePath(cred.getClass()));
        record.setCreatedAt(reflectTimestamp(cred, "getCreatedTime"));
        record.setUpdatedAt(reflectTimestamp(cred, "getUpdatedTime"));
        return record;
    }

    /**
     * Builds a comma-separated inheritance chain from the given class up to (but not including)
     * AbstractItem, stopping at Object if AbstractItem is not in the hierarchy.
     */
    static String buildInheritancePath(Class<?> clazz) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals("hudson.model.AbstractItem")) break;
            if (sb.length() > 0) sb.append(',');
            sb.append(c.getName());
        }
        return sb.toString();
    }

    private void populateFieldMaps(CredentialRecord record, StandardCredentials cred) {
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, Object> rawValues = new LinkedHashMap<>();
        List<String> valuesWithError = new ArrayList<>();

        boolean isExternalProvider = isExternalProvider(record);

        try {
            for (Field field : getAllFields(cred.getClass())) {
                String name = field.getName();
                if (name.startsWith("$") || name.equals("serialVersionUID") || SKIP_FIELDS.contains(name)) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(cred);
                    fields.put(name, field.getType().getName());

                    if (!isExternalProvider && config.isExportSecretValues()) {
                        if (value instanceof Secret secret) {
                            try {
                                rawValues.put(name, encryptionService.encryptValue(secret.getPlainText()));
                            } catch (Exception ex) {
                                valuesWithError.add(name);
                                rawValues.put(name, null);
                            }
                        } else if (value != null) {
                            rawValues.put(name, String.valueOf(value));
                        } else {
                            rawValues.put(name, null);
                        }
                    }
                } catch (IllegalAccessException ex) {
                    LOGGER.log(Level.FINEST, FIELD_ACCESS_FAILED.format(name), ex);
                }
            }
        } catch (Exception e) {
            record.setError(sanitizeErrorMessage(e));
            LOGGER.warning(CREDENTIAL_RETRIEVAL_FAILED.format(cred.getId(), e.getMessage()));
        }

        record.setFields(fields);

        if (!isExternalProvider && config.isExportSecretValues() && !rawValues.isEmpty()) {
            try {
                record.setValues(encryptionService.encryptValue(
                        new com.google.gson.Gson().toJson(rawValues)));
            } catch (Exception e) {
                record.setError(sanitizeErrorMessage(e));
            }
        }
        if (!valuesWithError.isEmpty()) {
            record.setValuesWithError(valuesWithError);
        }
    }

    private Map<String, String> buildAdditionalData(ItemGroup<?> context, String scopePath) {
        Map<String, String> data = new LinkedHashMap<>();
        boolean isGlobal = context instanceof Jenkins;
        String provider = isGlobal ? "SystemCredentialsProvider" : "FolderCredentialsProvider";
        data.put("storeProvider", provider);
        data.put("storeProviderVersion", resolvePluginVersion(isGlobal ? "credentials" : "cloudbees-folder"));
        data.put("scope", isGlobal ? "global" : "folder");
        data.put("scopePath", scopePath);
        return data;
    }

    private String resolvePluginVersion(String pluginName) {
        try {
            hudson.PluginWrapper plugin = Jenkins.get().getPlugin(pluginName).getWrapper();
            return plugin != null ? plugin.getVersion() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Walks the class hierarchy to collect all declared fields (replaces FieldUtils.getAllFields). */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            result.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return result;
    }

    private boolean isExternalProvider(CredentialRecord record) {
        Map<String, String> additional = record.getAdditionalData();
        if (additional == null) return false;
        String provider = additional.getOrDefault("storeProvider", "");
        return !provider.equals("SystemCredentialsProvider")
                && !provider.equals("FolderCredentialsProvider")
                && !provider.equals("JobCredentialsStore");
    }

    private String resolveTypeDisplayName(StandardCredentials cred) {
        try {
            return cred.getDescriptor().getDisplayName();
        } catch (Exception e) {
            return cred.getClass().getSimpleName();
        }
    }

    private String reflectTimestamp(Object obj, String methodName) {
        try {
            Object result = obj.getClass().getMethod(methodName).invoke(obj);
            if (result == null) return null;
            if (result instanceof java.util.Date d)
                return DateTimeFormatter.ISO_INSTANT.format(d.toInstant());
            if (result instanceof Long l)
                return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(l));
            return result.toString();
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeErrorMessage(Exception e) {
        if (e instanceof java.security.GeneralSecurityException
                || e instanceof org.jose4j.lang.JoseException) {
            return "DISC_ERR_DECRYPT_FAILED";
        }
        if (e instanceof IllegalAccessException) {
            return "DISC_ERR_REFLECT_DENIED";
        }
        if (e instanceof IllegalStateException) {
            return "DISC_ERR_INVALID_STATE";
        }
        if (e instanceof java.io.IOException) {
            return "DISC_ERR_IO_FAILURE";
        }
        return "DISC_ERR_UNKNOWN";
    }
}
