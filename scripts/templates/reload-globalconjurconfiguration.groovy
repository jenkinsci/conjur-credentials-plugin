import jenkins.model.Jenkins
import org.conjur.jenkins.configuration.GlobalConjurConfiguration

def jenkins = Jenkins.getInstanceOrNull()
if (jenkins == null) {
    throw new IllegalStateException("Jenkins instance is not available")
}

def conjurConfig = jenkins.getDescriptorByType(GlobalConjurConfiguration.class)
if (conjurConfig == null) {
    throw new IllegalStateException("GlobalConjurConfiguration descriptor not found")
}

conjurConfig.load()
conjurConfig.save()
println("Reloaded GlobalConjurConfiguration from Jenkins home")
