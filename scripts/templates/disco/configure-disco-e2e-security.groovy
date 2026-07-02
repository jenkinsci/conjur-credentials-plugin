import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import jenkins.model.Jenkins

String adminId = System.getenv('JENKINS_ADMIN_ID') ?: 'admin'
String adminPassword = System.getenv('JENKINS_ADMIN_PASSWORD')

Jenkins jenkins = Jenkins.get()

HudsonPrivateSecurityRealm securityRealm
if (jenkins.getSecurityRealm() instanceof HudsonPrivateSecurityRealm) {
    securityRealm = (HudsonPrivateSecurityRealm) jenkins.getSecurityRealm()
} else {
    securityRealm = new HudsonPrivateSecurityRealm(false)
    jenkins.setSecurityRealm(securityRealm)
}

def adminUser = securityRealm.getAllUsers().find { it.id == adminId }
if (adminUser == null) {
    securityRealm.createAccount(adminId, adminPassword)
    println "Created CyberArk DisCo E2E Jenkins admin user '${adminId}'"
} else {
    adminUser.addProperty(HudsonPrivateSecurityRealm.Details.fromPlainPassword(adminPassword))
    adminUser.save()
    println "Reset CyberArk DisCo E2E Jenkins admin user '${adminId}' password"
}

FullControlOnceLoggedInAuthorizationStrategy authorizationStrategy = new FullControlOnceLoggedInAuthorizationStrategy()
authorizationStrategy.setAllowAnonymousRead(false)
jenkins.setAuthorizationStrategy(authorizationStrategy)
jenkins.save()

println 'Configured CyberArk DisCo E2E Jenkins security: authenticated users have full control; anonymous read disabled'