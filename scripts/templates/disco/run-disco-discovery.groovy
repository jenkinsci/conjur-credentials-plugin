import org.conjur.jenkins.disco.config.DiscoExporterConfiguration
import org.conjur.jenkins.disco.discovery.DiscoveryOrchestrator
import org.conjur.jenkins.disco.model.DiscoveryRunResult

def config = DiscoExporterConfiguration.get()
if (config == null) {
    throw new IllegalStateException('CyberArk DisCo Discovery configuration is not available')
}

config.load()
config.setLastExportTimestamp(0L)
println "CyberArk DisCo Discovery config: subdomain=${config.getSubdomain()}, authMode=${config.getAuthMode()}, credentialId=${config.getConjurCredentialId()}"

if (!config.getSubdomain()) {
    throw new IllegalStateException('CyberArk DisCo Discovery subdomain is not configured')
}
if (!config.getConjurCredentialId()) {
    throw new IllegalStateException('CyberArk DisCo Discovery credential is not configured')
}

def orchestrator = DiscoveryOrchestrator.getInstance()

println 'Starting CyberArk DisCo Discovery export...'
orchestrator.run(DiscoveryOrchestrator.TriggerType.MANUAL)

def result = orchestrator.getCurrentResult()
println "CyberArk DisCo Discovery completed. status=${result.getStatus()}, message=${result.getMessage()}, kid=${result.getKid()}, jwksUri=${result.getJwksUri()}, conjurUrl=${result.getConjurUrl()}"

if (result.getStatus() != DiscoveryRunResult.Status.SUCCESS) {
    throw new IllegalStateException("CyberArk DisCo Discovery export did not succeed. status=${result.getStatus()}, message=${result.getMessage()}")
}