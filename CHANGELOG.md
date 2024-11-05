# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [2.2.3] - 2024-11-05
## What’s New
- **Fixed should not access to other Jenkins Job Conjur Credentials ID:** Fixed the Conjur Credential store for one folder Job could not be accessed from other folder-level Jobs.
- **Avoid unnecessary folder-level logging:** Eliminate unnecessary logging by removing all Jenkins Job Items.

## [2.2.2] - 2024-10-08
## What’s New
- **Subfolder Host Identity Mapping Issue:** Fixed an issue where credentials mapped to subfolder host identities were not injected during pipeline execution, despite being visible at the subfolder level.
- **Jenkins Credential Store Inheritance:** Fixed the issue with Jenkins credential store inheritance.
- **Credentials store list is randomly empty:** If the Conjur appliance account or appliance URL is empty, should fall back to the global configuration values.
- **Jenkins folder-level Job:** Skip JWT/API Key authentication if Jenkins folder credentials scope is null.
- **Folder-level Display Name:** Fixed the Jenkins folder-level display name.

## [2.2.1] - 2024-06-29
## What’s New
Fixed to support Jenkins folder-level system (non-global) credentials with Conjur API key authentication. 

## [2.2.0] - 2024-06-03
## What’s New
Enhancement to support read/view permissions of Conjur Credentials for Jenkins users

## [2.1.0] - 2024-05-07
## What’s New
Support for multithreading access.

## [2.0.0] - 2024-03-04
## What’s New
Simplified JWT Configuration for Enhanced Security and User Experience
We're excited to announce a significant update to the Conjur Credentials plugin for Jenkins, focusing on simplifying and enhancing the JWT (JSON Web Token) configuration process. Our goal with this update is to streamline the user experience while increasing the security of your configurations.

## Key Updates:
- **Reduced Complexity:** We've reduced the number of custom fields in the JWT configuration. This approach not only simplifies the configuration process but also enhances the overall security by minimizing potential vulnerabilities.
- **Deprecation of Some Fields:** Please note that some fields (claims) have been deprecated in this update. Fields are restricted to pre-selected values, please ensure your existing configuration is compatible. This means that certain custom user inputs will no longer be supported. This change is critical for maintaining a secure and efficient configuration environment.
- **Simplified Configuration:he number of custom fields in the JWT configuration. This approach not only simplifies the configuration process but also enhances the overall security by minimizing potential vulnerabilities.
- **Deprecation of Some Fields:** Please note th** This functionality allows you to temporarily use some "grandfathered" values from your previous configurations. This interim solution is available until the next release, providing a comfortable adjustment period.

## Impact on Your Environment:
These changes are designed to enhance both security and user experience. However, they may impact your current environment due to the deprecation of certain fields and the shift towards a more streamlined configuration approach. We encourage you to review your current configurations and adapt to the new system, leveraging simplified configuration for an easier transition.

We strongly recommend utilizing the default values recommended for fields that will be deprecated. These defaults are either system-generated or selected from an approved list of values, ensuring optimal security and compatibility.

## [1.0.18] - 2024-02-02
- Update to support JWKS public key re-generation.

## [1.0.17] - 2023-08-23
- Fixed for Null-Pointer exception while retrieving Secrets
- Fixed pipeline build Junit Test cases rewritten with Mockito and removed power-mockito dependencies compatibility with JDK 11 &17 version.
- Fixed Jenkins-Bitbucket Instance 

## [1.0.16] - 2023-06-28
- End to End test of internal automated build process

## [1.0.15] - 2023-06-28
- Update for internal automated build process

## [1.0.14] - 2022-12-15
- Support access of Folder level crdentials to child folders & jobs.

## [1.0.13] - 2022-11-23
- Security updates in pom.xml & support to Java 11. The following depedency updates are made:
- org.jenkins-ci.plugins is updated from 4.17 to 4.48
- Jenkins version has been updated from 2.176.1 to 2.377
- kotlin-stdlib-common updated to 1.6.20
- okhttp has been updated from 3.11.0 to 4.10.0
- jackson-databind has been updated from 2.12.5 to 2.14.0
- gsom from 2.8.8 to 2.8.9
- io.jenkins.tools.bom artifact id updated from bom-2.164.x to bom-2.332.x

## [1.0.7] - 2021-10-05
- JWT token issuer is set to the root URL of the jenkins instance
- WebService ID for the authentication can be either the service id or authenticator_type/service_id (authn-jwt/id)
- Warning/error on validation for Key and Token TTL 

## [1.0.6] - 2021-09-27
- Updated README.md 
- Added "JWT Token Claims" button to configuration page to obtain referecence claims to be used by JWT Authenticator
- Fixed bindings for context aware store credentials

## [1.0.5] - 2021-09-23
- Added JWT Authentication
- Added Context Aware (Based on JWT) Credential Provider
- Updated Doc
- Misc fixes

## [1.0.4] - 2021-07-12
- Incorporated changes for null certificate on slave
- Brought fixes for core cyberark/conjur-credentials-plugin
- Release in plugins

##[ 1.0.2] - 2020-05-05

### Added
- Included changes to allow GIT plugin to retrieve credentials from slaves

### Removed
- Removed binaries deliverables, to use artifactory to deliver binaries

## 0.7.0 - 2019-09-27

### Added
- Added Support for SSH Private Key

[Unreleased]: https://github.com/cyberark/conjur-credentials-plugin/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/cyberark/conjur-credentials-plugin/compare/v0.7.0...v1.0.2
