# P06 independent full review

TASK / AGENT_ID / BASE_REVISION: `P06-REVIEW` / `/root/p00_review` / current shared `dev` candidate

STATUS: **FAILED — REJECT P06**

| ID | Decision | Independent result |
|---|---|---|
| P06-01 | **FAIL** | Java is readable and deterministic for covered fixtures, but the emitter writes `unit.imports()` verbatim: it neither sorts nor deduplicates imports as the frozen contract requires. |
| P06-02 | **FAIL** | Stored/default/custom expansion exists, but the real binder drops property initializers (`Optional.empty()`), and computed/stored classification is inferred from descendant token names rather than a complete property-storage semantic model. Required initializer/computed/visibility ABI coverage is absent. |
| P06-03 | **FAIL** | Contextual `field` is symbol-bound, but the real `ParseResult` binder constructs every general `MemberAccessExpression` with `property=Optional.empty()`. Property receiver reads/writes are demonstrated only with hand-built bound objects, so the required symbol-bound source pipeline and once-only receiver/RHS semantics are not implemented. |
| P06-04 | **FAIL** | Stable two-line header and no timestamp/absolute checkout path pass. Mapping does not: `Writer.emit()` creates one broad segment per member; identifiers, initializers, expressions, statements and boilerplate do not receive the required individual direct/expanded/synthetic segments. |
| P06-05 | **FAIL** | The public `JavaCompiler`, fresh file manager, explicit UTF-8/release/destination and `-proc:none` path works on JDK 25. The required native JDK 21 task fails during Gradle project configuration because build logic requires JVM 25; no missing-compiler seam or release-25-source-under-21 negative is present. |
| P06-06 | **FAIL** | Separate JVM execution and private-field compilation-negative exist. The trim oracle is only exercised from a hand-built bound fixture; the actual parse→bind→driver consumer uses `field = value` and input `Ada`, so it cannot prove canonical source setter trim behavior. |
| P06-07 | **FAIL** | One generated javac error is remapped only by asserting a nonempty original location. Required exact returned-expression, missing-symbol, synthetic-conflict, CRLF+emoji and Unicode-escape mappings/ranges are absent. |
| P06-08 | PASS | Repeated emission and driver publication are byte/manifest deterministic with no staging accumulation in the exercised case. |
| P06-09 | PASS | Generated `.java` is inspected and compiled by normal javac with processors disabled and without Lombok/runtime. |
| P06-10 | **FAIL** | Basic manifest, stale-owned deletion, changed/foreign collision and top-level root checks exist. Publication first deletes stale files, then moves individual files across generated/classes roots, then writes the manifest: failure can leave a partial mixed result. Descendant symlink traversal, corrupt manifest, and fault-injected atomicity are not covered. This violates the frozen all-success publication contract. |
| P06-11 | PASS | A separate compiled class is reflection-checked for private `String name`, getter return and setter parameter/return types, and absence of `getRaw`. |
| P06-12 | PASS | Reviewer-only isolated mutation changed trim lowering to direct assignment; the focused consumer/golden test failed (exit 1), proving the oracle is sensitive. |

## Executed evidence

- Java 25 strict `:compiler-core:test :compiler-driver:test --rerun-tasks`: exit 0. Current XML: core 62 tests and driver test task 7 tests, no failures/errors/skips.
- Java 25 strict `:compiler-driver:p06EndToEnd --rerun-tasks`: exit 0; seven tests, no failures/skips.
- Native JDK 21 (`/opt/jdk21/jdk-21.0.11+10`) strict `:compiler-driver:p06Release21 --rerun-tasks`: **exit 1** at configuration: `:build-logic` requires JVM 25. Evidence `.agent/logs/P06-REVIEW/p06-release21.log`.
- Java 25 strict `verifyQuick`: exit 0, 79 tasks, `VERIFY_QUICK_PASS`; its banner still honestly says later suites are not verified.
- Isolated setter mutation: focused test exit 1, one test/one failure at `P06EmitterTest.java:218`. Evidence `.agent/logs/P06-REVIEW/setter-mutation.log`.
- API golden remains fixed at `entries=938`, semantic digest `6d379fa60affd679ead4dc07a4bcc509c77e832a208d3f60de699c364b0e620f`; strict core tests exercised the bidirectional gate.

## Contract judgments

- In-process cancellation is not an additional P06 blocker by itself: processors are rejected and the frozen P06 lifecycle explicitly requires checks before javac and before publication, which are present. It still cannot claim hard mid-`CompilationTask.call()` termination; that limitation must remain explicit until process isolation.
- Multi-root publication is a P06 blocker. The frozen P06 contract requires publication of a complete successful result and atomic staging semantics; per-file moves after destructive stale cleanup do not provide that invariant.

Final result: PASS `P06-08/09/11/12`; **FAIL `P06-01/02/03/04/05/06/07/10`**. Stage exit is not met because the real source pipeline does not prove property trim/read-write behavior, source errors lack exact independent location evidence, native JDK 21 fails, and publication can be partial.

REVIEW: independent `/root/p00_review`; implementers did not approve their own work. `.idea/vcs.xml` remained excluded and untouched.

---

## Second full review after core/driver repairs — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P06-REVIEW-R2` / `/root/p00_review` / repaired shared `dev` candidate

STATUS: **FAILED — REJECT P06**

| ID | Decision | Independent result |
|---|---|---|
| P06-01 | PASS | Imports are now deterministically deduplicated/sorted by the bound unit; readable formatting, Java semicolons, source order and stable output pass. |
| P06-02 | PASS | Real parse binding retains property initializer and stored/custom/default/computed accessor structure; computed-property storage and accessor visibility tests pass. |
| P06-03 | PASS | Real `ParseResult` member reads/writes now carry `PropertyDescriptor` bindings; the real source E2E emits getter/setter calls and custom trim without text-name rewriting. Simple receiver/RHS appear once in emitted calls; unsupported expression-valued/compound forms remain fail-closed. |
| P06-04 | PASS | Header remains stable/no timestamp/absolute path. Storage/getter/setter, expressions/statements and generated boilerplate now have separate direct/expanded/synthetic source-map segments with component origins. |
| P06-05 | **FAIL** | JDK 25 public-API compiler paths and releases 21/25 pass, but the contract's native JDK 21 acceptance command still cannot configure the build: `:build-logic` requires JVM 25. Therefore the required runtime-21 matrix is not reproducible through the delivered task. |
| P06-06 | PASS | The real parse→bind→driver path now consumes `" Ada "`, runs a separate JVM and asserts `Ada`; the core suite also checks private storage rejection and reflection ABI. |
| P06-07 | PASS | Independent executed tests now assert exact code-point ranges for multiple CRLF/emoji/Unicode-escape javac errors, exact missing-symbol expression origin, synthetic owner fallback, and Java-only generated-location fallback. |
| P06-08 | PASS | Repeated Java and manifest bytes remain identical with no import/member/staging accumulation. |
| P06-09 | PASS | Normal javac compiles readable generated Java under explicit `-proc:none` without Lombok/runtime; output contains no checkout path. |
| P06-10 | **FAIL** | Rollback implementation, corrupt-manifest refusal and descendant-symlink refusal are present. However executable fault injection covers only `after-generated-move`, not `after-stale-cleanup`, `after-classes-move`, or `after-manifest`; no output-budget exhaustion or changed-owned-output test is present. The assigned all-phase/no-partial acceptance matrix is therefore not independently demonstrated. |
| P06-11 | PASS | Reflection verifies private backing field and exact getter/setter descriptors while excluding a plain-field accessor. |
| P06-12 | PASS | Fresh isolated reviewer mutation removed the real parsed trim call; `parseBindEmitJavacAndJvmEndToEnd` failed, exit 1 (one test, one failure). |

### Reproduced evidence

- Java 25 strict `:compiler-core:test :compiler-driver:test --rerun-tasks`: exit 0; core 64 and driver 12 tests, zero failures/errors/skips.
- Java 25 strict `:compiler-driver:p06EndToEnd --rerun-tasks`: exit 0; 12 tests, zero failures/skips.
- Native JDK 21 (`/opt/jdk21/jdk-21.0.11+10`) strict `:compiler-driver:p06Release21 --rerun-tasks`: **exit 1 during configuration** because build logic requires JVM 25. `.agent/logs/P06-REVIEW/r2-release21.log`.
- Java 25 strict `verifyQuick`: exit 0; 79 tasks and `VERIFY_QUICK_PASS`.
- Fresh real-source setter mutation: exit 1, `P06EmitterTest.parseBindEmitJavacAndJvmEndToEnd` failed at line 135. `.agent/logs/P06-REVIEW/r2-setter-mutation.log`.
- API golden remains fixed at `entries=938`, digest `6d379fa60affd679ead4dc07a4bcc509c77e832a208d3f60de699c364b0e620f`; core strict tests re-exercised the bidirectional gate.

Final result: PASS `P06-01/02/03/04/06/07/08/09/11/12`; **FAIL `P06-05/10`**. P06 remains rejected until the documented native-JDK-21 task succeeds and every publication phase plus changed-owned/output-budget failures proves rollback/no partial output.

REVIEW: independent `/root/p00_review`; implementers did not self-approve. `.idea/vcs.xml` remained untouched.

---

## Final focused review R05 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P06-REVIEW-R05` / `/root/p00_review` / final repaired shared `dev` candidate

STATUS: **VERIFIED — ACCEPT P06**

| ID | Decision | Final independent result |
|---|---|---|
| P06-01 | PASS | Readable deterministic Java, sorted/deduplicated imports, stable member order and required semicolons pass. |
| P06-02 | PASS | Real parsed initializer plus stored/default/custom/computed property ABI and accessor visibility pass. |
| P06-03 | PASS | Real parsed property reads/writes use bound descriptors; ordinary fields remain fields and supported evaluation emits each receiver/RHS once. |
| P06-04 | PASS | Stable header and fine storage/getter/setter/expression/statement/synthetic mappings pass without timestamp or absolute path. |
| P06-05 | PASS | Public `JavaCompiler` paths pass releases 21/25. The documented task now configures and runs successfully under a native Java 21 daemon; processor/release/cancellation guards remain fail-closed. |
| P06-06 | PASS | Separate consumer JVM proves custom trim/getter behavior; direct private access fails compilation. |
| P06-07 | PASS | Exact multi-error CRLF/emoji/Unicode, missing-expression, synthetic-owner and Java-only fallback mapping cases pass. |
| P06-08 | PASS | Repeated Java/manifest output is byte-identical and staging does not accumulate. |
| P06-09 | PASS | Generated Java remains readable and compiles under normal javac with `-proc:none`, no Lombok/runtime. |
| P06-10 | PASS | All five transaction checkpoints roll back both roots and manifest byte-identically with no partial files or staging residue. Changed-owned, output-cap, descendant-symlink, corrupt-manifest, foreign collision/preservation and nested-root negatives pass. |
| P06-11 | PASS | Reflection verifies backing-field modifiers/type and getter/setter descriptors, with no plain-field accessor. |
| P06-12 | PASS | Fresh R2 isolated trim-removal mutation made the real parse→bind→emit→consumer test fail; oracle remains sensitive. |

### Reproduced evidence

- Native Temurin 21.0.11 (`JAVA_HOME=/opt/jdk21/jdk-21.0.11+10`) documented strict `:compiler-driver:p06Release21 --rerun-tasks`: exit 0; configuration succeeded, 13 tasks executed, one tagged runtime-21 test passed with no skips. Logs: `.agent/logs/P06-REVIEW/r5-jdk21-java.log`, `r5-release21.log`.
- Java 25 strict `:compiler-driver:test :compiler-driver:p06EndToEnd --rerun-tasks`: exit 0; 13 tests in each task, zero failures/errors/skips. This includes the five-phase rollback loop and all ownership/resource/path negatives.
- Java 25 strict `verifyQuick`: exit 0; 79 tasks, `VERIFY_QUICK_PASS`.
- `build-logic/build/classes/java/main/org/javelle/buildlogic/JavaConventionsPlugin.class` is classfile major 65 (Java 21). `build-logic` retains the pinned Java 25 compiler toolchain with `options.release=21`; the successful native-21 run proves this configuration no longer blocks the daemon.
- Public API golden remains `entries=938`, semantic digest `6d379fa60affd679ead4dc07a4bcc509c77e832a208d3f60de699c364b0e620f`; prior strict core 64-test run exercised its bidirectional gate with zero failures.

Final result: PASS `P06-01..12`; **ACCEPT P06**. This accepts the P06 executable property→Java→javac vertical slice only; it does not claim later full language, joint compilation, hard process-isolated cancellation, or debugger support.

REVIEW: independent `/root/p00_review`; implementers did not self-approve. `.idea/vcs.xml` remained excluded and untouched.
