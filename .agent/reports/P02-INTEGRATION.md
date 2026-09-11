# P02-R04 — integration wiring

TASK / AGENT_ID / BASE_REVISION: `P02-R04` / `/root/p00_git_publish` / `29321d8479e92ac05b1f1ec963636464b24faee8`

STATUS: **IMPLEMENTED — independent review pending**

CHANGED_PATHS: root build wiring, `build-logic` architecture test wiring, `.agent/tools/governance.py`, one fixed Gradle verification hash, coordinator ledgers, and this report. R01/R02/R03 implementation paths are integrated unchanged except where required by these wiring defects. `.idea/vcs.xml` was excluded.

CONTRACT_CHANGES: none; P02 infrastructure only. Boundary marker modules are not product feature claims.

## Integrated gates

- `verifyQuick` executes semantic governance validation/selftests, LF/CRLF checks, governance archive normalization, exact project DAG, compiled source/bytecode architecture inspection, standalone architecture negative fixtures, all subproject checks, and the 14-test testkit suite.
- Atomic ledger selftest now injects failure after file fsync and proves prior-byte retention; a marker-synchronized pair of writers proves the file lock serializes updates and the stale fingerprint fails rather than overwriting.
- Product archive reproducibility creates two independent clean repository copies, runs actual Gradle `clean jar` builds, and compares SHA-256 maps for all 16 produced JARs. It does not substitute a synthetic governance ZIP fixture for product evidence.
- Strict metadata gained the fixed Gradle-plugin classpath `junit-bom-5.14.2.module` hash required by isolated clean builds.
- `verifyAll` and `releaseCheck` retain explicit, nonzero `INCOMPLETE` behavior.

## Executed acceptance

| Check | Exit | Result |
|---|---:|---|
| `governance.py validate/selftest/line-endings` | 0 | schemas/requirements/worktree policy, semantic negatives, atomic fingerprint/fsync/concurrency, and line endings pass |
| strict `verifyQuick` | 0 | `BUILD SUCCESSFUL` in 35s; 74 tasks; compiled architecture and all integrated gates pass |
| product archive gate inside `verifyQuick` | 0 | 16 actual JARs from two clean builds are byte-identical |
| standalone `architectureBoundaryTest` | 0 | forbidden source families and descriptor-only bytecode negative emit `ARCHITECTURE_NEGATIVES_OK` |
| isolated `TASKS.json={}` | nonzero expected | rejected with `tasks schema`; the semantic corruption no longer greens |
| strict `verifyAll` | nonzero expected | named `INCOMPLETE` gates retained |
| strict `releaseCheck` | nonzero expected | named `INCOMPLETE` release gates retained |

Evidence: ignored `.agent/logs/P02-INTEGRATION/`.

REVIEW: pending independent reviewer; implementer does not approve P02.

RISKS_OR_BLOCKERS: remote CI remains `CI_NOT_RUN`; cross-OS checks and later product suites remain assigned downstream. P03 stays locked until review acceptance.

NEXT_DEPENDENCIES: independent clean-checkout P02 review, then integration acceptance only if all P02 requirements pass.

REPORT_PATH: `.agent/reports/P02-INTEGRATION.md`

## P02-R06 focused strict-verification repair

The clean isolated build requested `com.google.guava:guava-parent:32.1.3-jre` metadata that was not covered by strict verification. The fixed Maven Central POM at `https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/32.1.3-jre/guava-parent-32.1.3-jre.pom` was downloaded and hashed as `f283c1f04897a9a88a3fa4ff46804e65e82114809a09cd04094bf7de01b1857b`; that exact artifact entry was added. No group wildcard, trusted-key bypass, or verification disablement was introduced.

STATUS: **IMPLEMENTED — fresh isolated verification passed; independent review pending**

Fresh candidate verification excluded `.git`, `.idea`, build/cache/log/tmp, and `node_modules`, used an empty isolated `GRADLE_USER_HOME` plus `/usr/lib/jvm/java-25-openjdk`, and ran strict `verifyQuick`. Result: exit 0, `BUILD SUCCESSFUL in 1m 40s`, 74/74 tasks executed, testkit 14 tests with 0 failures/errors/skips, and 16 actual product JARs byte-identical across the two clean builds. No additional missing verification artifact appeared.

Evidence: `.agent/logs/P02-INTEGRATION/r06-fresh-verifyQuick.txt`; fixed POM bytes under ignored `.agent/tmp/P02-R06/`.
