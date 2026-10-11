# Releasing CaffeineDose

How release APKs are signed and published. For everyday debug builds, see [Building](README.md#building) in the README.

## Signing

Release builds are signed only when a signing configuration is provided. Never commit keystores or passwords:

| Gradle property (`caffeinedose.signing.…`) | Environment variable                  | Release workflow secret                              |
|--------------------------------------------|---------------------------------------|------------------------------------------------------|
| `storeFile`                                | `CAFFEINEDOSE_SIGNING_STORE_FILE`     | `CAFFEINEDOSE_SIGNING_KEYSTORE_BASE64` (base64 file) |
| `storePassword`                            | `CAFFEINEDOSE_SIGNING_STORE_PASSWORD` | `CAFFEINEDOSE_SIGNING_STORE_PASSWORD`                |
| `keyAlias`                                 | `CAFFEINEDOSE_SIGNING_KEY_ALIAS`      | `CAFFEINEDOSE_SIGNING_KEY_ALIAS`                     |
| `keyPassword`                              | `CAFFEINEDOSE_SIGNING_KEY_PASSWORD`   | `CAFFEINEDOSE_SIGNING_KEY_PASSWORD`                  |

The keystore is created once, and every update must be signed with it; if it ever changes, update
`SIGNING_CERT_SHA256` in the workflow:

```bash
keytool -genkeypair -keystore caffeinedose-release.jks -alias caffeinedose -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 caffeinedose-release.jks   # macOS: base64 -i caffeinedose-release.jks
```

## Publishing a release

Bump the version in `app/build.gradle.kts` on `main`, and add its changelog as
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (`./gradlew -q :app:printVersionCode`). Then run the
[release workflow](.github/workflows/release.yml) on `main` (Actions → Release → Run workflow). It creates the `vX.Y.Z`
tag, builds from scratch, tests, lints, signs, checks the signing certificate, attests the build provenance, and
publishes the APK with its checksum, with the changelog as release notes. Pushing a tag that points to `main` works too.

The workflow runs in the `release` environment, which can hold the secrets and protection rules (Settings →
Environments).
