# P09 independent Gradle plugin review

TASK / AGENT_ID / BASE_REVISION: `P09-REVIEW` / `/root/p00_review` / shared `dev` candidate

STATUS: **FAILED — REJECT P09**

| ID | Decision | Independent result |
|---|---|---|
| P09-01 | PASS | `org.javelle` resolves as a real binary plugin with typed lazy extension and task types; consumer does not provide JavaExec glue. |
| P09-02 | PASS | Main/test Javelle source sets and both generate tasks exist; the TestKit fixture executes both generation tasks without creating source directories during configuration. |
| P09-03 | PASS | Generated main/test Java roots are attached through task providers; real `compileJava`, application run and jar succeed. |
| P09-04 | PASS | Java analysis sources are declared relative inputs, generated/classes roots are excluded, and the consumer proves Java → generated Javelle → Java without a task cycle. |
| P09-05 | **FAIL** | Process-isolated Worker and explicit `JAVA_HOME` are implemented, and `/opt/jdk21/jdk-21.0.11+10/bin/java` exists. No test runs the consumer with that native JDK 21 launcher or scans the daemon classpath/configuration phase; only JDK 25 targeting release 21 was run. Missing compiler is the sole trust negative. |
| P09-06 | **FAIL** | Fixed output paths exist, but tests only check main model/map file existence. They do not decode/validate both main/test models, assert no absolute leakage or prove no `src` mutation. Exported toolchain metadata is derived from the Gradle daemon runtime rather than the selected launcher provider. |
| P09-07 | **FAIL** | The temporary consumer uses `includeBuild`, but `GradleRunner` is not passed `--offline`; absence of Plugin Portal/network resolution is therefore not demonstrated. |
| P09-08 | **FAIL** | The fixture runs `jar`, `testClasses`, and application `run`; it never runs Gradle `test`, contains no executable test assertion, and does not launch the built consumer in a separate external Java process. |
| P09-09 | **FAIL** | Identical generation becomes `UP_TO_DATE`, configuration cache is reused, and a Javelle source edit reruns. Required Java-signature, classpath-JAR-content, compiler-content, release and option mutations are absent. Compile classpath is not exposed as a task `@Classpath` input at all. |
| P09-10 | **FAIL** | No syntax/type-error consumer exists; exact `.javelle` URI/range, build failure, and preservation of the last accepted outputs are untested. |
| P09-11 | **FAIL** | Deleted sources remove generated Java/maps and a foreign file survives. The test does not rebuild/check stale class removal and has no modified-owned, symlink or path-collision negative. Source-map mirroring deletes old owned maps before copying/writing the new manifest, without a staging rollback transaction. |
| P09-12 | **FAIL** | TestKit creates a temporary project, but no independent docs-driven fresh offline consumer run was performed with the declared JDK matrix. |

## Executed evidence

- Java 25 strict `:gradle-plugin:clean :gradle-plugin:test :gradle-plugin:p09TestKit --rerun-tasks`: exit `0`; two TestKit tests, failed/skipped `0`; 25 tasks executed. Log: `.agent/logs/P09-REVIEW/tests.log`.
- Java 25 strict `verifyQuick`: exit `1` at an actively changing P10 `language-tooling` test (`validatedWorkspacePropertyMetadataSeedsSemanticIndexWithoutFabrication`); all reached P09 tasks were green. This is recorded as a concurrent non-P09 failure, not a P09 defect. Log: `.agent/logs/P09-REVIEW/verifyQuick.log`.
- Native JDK probe: `/opt/jdk21/jdk-21.0.11+10/bin/java` exists and is executable; it was not consumed by a P09 acceptance vector.

Final result: PASS `P09-01/02/03/04`; **FAIL `P09-05/06/07/08/09/10/11/12`**. P37-only build-cache, relocation and advanced source-set work was not required. Stage exit is not met because the mandatory P09 consumer, failure, invalidation, toolchain and ownership vectors are missing.

REVIEW: independent `/root/p00_review`; implementation/ledgers and `.idea/vcs.xml` were not modified.
