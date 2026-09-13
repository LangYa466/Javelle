# P09-W02 Gradle plugin implementation report

TASK / AGENT_ID / BASE_REVISION: `P09-W02` / `/root/p00_repair` / shared `dev`

STATUS: **IMPLEMENTED, REVIEW REQUIRED**

## P09-R01 review repairs

- P09-05: selected Gradle launcher metadata drives Worker `JAVA_HOME`. The consumer uses native `/opt/jdk21/jdk-21.0.11+10`; a separate JDK 25/release 25 fixture passes. A compiler marker proves `help` configuration does not launch it.
- P09-06/07: both main/test P08 models are inspected for selected JDK metadata and checkout-path leakage. Every TestKit invocation is `--offline` and resolves the plugin through local `includeBuild`.
- P09-08: the fixture runs real Gradle `test` with a Java assertion crossing Java/Teyru/Java, application `run`, jar, and the built classes in an external native JDK 21 process.
- P09-09: compile classpath is a literal `@Classpath` input and is forwarded to joint javac. Java source, classpath JAR content, compiler file content, release and option mutations each rerun generation after an `UP_TO_DATE` baseline.
- P09-10/11: syntax failure retains accepted bytes and exposes original `A.teyru` plus `TY-SYN`; modified-owned, symlink and foreign collision attacks fail closed. Stale generated Java/class/map removal is asserted. Source-map publication now validates ownership and uses a sibling stage, atomic swap and backup rollback.

## Implemented requirements

- P09-01/02/03: binary plugin `dev.teyru`, typed lazy `teyru` extension, real main/test `SourceDirectorySet`, provider-backed generate tasks before Java compilation.
- P09-05: `GenerateTeyruJava` uses Gradle `processIsolation()` Worker API and starts only the explicitly configured packaged `teyru` executable. Missing/non-executable input fails before publication.
- P09-06: export tasks serialize the accepted P08 portable model to `build/teyru/workspace/{main,test}.json`; generated Java is under `build/generated/sources/teyru/<sourceSet>`.
- P09-08/09: external TestKit consumer compiles main and test Teyru sources, Java calls generated Teyru, application `run` prints `OK`, jar is built, second identical build is `UP_TO_DATE` with configuration-cache reuse, source mutation reruns, deleted owned output disappears, and an injected foreign file survives.
- P09-04/08: the execution facade accepts declared Java sources and forwards them to the P06 joint javac request. The offline consumer proves `Java Main -> Teyru User -> Java Helper` with the JVM output `OK`.
- P09-06/11: source maps are hash-owned below `build/teyru/source-maps/<sourceSet>`; zero-source regeneration validates prior CLI ownership, removes owned Java/maps, and preserves an unlisted foreign file.
- P09-07/12: the final TestKit consumer resolves `dev.teyru` using `pluginManagement.includeBuild(<local checkout>)` from a temporary external project.

## Verification

- `./gradlew --no-daemon :gradle-plugin:spotlessApply :gradle-plugin:test --tests dev.teyru.gradle.P09GradlePluginTest --rerun-tasks` — exit 0, 2/2 P09 external cases using offline local `includeBuild`. Evidence `.agent/logs/P09-W02/w03-final-r2.log`.
- `./gradlew --no-daemon --dependency-verification=strict :gradle-plugin:test :gradle-plugin:p09TestKit verifyQuick` — exit 0 before the final expanded fixture; `verifyQuick` reports pass and architecture boundaries pass. Evidence `.agent/logs/P09-W02/full-verify.log`.
- `./gradlew --no-daemon :gradle-plugin:test --rerun-tasks` after P09-R01 — exit 0; 7 tests, zero failures/skips. Final run including external native JDK 21 launch: `.agent/logs/P09-W02/r01-final.log`. Targeted invalidation and ownership logs are `r01-invalidation.log` and `r01-negatives.log`.

## Remaining independent verification

- Independent reviewer must reproduce the repaired counterexamples; implementation does not self-accept. P37 full build-cache/custom-source-set scope is not claimed.
- Final `verifyQuick` reached all P09 tests and architecture gates, then failed only because concurrently modified `language-server` produced non-reproducible archive bytes. Exact evidence `.agent/logs/P09-W02/w03-final-verify.log`; P09 module/CLI/driver tests were green.
- P09-R01 `verifyQuick` again reached green P09, archive reproducibility and architecture gates, then failed only at formatting of concurrently edited `language-server/src/test/.../P10BlackBoxTest.java`. Evidence `.agent/logs/P09-W02/r01-verifyQuick.log`.

CONTRACT_CHANGES: none. No public publication was attempted.
