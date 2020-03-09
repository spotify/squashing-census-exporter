# Releasing

Releases are staged with Sonatype Nexus and then synced to maven central. To publish a new release, update the `version` in build.gradle and run

`./gradlew clean assemble uploadArchives`.

Navigate to [the nexus staging repositories](https://oss.sonatype.org/#stagingRepositories) and find the newly created repo. Close it, and assuming all checks pass, release it using the UI buttons.

## Configuration

The `:uploadArchives` task uses the [gradle nexus plugin](https://github.com/bmuschko/gradle-nexus-plugin). It expects several configuration parameters in `~/.gradle/gradle.properties`:

- nexusUsername
- nexusPassword
- signing.keyId
- signing.password

While the plugin doesn't allow using a gpg agent, it can be worked around by providing dummy values and setting `signing.gnupg.keyName`, eg:
```
signing.keyId=null
signing.password=null
signing.secretKeyRingFile=/dev/null
signing.gnupg.keyName=D52838F9
```
