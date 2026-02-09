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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurSecretUsernameCredentialsBindingTest {

    private JenkinsRule j;
    @Mock
    private BindingDescriptor<ConjurSecretUsernameCredentials> bindCred;
    @Mock
    private ConjurSecretUsernameCredentialsBinding credBinding;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testBind() throws Exception {
        ConjurSecretUsernameCredentialsBinding bind = new ConjurSecretUsernameCredentialsBinding("Dev-Team-1");
        MultiEnvironment env1 = null;
        File file = new File("/var/jenkins_home/workspace/Dev-Team-1/test-pipeline");
        TaskListener task = new StreamTaskListener(System.out, Charset.defaultCharset());
        Launcher launch = j.jenkins.createLauncher(task);
        FilePath ws = new FilePath(file);
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "test-pipeline");
        Run<?, ?> completedBuild = new WorkflowRun(job);
        try {
            env1 = bind.bind(completedBuild, ws, launch, task);
        } catch (Exception ignored) {
        }
        assertNull(env1);

    }

    @Test
    void testUsernameVariable() {
        final ConjurSecretUsernameCredentialsBinding userNameCredentials = new ConjurSecretUsernameCredentialsBinding(
                "Test pipeline");
        String usernameVariable = "userName";
        userNameCredentials.setUsernameVariable(usernameVariable);
        String actualVar = userNameCredentials.getUsernameVariable();

        assertEquals(usernameVariable, actualVar,
                "Username variable should match the value that was set");
    }

    @Test
    void testVariables() {
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
    class DescriptorImpl {

        @Test
        void testGetDisplayName() {
            assertNull(bindCred.getDisplayName());
        }

        @Test
        void testRequiresWorkspace() {
            boolean condition = false;
            assertEquals(condition, bindCred.requiresWorkspace());
        }
    }

    @Test
    void testType() {
        ConjurSecretUsernameCredentialsBinding bind = new ConjurSecretUsernameCredentialsBinding("Dev-Team-1");
        assertNotNull(bind.type());
    }

    @Test
    void testDescriptorImplProperties() {
        ConjurSecretUsernameCredentialsBinding.DescriptorImpl descriptor =
                new ConjurSecretUsernameCredentialsBinding.DescriptorImpl();

        assertEquals("Conjur Secret Username credentials", descriptor.getDisplayName());
        assertFalse(descriptor.requiresWorkspace());
        assertEquals(ConjurSecretUsernameCredentials.class, descriptor.type());
    }


}