# Contributing to the CyberArk Secrets Manager Credentials Plugin

## Building from Source

To build the plugin from source, Maven is required. Build it like this:

```bash
git clone https://github.com/jenkinsci/conjur-credentials-plugin
cd conjur-credentials-plugin

# External contributors — fetches directly from Maven Central, no credentials needed:
mvn --settings mvn-settings.xml.dist clean install
```

**Internal developers:** `mvn-settings.xml` mirrors dependencies through internal Artifactory.
Set `ARTIFACTORY_HOST`, `ARTIFACTORY_USERNAME` and `ARTIFACTORY_PASSWORD`, then run
`mvn --settings mvn-settings.xml clean install`.

## Installing from Binaries

As another option, you can use the latest .hpi found under the binaries folder.

### Install in Jenkins

When you have the .hpi file, log into Jenkins as an administrator.
Then go to **Jenkins** -> **Manage Jenkins** -> **Manage Plugins** -> **Advanced**.
In the **Upload Plugin** section, browse for the .hpi and upload it to Jenkins:

![Upload Plugin](docs/images/UploadPlugin-Jenkins.png)

After installing the plugin, restart Jenkins:

![Install Plugin](docs/images/Plugin-Installing.png)

### Release and Promote

1. Before releasing a new version, update the version number in the `pom.xml` file. Change the `<version>` tag value to reflect the new version following semantic versioning principles (e.g., from `1.0.0` to `1.0.1`).
1. Merging into main/master branches will automatically trigger a release. If successful, this release can be promoted at a later time.
1. Jenkins build parameters can be utilized to promote a successful release or manually trigger additional releases as needed.
1. Reference the [internal automated release doc](https://github.com/conjurinc/docs/blob/master/reference/infrastructure/automated_releases.md#release-and-promotion-process) for releasing and promoting.
