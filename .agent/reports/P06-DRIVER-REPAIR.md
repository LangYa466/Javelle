# P06-R02 driver repair

- Agent: `/root/p00_recon`
- Base revision: `0b16c5ca2c26be3fd0b9d3079fea4c80a3c0310b`
- Status: BLOCKED on P06-07 upstream source-map granularity; P06-06/P06-10 repairs implemented

## P06-06 repaired

The actual parse→bind→emit→driver fixture now contains `field = value.trim()` and sends `" Ada "` from a separately compiled Java consumer. A separate JVM prints exactly `Ada`; direct backing-field access remains a real javac negative. This no longer relies on the hand-built compiler-core fixture for trim behavior.

## P06-10 repaired

- The complete new generated/class candidate is ready before publication begins.
- Previous owned files and manifest are hash-validated and copied into the same unique transaction staging directory.
- Failures after stale cleanup, generated moves, class moves, or manifest replacement roll both output roots and the manifest back to their byte-identical prior generation.
- Added fail-closed corrupt-manifest, changed-owned, foreign collision, nested roots and descendant-symlink checks. Stale cleanup never removes foreign/changed files.
- Fault injection after the generated-root move proves the old Java, classfile and manifest are restored and no new-generation file remains.

## JDK 21 profile repaired

`:compiler-driver:p06Release21` is configured from the module build file to execute the tagged real driver test with `/opt/jdk21/jdk-21.0.11+10/bin/java`. The Gradle daemon remains on JDK 25. The forked test asserts `Runtime.version().feature() == 21`, invokes the real driver/JavaCompiler with `--release 21`, and runs the resulting consumer JVM.

## P06-07 result and ownership blocker

The driver converts javac UTF-16 positions to Unicode code points, uses exact source-map overlap and does not guess an original location. A real generated `void` property javac error maps to its Javelle source origin. The requested complete returned-expression/missing-symbol/synthetic-conflict/CRLF+emoji/Unicode-escape matrix cannot be completed inside this package: the accepted compiler-core emitter currently creates broad member segments, while a CRLF+emoji source fails during core emission with `source-map locations require UNICODE_CODE_POINT` before javac. Fixing this requires compiler-core P06-04 ownership, explicitly excluded from P06-R02. Evidence was reproduced with the proposed matrix test, then the failing out-of-contract test was not retained.

## Verification

1. JDK 25 daemon: `:compiler-driver:test :compiler-driver:p06EndToEnd :compiler-driver:p06Release21 --rerun-tasks` — exit 0; driver 9 tests, E2E 9 tests, native-JDK21 1 test; 0 failures/skips.
2. `verifyQuick` — exit 0; 79 tasks; `VERIFY_QUICK_PASS`.

Independent review is required. P06-07 must remain failed until the compiler-core map producer is repaired and the full matrix is added/reproduced.
