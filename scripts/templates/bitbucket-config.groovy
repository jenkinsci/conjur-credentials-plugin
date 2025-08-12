import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration
import jenkins.model.Jenkins

def instance = Jenkins.getInstance()
def pluginConfig = instance.getDescriptorByType(BitbucketPluginConfiguration.class)

def serverConfig = new BitbucketServerConfiguration(
        "bitbucket-personal-token",     // adminCredentialsId
        "http://bitbucket1:7990",       // baseUrl
        "bitbucket"                     // id (nullable)
)

serverConfig.setServerName("bitbucket")  //  method to set name

pluginConfig.setServerList([serverConfig])
pluginConfig.save()