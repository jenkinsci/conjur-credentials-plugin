package org.conjur.jenkins.conjursecrets;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Nested;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConjurSecretUsernameCredentialsBindingTest {

    @Rule
    public JenkinsRule jRule = new JenkinsRule();
    @Mock
    private BindingDescriptor<ConjurSecretUsernameCredentials> bindCred = null;
    @Mock
    private ConjurSecretUsernameCredentialsBinding credBinding;


    @Test
    public void testBind() throws Exception {
        ConjurSecretUsernameCredentialsBinding bind = new ConjurSecretUsernameCredentialsBinding("Dev-Team-1");
        MultiEnvironment env1 = null;
        File file = new File("/var/jenkins_home/workspace/Dev-Team-1/test-pipeline");
        TaskListener task = new StreamTaskListener(System.out, Charset.defaultCharset());
        Launcher launch = jRule.jenkins.createLauncher(task);
        FilePath ws = new FilePath(file);
        WorkflowJob job = jRule.jenkins.createProject(WorkflowJob.class, "test-pipeline");
        Run<?, ?> completedBuild = new WorkflowRun(job);
        try {
            env1 = bind.bind(completedBuild, ws, launch, task);
        } catch (Exception ignored) {
        }
        assertNull(env1);

    }

    @Test
    public void testUsernameVariable() throws SecurityException {
        final ConjurSecretUsernameCredentialsBinding userNameCredentials = new ConjurSecretUsernameCredentialsBinding(
                "Test pipeline");
        String usernameVariable = "userName";
        userNameCredentials.setUsernameVariable(usernameVariable);
        String actualVar = userNameCredentials.getUsernameVariable();

        assertEquals(usernameVariable, actualVar,
                "Username variable should match the value that was set");
    }

    @Test
    public void testVariables() {
        ConjurSecretUsernameCredentialsBinding bind = new ConjurSecretUsernameCredentialsBinding("Dev-Team-1");
        String userNameVar = "test";
        String pwdVar = "pwd";
        bind.setPasswordVariable(pwdVar);
        bind.setUsernameVariable(userNameVar);
        Set<String> varSet = new HashSet<>(Arrays.asList(userNameVar, pwdVar));
        Set<String> actualVarSet = bind.variables();

        assertEquals(varSet, actualVarSet);
    }

    @Nested
    public class DescriptorImpl {

        @Test
        public void testGetDisplayName() {
            assertEquals("Conjur Secret Username credentials", bindCred.getDisplayName());
        }

        @Test
        public void testRequiresWorkspace() {
            boolean condition = false;
            assertEquals(condition, bindCred.requiresWorkspace());
        }
    }

    @Test
    public void testType() {
        ConjurSecretUsernameCredentialsBinding bind = new ConjurSecretUsernameCredentialsBinding("Dev-Team-1");
        assertNotNull(bind.type());
    }

    @Test
    public void testDescriptorImplProperties() {
        ConjurSecretUsernameCredentialsBinding.DescriptorImpl descriptor =
                new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();

        assertEquals("Conjur Secret Username credentials", descriptor.getDisplayName());
        assertFalse(descriptor.requiresWorkspace());
        assertEquals(ConjurSecretUsernameCredentials.class, descriptor.type());
    }


}