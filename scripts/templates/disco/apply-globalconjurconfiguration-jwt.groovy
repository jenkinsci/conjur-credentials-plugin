import jenkins.model.Jenkins

def jenkins = Jenkins.getInstanceOrNull()
if (jenkins == null) {
    throw new IllegalStateException('Jenkins instance is not available')
}

def applianceUrl = '''{{CONJUR_APPLIANCE_URL}}'''
def conjurAccount = '''{{CONJUR_ACCOUNT}}'''
def serviceId = '''{{CONJUR_SERVICE_ID}}'''

def xmlEscape = { value ->
    value
        .replace('&', '&amp;')
        .replace('<', '&lt;')
        .replace('>', '&gt;')
        .replace('"', '&quot;')
        .replace("'", '&apos;')
}

def xml = """<?xml version='1.1' encoding='UTF-8'?>
<org.conjur.jenkins.configuration.GlobalConjurConfiguration plugin="conjur-credentials">
  <conjurConfiguration>
    <applianceURL>${xmlEscape(applianceUrl)}</applianceURL>
    <account>${xmlEscape(conjurAccount)}</account>
    <credentialID></credentialID>
    <certificateCredentialID></certificateCredentialID>
  </conjurConfiguration>
  <selectAuthenticator>JWT</selectAuthenticator>
  <enableJWKS>true</enableJWKS>
  <authWebServiceId>${xmlEscape(serviceId)}</authWebServiceId>
  <jwtAudience>cyberark-conjur</jwtAudience>
  <keyLifetimeInMinutes>60</keyLifetimeInMinutes>
  <tokenDurarionInSeconds>120</tokenDurarionInSeconds>
  <enableContextAwareCredentialStore>true</enableContextAwareCredentialStore>
  <enableIdentityFormatFieldsFromToken>false</enableIdentityFormatFieldsFromToken>
  <identityFormatFieldsFromToken>jenkins_full_name</identityFormatFieldsFromToken>
  <selectIdentityFormatToken>jenkins_full_name</selectIdentityFormatToken>
  <selectIdentityFieldsSeparator>-</selectIdentityFieldsSeparator>
  <identityFieldName>identity</identityFieldName>
</org.conjur.jenkins.configuration.GlobalConjurConfiguration>
"""

def configFile = new File(jenkins.getRootDir(), 'org.conjur.jenkins.configuration.GlobalConjurConfiguration.xml')
configFile.text = xml
println('Wrote GlobalConjurConfiguration to: ' + configFile.absolutePath)
