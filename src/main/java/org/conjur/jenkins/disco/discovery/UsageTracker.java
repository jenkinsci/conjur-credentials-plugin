package org.conjur.jenkins.disco.discovery;

import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import org.conjur.jenkins.disco.DiscoCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.conjur.jenkins.disco.DiscoCode.*;

/**
 * Scans all Jenkins jobs and folders to build a usage graph:
 * credentialId → list of paths (jobs + folders) that reference it.
 *
 * For Pipeline jobs a regex scan over the script text is used (no AST parsing).
 * For Freestyle jobs the build wrapper / builder XML is inspected via toString().
 */
public class UsageTracker {

    private static final Logger LOGGER = Logger.getLogger(UsageTracker.class.getName());

    private final Map<String, List<String>> usage = new HashMap<>();

    public void scan() {
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            scanJobs();
        }
    }

    public List<String> getWhereUsed(String credentialId) {
        return usage.getOrDefault(credentialId, new ArrayList<>()).stream()
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns only the usage entries that fall within the given scope — i.e. jobs
     * whose full path starts with {@code scopePath}. This prevents a credential
     * defined in Folder1 and a same-ID credential defined in Folder2 from sharing
     * each other's job references.
     *
     * "Global" scope matches everything (globally-defined credentials are visible
     * everywhere, so any job could use them).
     */
    public List<String> getWhereUsedInScope(String credentialId, String scopePath) {
        List<String> all = usage.getOrDefault(credentialId, new ArrayList<>());
        if ("Global".equals(scopePath)) {
            return all.stream().distinct().collect(java.util.stream.Collectors.toList());
        }
        return all.stream()
                .filter(path -> path.equals(scopePath) || path.startsWith(scopePath + "/"))
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    // -------------------------------------------------------------------------

    private void scanJobs() {
        try {
            for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
                try {
                    String jobPath = job.getFullName();
                    String configXml = safeGetConfigXml(job);
                    if (!configXml.isEmpty()) {
                        extractCredentialIds(configXml).forEach(credId ->
                                usage.computeIfAbsent(credId, k -> new ArrayList<>()).add(jobPath)
                        );
                    }

                    // For Pipeline jobs also check script body
                    if (job instanceof WorkflowJob) {
                        String script = safeGetPipelineScript((WorkflowJob) job);
                        extractCredentialIds(script).forEach(credId ->
                                usage.computeIfAbsent(credId, k -> new ArrayList<>()).add(jobPath)
                        );
                    }
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, JOB_ITEM_SCAN_FAILED.format(job.getFullName()), t);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, JOB_SCAN_FAILED.format(), t);
        }
    }

    private String safeGetConfigXml(Job<?, ?> job) {
        try {
            return job.getConfigFile().asString();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeGetPipelineScript(WorkflowJob job) {
        try {
            org.jenkinsci.plugins.workflow.flow.FlowDefinition def = job.getDefinition();
            if (def == null) return "";
            // CpsFlowDefinition.getScript() holds the Groovy source; use reflection
            // so this class compiles without workflow-cps on the main compile classpath.
            try {
                return (String) def.getClass().getMethod("getScript").invoke(def);
            } catch (NoSuchMethodException ignored) {
                // not a CPS definition — fall through to toString
            }
            return def.toString();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, PIPELINE_SCRIPT_READ_FAILED.format(job.getFullName()), t);
        }
        return "";
    }

    /**
     * Extracts credential IDs from a config/script string.
     * Looks for patterns: credentialsId('...'), credentialsId("..."), <credentialsId>...</credentialsId>
     */
    public static List<String> extractCredentialIds(String text) {
        List<String> ids = new ArrayList<>();
        if (text == null || text.isEmpty()) return ids;

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:credentialsId|credentials)\\s*[=:(\"']\\s*[\"']([^\"']+)[\"']")
                .matcher(text);
        while (m.find()) {
            ids.add(m.group(1));
        }

        // XML element form: <credentialsId>value</credentialsId>
        java.util.regex.Matcher xmlM = java.util.regex.Pattern
                .compile("<credentialsId>([^<]+)</credentialsId>")
                .matcher(text);
        while (xmlM.find()) {
            ids.add(xmlM.group(1));
        }

        return ids;
    }
}
