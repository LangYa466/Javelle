# P01-W03 local/tool version-lock implementation proposal

TASK / AGENT_ID / BASE_REVISION: `P01-W03` / `/root/p00_repair` / `eb75e78a6772b62271b2b6affd127cc16ee54d42`

STATUS: **IMPLEMENTED (proposal only; no build mutation)**

CHANGED_PATHS: `.agent/reports/P01-LOCK-PROPOSAL.md`; ignored evidence `.agent/logs/P01-LOCK-PROPOSAL/local-probe.txt`

CONTRACT_CHANGES: none

REQUIREMENTS: P01-02/03/04/07/10/11 implementation design feeding P02-01/02/06/08/12. Exact third-party pins remain owned by the appropriate P01 research packages.

## Current repository finding

The repository is a single Java build with one root `settings.gradle.kts` and `build.gradle.kts`. It has JUnit BOM `6.0.0`, no version catalog, no dependency locks, no dependency-verification metadata, no build-logic included build, and no toolchain declaration. The wrapper URL is fixed to Gradle `9.6.0`, but has no `distributionSha256Sum`.

**Blocker `P01-WRAPPER-INTEGRITY-001`:** checked-in `gradle-wrapper.jar` is SHA-256 `91a239400bb638f36a1795d8fdf7939d532cdc7d794d1119b7261aac158b1e60`. The official Gradle 9.6.0 and 9.6.1 wrapper checksum endpoint returned `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`. Do not execute the existing wrapper as trusted. P02 must regenerate both scripts/JAR from the accepted official distribution and independently compare the JAR before the first wrapper execution.

Observed official distribution hashes:

- Gradle 9.6.0 `-bin.zip`: `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`
- Gradle 9.6.1 `-bin.zip`: `9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`

The system `gradle` reports 9.6.1 on OpenJDK 25.0.3. This proves local executability, not wrapper integrity or the final project pin.

## Proposed lock layout for P02

1. `gradle/wrapper/gradle-wrapper.properties`
   - Fixed `distributionUrl`, no RC/nightly/dynamic version.
   - Add the official `distributionSha256Sum` for the accepted version.
   - Regenerate `gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` together; validate JAR SHA-256 against Gradle's published checksum before invoking it.
2. `gradle/libs.versions.toml`
   - One central catalog with exact `[versions]`, `[libraries]`, and `[plugins]`; no `latest.*`, ranges, `+`, snapshots, or changing modules.
   - Aliases grouped by contracts: parser/compiler, JSON/RPC/LSP, tests, quality, IntelliJ Platform, website-supporting JVM tools. The Node lock belongs under `website/`, not this catalog.
3. `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, and `build-logic/src/main/kotlin/org.javelle.*-conventions.gradle.kts`
   - Freeze Java toolchains, `options.release`, test launcher, UTF-8, reproducible archives, dependency locking, and quality rules once.
   - Import the root catalog explicitly into the included build; Gradle auto-import only covers the root `gradle/libs.versions.toml`.
4. `gradle/verification-metadata.xml`
   - Strict verification using SHA-256 for every resolved artifact/POM/module/plugin marker and PGP where independently verified.
   - Generate as bootstrap only, review the diff against upstream/Maven Central sources, then commit. Never accept generated metadata merely because resolution succeeded.
5. `gradle.lockfile` in every project/included build that owns resolvable external configurations.
   - Activate `lockAllConfigurations()` through conventions and resolve all applicable project and build-logic configurations with `--write-locks`.
   - Lock files supplement exact catalog pins by freezing transitives; they do not replace checksum verification.
6. `docs/engineering/toolchain-matrix.md`, `docs/engineering/SOURCES.md`, and `third-party/dependencies.json`
   - Record the four independent Java axes, source URL/check date/checksum, supported OS/arch, license, bundling/runtime/output status, and external namespace gates.
   - Source snapshots must remain concise and license-reviewed.

## Toolchain contract

Do not collapse these axes:

| Axis | Proposed Gradle representation | Pin owner/state |
|---|---|---|
| Compiler runtime JDK | Java launcher/toolchain for compiler process | JDK 25 exact vendor/build/checksum: **WAIT P01-W01** |
| Gradle daemon JDK | CI matrix/environment plus daemon criteria; not inferred from target release | Minimum/fixed CI vendor/build: **WAIT P01-W01** |
| Javelle target release | explicit compiler option/API accepting 21 and 25 profiles | semantic inventory: **WAIT P01-W01/spec** |
| IDEA client bytecode | IntelliJ module toolchain plus `options.release` matching lowest IDE platform Java | exact IDE floor: **WAIT P01-W02/P01-04** |

Gradle's official compatibility table says Java 25 toolchains and daemon execution are supported from Gradle 9.1.0. IntelliJ Platform Gradle Plugin 2.x requires Gradle 9.0.0+ and Java 17+, so either fixed 9.6.0 or 9.6.1 satisfies those lower bounds. **Candidate recommendation:** accept Gradle 9.6.1 because it is locally executable and is a patch successor to the current 9.6.0 URL, but W01 must approve the exact version/source snapshot before P02 changes the wrapper.

## IntelliJ matrix contract

- Candidate plugin pin from current JetBrains documentation: `org.jetbrains.intellij.platform` 2.18.1; treat as **WAIT P01-W02/P01-04**, not accepted merely from the current docs example.
- Build against the lowest supported IDEA release, then run Plugin Verifier and real smoke tests across the lowest and highest accepted stable IDE builds.
- Candidate conservative range is IDEA 2024.2 (branch 242, Java 21) through the stable upper build selected by P01-04. Do not omit `until-build` and thereby imply future compatibility.
- Test IC and IU separately because Java/debug APIs and bundled modules differ. Declare `com.intellij.java`/required bundled modules explicitly; 2026.2+ debugger APIs may require `bundledModule("intellij.platform.debugger")`.
- LSP API/module availability must be probed in each target artifact; it cannot be inferred from marketing release names. Unsupported IDEs/builds go in the matrix with a concrete missing module/API reason.
- IDEA 2026.2+ moves platform Java to 25; adding that branch may require a separate plugin artifact/release line. Do not mix it into the Java-21 bytecode range without verifier and runtime evidence.

## Pins that must wait for other P01 packages

- JDK 25 and Java 21 distributions, vendor builds, URLs, and checksums: W01.
- IntelliJ platform releases/build numbers, LSP/debug bundled modules, Platform Gradle Plugin final pin: P01-04/W02.
- Lombok artifact/source tag/hash: P01-05.
- LSP protocol library, JSON/RPC library, DTO gap inventory: P01-06.
- Parser, testing, formatter/lint, SBOM/license tooling and their license disposition: P01-07/W02.
- Node/package manager/runtime and front-end lockfile: P01-11.

No placeholder version may enter `libs.versions.toml`; unresolved entries remain absent until their owning report is independently accepted.

## Acceptance for the future P02 implementation

1. Wrapper integrity: offline-safe script compares the accepted wrapper JAR and distribution hashes; altered byte and wrong `distributionSha256Sum` negative tests must fail.
2. Toolchain separation: Gradle diagnostic task emits daemon JDK, compiler launcher JDK, requested target release, and IDEA bytecode target as four named JSON fields. Matrices run target 21 and 25; unsupported combinations fail clearly.
3. Pin audit: repository scan rejects dynamic/range/snapshot/changing versions in catalogs, plugin declarations, build logic, and Node manifests.
4. Verification: a clean, isolated `GRADLE_USER_HOME` resolves under strict dependency verification; tampered checksum and missing metadata cases fail. Save only bounded logs.
5. Locking: clean resolution produces no lockfile diff; an intentionally changed transitive fails until an explicitly reviewed `--write-locks` update.
6. IDE matrix: Plugin Verifier plus real launch/smoke evidence for every declared IC/IU boundary; out-of-range builds are rejected by plugin metadata.
7. Architecture: build-logic tests reject core dependencies on Gradle/IDE/LSP and cyclic project edges.
8. Aggregates: `verifyQuick`, `verifyAll`, and `releaseCheck` enumerate required gates and report incomplete gates as failures, never empty success.

Suggested lightweight commands after implementation (exact tasks finalized by P02):

```text
sha256sum -c gradle/wrapper/gradle-wrapper.jar.sha256
./gradlew --version
./gradlew --dependency-verification strict dependencies
./gradlew resolveAndLockAll --write-locks
git diff --exit-code -- '**/gradle.lockfile' gradle/verification-metadata.xml
./gradlew verifyQuick
```

## Primary sources checked 2026-09-11

- Gradle compatibility matrix: <https://docs.gradle.org/current/userguide/compatibility.html>
- Gradle wrapper/integrity: <https://docs.gradle.org/current/userguide/gradle_wrapper.html>
- Gradle dependency verification: <https://docs.gradle.org/current/userguide/dependency_verification.html>
- Gradle dependency locking: <https://docs.gradle.org/current/userguide/dependency_locking.html>
- Gradle version catalogs: <https://docs.gradle.org/current/userguide/version_catalogs.html>
- IntelliJ Platform Gradle Plugin 2.x: <https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html>
- IntelliJ build ranges: <https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html>
- IntelliJ plugin/module compatibility: <https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html>
- IntelliJ 2026 API changes: <https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html>

TESTS: local read-only probe and official checksum fetches exit 0; no wrapper execution and no heavy build. Evidence: `.agent/logs/P01-LOCK-PROPOSAL/local-probe.txt`.

REVIEW: pending independent reviewer; proposal owner cannot approve its own package.

RISKS_OR_BLOCKERS: existing wrapper JAR is not integrity-verified and must not be trusted; all cross-package pins listed above await accepted owners. Nobara is not in Gradle's explicitly listed supported OS table, so Linux CI must include an officially supported Ubuntu baseline even though the local Gradle install works.

NEXT_DEPENDENCIES: W01/W02/P01-04..11 decisions, then a single build owner implements the accepted lock layout in P02.

REPORT_PATH: `.agent/reports/P01-LOCK-PROPOSAL.md`
