# P06-R05 final driver P06-05/P06-10 repair

- Agent: `/root/p00_recon`
- Base revision: `0b16c5ca2c26be3fd0b9d3079fea4c80a3c0310b`
- Status: IMPLEMENTED / independent review pending
- Requirements: P06-05, P06-10

## Native JDK 21 Gradle execution

`build-logic` continues to use the pinned JDK 25 compiler toolchain but now emits Java 21-compatible convention-plugin bytecode with `options.release=21`. This permits the documented Gradle invocation itself—not merely its test child—to run on JDK 21.

Executed:

`JAVA_HOME=/opt/jdk21/jdk-21.0.11+10 PATH=/opt/jdk21/jdk-21.0.11+10/bin:$PATH ./gradlew --no-daemon --dependency-verification=strict :compiler-driver:p06Release21 --rerun-tasks`

Exit 0. The tagged test additionally asserts runtime feature 21 and runs the real driver, public `JavaCompiler --release 21`, and consumer JVM.

## Transaction failure matrix

The test fault-injects each implemented commit phase:

1. after stale cleanup;
2. after generated Java moves;
3. after classfile moves;
4. immediately before manifest replacement;
5. immediately after manifest replacement.

For every phase it verifies byte-identical restoration of old Java/class/manifest, absence of all new-generation files, and removal of the unique transaction directory. Separate executable cases verify changed-owned output refusal, output-budget exhaustion with no publication, descendant symlink rejection, corrupt-manifest refusal, foreign preservation and normal stale cleanup.

## Full verification

- JDK 25: `:compiler-driver:test :compiler-driver:p06EndToEnd --rerun-tasks` — exit 0; 13 + 13 tests, 0 failures/errors/skips.
- Native JDK 21 command above — exit 0; 1 test, 0 failures/errors/skips.
- JDK 25 `verifyQuick` — exit 0; 79 tasks; `VERIFY_QUICK_PASS`.

No compiler target or product language contract changed. Independent fault/recovery review remains required before acceptance.
