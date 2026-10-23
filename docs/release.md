# Release guide

How to publish the SDK to Maven Central. **Nothing here runs automatically and nothing has been published yet.** Publishing needs the maintainer's explicit decision and several things only the maintainer can provide (see [Human setup required](#human-setup-required)).

## Release history

| Version | Date | Notes |
|---|---|---|
| 0.1.0 | 2026-10-23 | First release; published to Maven Central, signed with key `BC2FB2385127DC8345884595564BA523FA3BCCF6` (expires 2028-10-22, renew before then). Verified from clean Maven and Gradle consumers and with `gpg --verify` against the public keyserver. |
| 0.1.1 | 2026-10-23 | Patch: `balance()` accepts the bare-number response of the real API; `Invoice` exposes the test-mode fields (constructor change). Found by the manual check against a test-mode app. Verified from clean Maven/Gradle consumers, `gpg --verify` of all four artifacts, and the live check run with the published jar. |

The signing key backup and its passphrase are kept by the maintainer outside the repository. Rotate the Portal token after any exposure.

## Baseline

| Item | Value |
|---|---|
| Coordinates | `io.github.alejo2075:qvapay-java` |
| First version | `0.1.0` (development uses `0.1.0-SNAPSHOT`, local only; snapshots are never published) |
| Runtime / bytecode | Java 17 LTS (`--release 17`), verified on 17 and 21 |
| Build | Maven Wrapper 3.10.0 (do not use Maven 4 for publishing) |
| Publisher | `org.sonatype.central:central-publishing-maven-plugin:0.12.0` (Central Portal), `autoPublish=false` |
| Signing | `maven-gpg-plugin:3.2.8`, only in profile `central` |
| Artifacts | `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.asc` signatures, checksums |

The old OSSRH/Nexus staging flow is not used. Published versions are immutable: a mistake is fixed with a **new** version, never by re-uploading.

## Human setup required

These steps cannot be done by the build or by an assistant:

- [x] Public GitHub repository `Alejo2075/qvapay-java` created and pushed.
- [x] Verify the namespace `io.github.alejo2075` in the [Central Portal](https://central.sonatype.com/publishing/namespaces) (log in with GitHub and follow the instructions to prove ownership).
- [ ] Confirm the maintainer data and the license (`LICENSE` is MIT, © 2026 Alejo2075; the POM lists *Alejandro Santana* as developer). Change both if you prefer other public details.
- [x] Generate a **Portal user token** (Central Portal → Account → *Generate User Token*).
- [x] Create a GPG key pair, publish the public key to a keyserver (for example `keys.openpgp.org`), keep the private key out of the repository.
- [x] GitHub environment `maven-central` exists with a required reviewer and is limited to protected branches.
- [x] Add the environment secrets (Settings → Environments → maven-central) `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY` (ASCII-armored export) and `GPG_PASSPHRASE`.
- [x] `main` protected (pull request, required status checks, no force-push, linear history). Private vulnerability reporting, secret scanning with push protection and Dependabot alerts are enabled.
- [ ] A QvaPay app in **test mode** if you want to run the manual integration checks below.
- [x] Explicit approval to publish (given for 0.1.0; each release needs its own).

## One-time machine setup (for local releases)

```bash
gpg --full-generate-key                          # RSA 4096 (or ed25519), no expiry or a long one
gpg --list-secret-keys --keyid-format long
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
```

`~/.m2/settings.xml` (never commit it):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>PORTAL_TOKEN_USERNAME</username>
      <password>PORTAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

## Procedure

1. **Decide the version** (SemVer) and update `CHANGELOG.md`: move `[Unreleased]` entries under `## [X.Y.Z] - YYYY-MM-DD`.
2. **Set the release version**, no `-SNAPSHOT`: `./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`. Update the version in `examples/*` and in the README installation snippets.
3. **Check the POM**: license, developer, SCM URL, dependencies. `./mvnw dependency:tree` should show only Jackson.
4. **Verify everything** with JDK 17 and again with JDK 21:
   ```bash
   ./mvnw -B -ntp clean verify
   ./mvnw -B -ntp install -DskipTests
   ./mvnw -B -ntp -f examples/plain-java/pom.xml clean verify
   ./mvnw -B -ntp -f examples/webhook/pom.xml clean verify
   (cd examples/gradle-java && ./gradlew test)
   (cd examples/gradle-groovy && ./gradlew test)
   python3 scripts/readme_snippets.py check
   ```
5. **Build the signed bundle without uploading anything** and inspect it:
   ```bash
   ./mvnw -B -ntp -Pcentral -DskipUpload=true -DskipTests clean deploy
   python3 scripts/verify_bundle.py target/central-publishing/central-bundle.zip
   ```
   This needs only the GPG key, no token. The verifier checks the five files per artifact, `.asc` signatures (it runs `gpg --verify`), checksums, required POM metadata and that the bytecode is Java 17 (class file major 61). Note: with a `-SNAPSHOT` version the plugin builds no bundle.
6. **Commit and tag**: `chore(release): prepare X.Y.Z`, then an annotated tag `git tag -a vX.Y.Z -m "vX.Y.Z"` on that commit. Pushing the tag does **not** publish anything.
7. **Upload for validation** with the protected workflow: GitHub → Actions → *Release (manual)* → *Run workflow* on `main` with the version. A reviewer must approve the `maven-central` environment. The workflow checks version/tag/changelog, re-runs the full verification and the example consumers, signs, and uploads to the Portal. Alternatively, locally: `./mvnw -Pcentral -DskipTests clean deploy`.
8. **Review and publish manually** in <https://central.sonatype.com/publishing/deployments>. Because `autoPublish=false`, an upload is never a publication. Wait for the state *Published*.
9. **Verify resolution** from clean consumers once the artifact is visible (it can take a while to sync):
   ```bash
   # Maven: temporary project with the dependency and no extra repositories
   # Gradle: repositories { mavenCentral() } only (no mavenLocal, no JitPack)
   ```
   Remove `mavenLocal()` from the example builds and run them against Central.
10. **Update the repository** only after step 9: set real coordinates and the Maven Central badge in `README.md` and `README.es.md` (remove the "not yet published" notes), add the Javadoc link if hosted, publish a GitHub Release from the tag, and bump to the next `-SNAPSHOT`.
11. **Rotate credentials** if anything leaked. Never reuse a version number. Rollback means publishing a corrected new version and documenting it in the changelog.

## Optional manual integration check (never in public CI)

With an app in **test mode** and credentials in environment variables, run `merchant().info()`, `merchant().balance()`, create an invoice and fetch it with `transactions().get(...)`, and verify a webhook from the QvaPay simulator or a signed fixture. Compare the results with [api-coverage.md](api-coverage.md) and record any difference there. Do not run `charge` against production.

## Never

- Publish without the maintainer's approval, or from a fork.
- Put tokens, GPG keys or passphrases in Git, logs or workflow files.
- Claim the package is on Maven Central before a verified link exists.
