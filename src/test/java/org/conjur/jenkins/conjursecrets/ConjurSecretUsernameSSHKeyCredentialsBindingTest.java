package org.conjur.jenkins.conjursecrets;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding.MultiEnvironment;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class ConjurSecretUsernameSSHKeyCredentialsBindingTest {

    private JenkinsRule j;

    private BindingDescriptor<ConjurSecretUsernameSSHKeyCredentials> bindCred = null;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        bindCred = new ConjurSecretUsernameSSHKeyCredentialsBinding.DescriptorImpl();
    }

    @Test
    void testBind() throws Exception {
        ConjurSecretUsernameSSHKeyCredentialsBinding bind = new ConjurSecretUsernameSSHKeyCredentialsBinding(
                "Dev-Team-1");
        File file = new File("/var/jenkins_home/workspace/Dev-Team-1/test-pipeline");
        TaskListener task = new StreamTaskListener(System.out, Charset.defaultCharset());
        Launcher launch = j.jenkins.createLauncher(task);
        FilePath ws = new FilePath(file);
        WorkflowJob job = j.createProject(WorkflowJob.class, "test-pipeline");
        Run<?, ?> completedBuild = new WorkflowRun(job);
        MultiEnvironment env1 = null;
        try {
            env1 = bind.bind(completedBuild, ws, launch, task);
        } catch (CredentialNotFoundException ex) {
            assertThrows(CredentialNotFoundException.class, () -> bind.bind(completedBuild, ws, launch, task));
        } catch (Exception ignored) {

        }

        assertNull(env1);
    }

    @Test
    void testUsernameVariable() {
        final ConjurSecretUsernameSSHKeyCredentialsBinding userNameCredentials = new ConjurSecretUsernameSSHKeyCredentialsBinding(
                "Test pipeline");
        String usernameVariable = "userName";
        userNameCredentials.setUsernameVariable(usernameVariable);
        String actualVar = userNameCredentials.getUsernameVariable();

        assertEquals(usernameVariable, actualVar,
                "Username variable should match the value that was set");
    }

    @Test
    void testVariables() {
        ConjurSecretUsernameSSHKeyCredentialsBinding bind = new ConjurSecretUsernameSSHKeyCredentialsBinding(
                "Dev-Team-1");
        String userNameVar = "test";
        String pwdVar = "pwd";
        bind.setSecretVariable(pwdVar);
        bind.setUsernameVariable(userNameVar);
        Set<String> varSet = new HashSet<>(Arrays.asList(userNameVar, pwdVar));
        Set<String> actualVarSet = bind.variables();

        assertEquals(varSet, actualVarSet);
    }

    @Test
    void testGetDisplayName() {
        assertNotNull(bindCred.getDisplayName());
    }

    @Test
    void testRequiresWorkspace() {
        assertFalse(bindCred.requiresWorkspace());
    }

    @Test
    void testtype() {
        ConjurSecretUsernameSSHKeyCredentialsBinding bind = new ConjurSecretUsernameSSHKeyCredentialsBinding(
                "Dev-Team-1");
        assertNotNull(bind.type());
    }

    @Test
    void testCredentialsAreMaskedInLogs() throws Exception {
        ConjurSecretUsernameSSHKeyCredentials cred = new ConjurSecretUsernameSSHKeyCredentialsImpl(
                CredentialsScope.GLOBAL, "usernamesshkey", "testUser", "usernamesshkeym", Secret.fromString("test"), "desc");
        SystemCredentialsProvider.getInstance().getCredentials().add(cred);
        SystemCredentialsProvider.getInstance().save();

        ConjurSecretCredentials spy = spy(cred);
        doReturn(Secret.fromString("test")).when(spy).getSecret();

        WorkflowJob job = j.createProject(WorkflowJob.class, "test-job");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "        withCredentials([conjurSecretUsernameSSHKey(credentialsId: 'usernamesshkey', usernameVariable:'SSH_USER', secretVariable:'SSH_KEY')]) {\n" +
                        "             println(SSH_USER) \n" +
                        "    }\n" +
                        "}", true)
        );

        WorkflowRun run = job.scheduleBuild2(0).get();
        String log = JenkinsRule.getLog(run);

        assertFalse(log.contains("testUser"), "User credential should be masked");
    }

}