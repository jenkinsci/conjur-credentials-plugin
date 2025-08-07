# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

Please use documentation outlined in the GitHub Readme for this version.  Official CyberArk documentation pages will be updated to reflect these changes once available

## [3.0.6] - 2025-08-05

### Fixed
No longer change secret ID when loading Conjur variable (CNJR-10588)

## [3.0.5] - 2025-06-18

### Added
- Added support for docker certificates

## [3.0.4] - 2025-06-06

### Added
- Added 'sub' entry  to JWTToken
- File Credential added

## [3.0.3] - 2025-06-06

### Fixed
- Fixed spotbugs

## [3.0.1] - 2025-06-02

### Added
- Added support for storing secrets in a secret file

### Fixed
- Fixed the issue where plugin couldn't find user credentials

## [3.0.0] - 2025-05-23

### Added
- Global Credentials added
- Added inheritance for Secrets and Configuration
- Added telemetry headers

### Changed
- Increased minimum Jenkins version to 2.455

### Removed
- Switched to new API - removed deprecated API calls and objects

## [2.2.4] - 2025-02-18

### Fixed
- Issue where toggling Conjur JWT checkbox with empty JWT input fields disabled/enabled the Save and Apply buttons

## [2.2.3] - 2024-11-12

### Fixed
- Fixed Conjur Credential store for one folder Job could not being accessible from other folder-level Jobs
- Fixed Jenkins MultiBranch feature branches Conjur Credential stored list visibility being grayed out

### Removed
- Eliminate unnecessary logging by removing all Jenkins Job Items.

## [2.2.2] - 2024-10-08

### Fixed
- Credentials mapped to subfolder host identities are now injected during pipeline execution
- Fixed issue with Jenkins credential store inheritance
- If Conjur appliance account or URL is empty, plugin now falls back to the global configuration values
- Skip JWT/API Key authentication if Jenkins folder credentials scope is null
- Fixed the Jenkins folder-level display name

## [2.2.1] - 2024-06-29

### Fixed
- Support Jenkins folder-level system (non-global) credentials with Conjur API key authentication.

## [2.2.0] - 2024-06-03

### Added
- Support read/view permissions of Conjur Credentials for Jenkins users

## [2.1.0] - 2024-05-07

### Added
- Support for multithreading access.

## [2.0.0] - 2024-03-04
## What’s New
- Simplified JWT Configuration for Enhanced Security and User Experience
We're excited to announce a significant update to the Conjur Credentials plugin for Jenkins, focusing on simplifying and enhancing the JWT (JSON Web Token) configuration process. Our goal with this update is to streamline the user experience while increasing the security of your configurations.

## Key Updates:
- Reduced Complexity:** We've reduced the number of custom fields in the JWT configuration. This approach not only simplifies the configuration process but also enhances the overall security by minimizing potential vulnerabilities.
- Deprecation of Some Fields:** Please note that some fields (claims) have been deprecated in this update. Fields are restricted to pre-selected values, please ensure your existing configuration is compatible. This means that certain custom user inputs will no longer be supported. This change is critical for maintaining a secure and efficient configuration environment.
- Simplified Configuration:he number of custom fields in the JWT configuration. This approach not only simplifies the configuration process but also enhances the overall security by minimizing potential vulnerabilities.
- Deprecation of Some Fields:** Please note th** This functionality allows you to temporarily use some "grandfathered" values from your previous configurations. This interim solution is available until the next release, providing a comfortable adjustment period.

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

### Added
- JWT Authentication
- Context Aware (Based on JWT) Credential Provider

### Fixed
- Misc. fixes

## [1.0.4] - 2021-07-12
- Incorporated changes for null certificate on slave
- Brought fixes for core cyberark/conjur-credentials-plugin
- Release in plugins

## [1.0.2] - 2020-05-05

### Added
- Included changes to allow GIT plugin to retrieve credentials from slaves

### Removed
- Removed binaries deliverables, to use artifactory to deliver binaries

## 0.7.0 - 2019-09-27

### Added
- Added Support for SSH Private Key

[3.0.6]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v3.0.5...v3.0.6
[3.0.5]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v3.0.4...v3.0.5
[3.0.4]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v3.0.3...v3.0.4
[3.0.3]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v3.0.1...v3.0.3
[3.0.1]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.2.4...v3.0.0
[2.2.4]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.2.3...v2.2.4
[2.2.3]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.2.2...v2.2.3
[2.2.2]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.2.1...v2.2.2
[2.2.1]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.18...v2.0.0
[1.0.18]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.17...v1.0.18
[1.0.17]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.16...v1.0.17
[1.0.16]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.15...v1.0.16
[1.0.15]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.14...v1.0.15
[1.0.14]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.13...v1.0.14
[1.0.13]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.7...v1.0.13
[1.0.7]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v1.0.2...v1.0.4
[1.0.2]: https://github.com/jenkinsci/conjur-credentials-plugin/compare/v0.7.0...v1.0.2
