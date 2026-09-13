# P06-W04 compiler driver and Java resolver

- Agent: `/root/p00_recon`
- Base revision: `0b16c5ca2c26be3fd0b9d3079fea4c80a3c0310b`
- Status: IMPLEMENTED / independent review pending
- Requirements: P06-05, P06-06, P06-07, P06-08, P06-09, P06-10, P06-11

## Delivered

- Frozen `CompileRequest`/`CompileResult`/`JavacMessage`/`CompilerDriver` API with defensive collection/byte ownership.
- Real `SourceInput → frontend → binder → deterministic emitter → javax.tools.JavaCompiler → classfile` pipeline. It uses a fresh standard file manager, explicit UTF-8, `--release 21|25`, explicit class/module paths and `-proc:none`; ambient classpath and shell javac are not used.
- Structured javac diagnostics with generated UTF-16 to code-point conversion and exact source-map overlap lookup. Unmapped diagnostics retain only a normalized generated filename; no original range is guessed.
- Unique staging, publish only after successful javac, deterministic ownership manifest with hashes, stale-owned removal only when old bytes still match, foreign collision/change refusal, traversal/nesting/symlink checks, candidate cleanup, cancellation/deadline/byte checks before publication.
- Deterministic Java path normalization in `java-resolver`.
- Required `p06EndToEnd` and `p06Release21` executable test tasks.

## Real verification

1. `./gradlew --no-daemon --dependency-verification=strict :compiler-driver:test :compiler-driver:p06EndToEnd :compiler-driver:p06Release21 --rerun-tasks`
   - Exit 0. Driver suite 7 tests; end-to-end task 7 tests; release-21 task 1 test. No failures/skips.
2. `./gradlew --no-daemon --dependency-verification=strict verifyQuick`
   - Exit 0; `VERIFY_QUICK_PASS`; 79 tasks.

The tests compile generated Teyru Java jointly with a handwritten Java consumer, launch the consumer in a separate JVM (`Ada`, exit 0), compile a direct-private-field Java negative, map a generated javac error back to its Teyru origin, exercise releases 21 and 25, compare repeated Java/manifest bytes, reject processors/future releases/cancelled requests, remove stale owned files, preserve foreign files, reject foreign collisions/nested roots, and assert staging cleanup.

## Review risks

- The in-process public JavaCompiler API cannot be forcefully interrupted while `CompilationTask.call()` is executing; cancellation/deadline is enforced immediately before and after javac. A later isolated compiler-process phase is still required for hard wall-clock termination of hostile processors/compilation.
- Publication uses atomic moves per owned file plus a final manifest, not an atomic directory exchange across the two configured output roots. Hash/collision validation prevents foreign deletion, but crash consistency across both roots needs independent fault-injection review.
- W04 validates explicit Java paths but does not claim the later full Java symbol/index resolver.
