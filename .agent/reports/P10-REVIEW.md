# P10 minimal LSP independent review

TASK / AGENT_ID / BASE_REVISION: `P10-REVIEW` / `/root/p00_review` / current shared `dev` candidate

STATUS: **FAILED — REJECT P10**

| ID | Decision | Independent result |
|---|---|---|
| P10-01 | PASS | Clean standalone `teyru-lsp --stdio` distribution exists, contains only Teyru/core/workspace jars, and language-server has no IntelliJ dependency/class. |
| P10-02 | **FAIL** | Basic framing and initialize/shutdown/exit work, but lifecycle collapses INITIALIZING into initialized immediately, so requests before the `initialized` notification are accepted. Invalid params/runtime dispatch errors are caught as JSON parse errors. Strict body UTF-8 is absent: an externally sent `0xff` inside a JSON string received a successful initialize response. |
| P10-03 | **FAIL** | Open/change/close and atomic UTF-16 edits use compiler-driver/core. Snapshot identity contains only URI/version/text fingerprint; it lacks required workspace-model and overlay-analysis fingerprints, and document URIs are not normalized. |
| P10-04 | **FAIL** | Initialize response has the minimal syntactic allowlist and denylist, but advertised hover/completion are not real semantic handlers. The authoritative 95-method inventory still marks initialize/lifecycle/open/change/close/diagnostics/hover/completion as `NOT_IMPLEMENTED` and `advertised=false`, contradicting runtime advertisement. |
| P10-05 | **FAIL** | Driver-backed diagnostics and close clearing exist, but the external suite never asserts a nonempty syntax diagnostic's exact URI/version/UTF-16 range followed by a fixed newer-version empty publish. It only observes a close clear, so the required fix-and-clear mapping vector is not independently proven. |
| P10-06 | **FAIL** | Workspace model is decoded/validated then discarded. It never seeds symbols. Completion returns one fixed list for every document; hover returns `Teyru symbol <word>` for any arbitrary identifier. This is explicitly forbidden fake support. |
| P10-07 | PASS | External tests parse framed stdout and force malformed transport; logs/errors stay on stderr. No unframed stdout was observed. |
| P10-08 | **FAIL** | Version/current-snapshot guards exist, but `$/cancelRequest` is an empty handler with no request registry/token and no deterministic cancel race test. |
| P10-09 | **FAIL** | Several malformed headers/JSON and partial EOF are bounded, but invalid UTF-8 is accepted after replacement decoding. Unknown request/notification, post-shutdown request, non-decimal/negative lengths and invalid-param codes are not covered by external assertions. |
| P10-10 | PASS | All server tests launch the installed executable and exchange raw framed bytes; they do not invoke handlers directly. |
| P10-11 | **FAIL** | Ten restart cycles exit 0, but no descendant PID, open-handle or post-exit sustained-CPU checks are performed as required. |
| P10-12 | PASS | The installed distribution runs externally with no IntelliJ jar/class in its runtime and no IDEA installation dependency. This is an independence result, not IDEA integration evidence. |

## Executed evidence

- Java 25 strict clean `:language-server:installDist :language-tooling:test :language-server:test --rerun-tasks`: exit 0; tooling 1 and external server 3 tests, zero failures/errors/skips; 25 tasks executed. `.agent/logs/P10-REVIEW/tests.log`.
- Java 25 strict `verifyQuick`: exit 0; 98 tasks and `VERIFY_QUICK_PASS`. `.agent/logs/P10-REVIEW/verifyQuick.log`.
- Independent raw client sent a correctly framed body containing invalid UTF-8 byte `0xff`; server returned a successful initialize result instead of deterministic rejection. This proves the body decoder is not strict.
- Structural audit: `new String(body, UTF_8)` performs replacement decoding; `$/cancelRequest -> {}`; workspace validation retains no model; completion is an unconditional fixed 12-item list; hover's default branch accepts any word.
- Inventory remains exactly 95 methods, but every currently handled lifecycle/document/language method inspected remains `supportStatus=NOT_IMPLEMENTED`, `advertised=false`; `$ /cancelRequest` (without the space) is also correctly still NOT_IMPLEMENTED in inventory and implementation.

Final result: PASS `P10-01/07/10/12`; **FAIL `P10-02/03/04/05/06/08/09/11`**. Stage exit is not met. Implement strict UTF-8/lifecycle/error states, semantic workspace-backed hover/completion, full snapshot identity, cooperative request cancellation, the missing external vectors/resource checks, and synchronize only truly proven inventory rows.

REVIEW: independent `/root/p00_review`; implementer did not self-approve. `.idea/vcs.xml` remained excluded and untouched.

---

## R2 — post-repair independent full review (authoritative)

STATUS: **FAILED — REJECT P10**

| ID | Decision | R2 independent result |
|---|---|---|
| P10-01 | PASS | The clean installed `teyru-lsp --stdio` launcher starts as a separate process; its distribution has no IntelliJ jar/class dependency. |
| P10-02 | PASS | Strict framing, invalid UTF-8 rejection, bounded lengths, the five lifecycle states, request IDs, `-32602/-32601/-32600/-32002`, shutdown/exit and one terminal response are now exercised by external-process tests. |
| P10-03 | PASS | Open/change/close use the shared compiler facade; URI normalization, monotonically increasing versions, UTF-16/CRLF edits, atomic invalid-edit rejection, and document/workspace/overlay fingerprints are represented in immutable snapshots. |
| P10-04 | PASS | Initialize advertises only six implemented capabilities and omits definition/pull diagnostics and later families. The authoritative inventory has exactly 95 unique methods, exactly 12 `VERIFIED`, and exactly six `advertised=true`; no unsupported method gained a claim. |
| P10-05 | **FAIL** | Original URI/version, nonempty-to-fixed-empty publication and close clearing are tested. However neither the server external test nor the tooling test asserts the emitted diagnostic's exact UTF-16 start **and end** range for the emoji/CRLF mapped parser fixture; the tooling assertion checks only start line `1`. The frozen contract requires original URI/range evidence, so this is not independently proven. |
| P10-06 | **FAIL** | Hover/completion are no longer fixed/string-search fakes: emitted Java is parsed through `JavaCompiler`/`JavacTask`, unknown hover is empty, and `String` plus the current source/property are derived. But the retained P08 workspace model contributes only its fingerprint; classpath, toolchain and property metadata are not used to seed the semantic index. There is no valid local workspace-symbol fixture. This does not meet the frozen requirement that workspace/JDK/classpath truth come from the validated workspace model. |
| P10-07 | PASS | External clients parse every stdout byte as JSON-RPC frames while malformed/error paths are forced; no unframed stdout was observed. |
| P10-08 | **FAIL** | Current-snapshot/version publication guards exist and queued cancellation returns exactly one `-32800`. The required deterministic slow-analysis/running cancellation vector is absent: the test cancels only a queued hover, and compiler/JavacTask analysis has no cooperative cancellation check or deterministic delay hook at parse/query boundaries. Therefore the cancel race and old-task publication rule are not fully proven. |
| P10-09 | PASS | External tests cover malformed/truncated headers and JSON, invalid UTF-8, partial EOF, unknown request/notification, invalid params and post-shutdown requests with bounded termination. |
| P10-10 | PASS | The 12 server tests launch the installed distribution and exchange independently framed raw bytes; no handler is invoked directly. |
| P10-11 | PASS | Ten restart cycles each assert exit code 0, dead PID, no live descendants, absent `/proc/<pid>/fd`, and a ten-second bound. A dead process cannot retain sustained CPU. |
| P10-12 | PASS | The standalone distribution smoke succeeds from the JVM environment without loading or distributing IDEA classes; this is LSP independence evidence only. |

### R2 executed evidence

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew --no-daemon --dependency-verification=strict :language-server:clean :language-server:installDist :language-tooling:test :language-server:test --rerun-tasks`: exit `0`; tooling `2`, server external-process `12`, failed/skipped `0`. Log: `.agent/logs/P10-REVIEW/r2-tests.log`.
- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew --no-daemon --dependency-verification=strict verifyQuick`: exit `0`; `98` tasks and `VERIFY_QUICK_PASS`. Log: `.agent/logs/P10-REVIEW/r2-verifyQuick.log`.
- Inventory parse: `95` unique rows; status counts `12 VERIFIED / 83 NOT_IMPLEMENTED`; advertisement count `6`; the verified set is lifecycle, cancellation, open/change/close/save, publish diagnostics, hover and completion only.
- Structural semantic audit: `DocumentWorkspace` calls `CliCompilerFacade`, then parses generated Java with public `javax.tools.JavaCompiler`/`JavacTask`/`TreeScanner`; there is no second grammar parser, fixed completion list, or arbitrary-word hover fallback. The same audit found workspace-model use only at snapshot fingerprinting and no interruption/token check inside analysis.

Final R2 result: PASS `P10-01/02/03/04/07/09/10/11/12`; **FAIL `P10-05/06/08`**. Stage exit remains unmet. Required repairs are bounded: assert exact diagnostic UTF-16 start/end externally; seed and test symbols from a valid P08 model; add cooperative running-analysis cancellation plus a deterministic race/publication test.

REVIEW: independent `/root/p00_review`; implementation and ledgers were not modified; `.idea/vcs.xml` remained excluded.

---

## R3 — final focused review after R02 repairs (authoritative)

STATUS: **FAILED — REJECT P10**

| ID | Decision | Final focused result |
|---|---|---|
| P10-01 | PASS | Standalone installed stdio distribution and IntelliJ-free boundary remain green. |
| P10-02 | PASS | Framing, strict UTF-8, lifecycle, IDs and error behavior remain green in the 12-process suite. |
| P10-03 | PASS | Normalized URI, immutable unsaved text, increasing versions and document/workspace/overlay fingerprints remain implemented and tested. |
| P10-04 | PASS | Capability response still matches the minimal allowlist; inventory remains 95 unique methods, 12 VERIFIED and six advertised, with no broader claims. |
| P10-05 | PASS | External process now asserts original URI, version 1, exact emoji/CRLF UTF-16 range start `(1,17)` and end `(1,18)`, followed in order by version 2 with an empty diagnostic list. |
| P10-06 | **FAIL** | Validated property metadata now seeds completion/hover and unknown hover is empty. However the P08 model's source roots, compile classpath and selected toolchain are not consumed for symbol resolution; `String` still comes from the ambient JVM and the sole valid-model test uses metadata only. The frozen contract requires workspace/JDK/classpath truth from the validated model and a valid local workspace-symbol fixture. |
| P10-07 | PASS | Framed stdout purity remains green. |
| P10-08 | **FAIL** | `DocumentWorkspace` now has cooperative interruption checkpoints and an injected checkpoint unit test. The external race still does not inject a deterministic running-analysis delay: it sends hover/cancel behind didOpen on the single worker, so cancellation may only cancel queued work. Thus a running request's single `-32800` terminal response and stale-old/new-wins publication race are not deterministically reproduced as required by BB-07. |
| P10-09 | PASS | Malformed, EOF, unknown and post-shutdown vectors remain green. |
| P10-10 | PASS | All server acceptance tests continue to use the installed external process and raw frames. |
| P10-11 | PASS | Ten-cycle PID/descendant/fd/time cleanup checks remain green. |
| P10-12 | PASS | JVM-only standalone distribution smoke remains green without an IDEA runtime dependency. |

### Final focused evidence

- Java 25 strict clean installDist + `:language-tooling:test :language-server:test --rerun-tasks`: exit `0`; tooling `4`, server external-process `12`, zero failures/skips; 25 tasks executed. Log `.agent/logs/P10-REVIEW/r3-focused.log`.
- Strict `verifyQuick`: exit `1` after P10 tests passed, at concurrently changing `:gradle-plugin:test` because its binary result file disappeared (`NoSuchFileException ... in-progress-results-generic.bin`). This is a P09 concurrent-build collision, not a P10 regression. Log `.agent/logs/P10-REVIEW/r3-verifyQuick.log`.
- Structural review confirms model property metadata consumption and compiler/JavacTask indexing, but no root/classpath/toolchain use; cooperative interruption checks occur after compiler completion and around the injectable checkpoint, while the server black-box launch exposes no delay seam.

Final decisions: PASS `P10-01/02/03/04/05/07/09/10/11/12`; **FAIL `P10-06/08`**. P10 stage exit remains unmet.

---

## R4 — independent post-implementation review

STATUS: **PASS — ACCEPT P10**

| ID | Decision | R4 independent result |
|---|---|---|
| P10-01 | PASS | `teyru-lsp --stdio` installs and launches as a standalone external process (`:language-server:installDist`); `language-server` depends only on `language-protocol`, `language-tooling`, `compiler-driver`/`compiler-core`/`workspace-model` — no IntelliJ jar/class present. |
| P10-02 | PASS | Strict UTF-8 body decoding, bounded/duplicate/negative/truncated Content-Length handling, the five lifecycle states, JSON-RPC error codes (`-32600/-32601/-32602/-32603/-32002/-32800`), and single terminal response per id remain implemented and covered by `P10BlackBoxTest`'s 12-case external suite. |
| P10-03 | PASS | `DocumentWorkspace` snapshots are immutable records carrying `uri`, `version`, `text`, document/workspace/overlay `fingerprint`s (`DocumentWorkspace.Snapshot`); `normalizeUri` rejects non-absolute/query/fragment URIs; UTF-16/CRLF/surrogate-interior boundaries are exercised by `utf16CrLfIncrementalChangeIsAtomicAndRejectsSurrogateInterior`. |
| P10-04 | PASS | `capabilities()` in `TeyruLanguageServerMain` advertises exactly `positionEncoding=utf-16`, incremental sync with `didSave.includeText=false`, `hoverProvider=true`, `completionProvider{resolveProvider=false}` — nothing else. `compatibility/lsp-methods.json` independently verified: 95 unique methods, exactly 12 `VERIFIED`, exactly 6 `advertised=true`, and zero methods where `advertised=true` but `supportStatus!=VERIFIED` (checked via direct JSON parse, not by trusting the file's own claims). |
| P10-05 | PASS | `compilerBackedSyntaxDiagnosticUsesRawUtf16` and the server-level diagnostic fixture assert the exact UTF-16 range for CRLF/emoji-adjacent content and a subsequent empty-array clear at a strictly newer version, against the original `.teyru` URI. |
| P10-06 | **PASS (previously FAIL in R2/R3 — now repaired)** | `DocumentWorkspace.semanticIndex` now genuinely consumes the validated P08 workspace model: `workspaceClasspath` feeds `StandardLocation.CLASS_PATH`, `workspaceRoots(..., false)` feeds `StandardLocation.SOURCE_PATH`, and `workspaceRelease` derives `--release` from the model's declared toolchain/source-set release (throwing `IllegalArgumentException` when the release exceeds the toolchain). Independently re-read and exercised `P10DocumentWorkspaceTest.resolvedRootsClasspathAndToolchainDriveSymbols`: it builds a real external `external.jar` (compiled to a temp dir with `--release 21`, then jarred) plus a real root-only Java source, resolves both into the workspace model's `PathRef`s, and proves (a) with both root+classpath present, completion/hover find `RootSymbol` from the source root, `ExternalOnly`/`fromJar` from the *jar-only* classpath, and hover for `RootSymbol` resolves via the AST rather than a fixed string; (b) with classpath emptied, `ExternalOnly` disappears while `RootSymbol` remains; (c) with the root emptied, `RootSymbol` disappears while `ExternalOnly` remains; (d) an incompatible toolchain (`17.0.1` vs. the real running JDK release) makes `analyze()` throw `IllegalArgumentException` rather than silently falling back. This is a genuine root/classpath/toolchain-driven symbol resolution test, not fabricated/fixed output — the jar-only and root-only halves individually failing/passing rules out coincidental resolution from the ambient JVM classpath. `validatedWorkspacePropertyMetadataSeedsSemanticIndexWithoutFabrication` additionally proves property-metadata-derived hover/completion from a checked-in P08 fixture, with an unknown position returning empty (no arbitrary-word fallback). |
| P10-07 | PASS | External black-box tests parse every stdout byte as JSON-RPC frames while forcing malformed transport/errors; no unframed stdout observed. |
| P10-08 | **PASS (previously FAIL in R2/R3 — now repaired)** | `DocumentWorkspace` exposes an injectable `analysisCheckpoint` (production default is a no-op; `testAnalysisDelayFromEnvironment()` reads `TEYRU_LSP_TEST_ANALYSIS_MARKER`/`TEYRU_LSP_TEST_ANALYSIS_CHECKPOINT` env vars to block a specific numbered checkpoint call until the worker thread is interrupted). `analyze()` calls this checkpoint and `cancelled()` (which throws `CancellationException` if `Thread.currentThread().isInterrupted()`) both before and after the compiler stage. The server (`TeyruLanguageServerMain`) runs all analysis/request work on a single-thread `ExecutorService`, tracks each request in a `pending` map as a `FutureTask`, and `$/cancelRequest` calls `task.cancel(true)` (a real thread interrupt) then races a `terminal` `Set` against the normal completion path to guarantee exactly one terminal response. Independently re-read and re-ran (thrice) `P10BlackBoxTest.cancelledOldAnalysisCannotPublishAndNewerDiagnosticWinsOnce`, which sets `TEYRU_LSP_TEST_ANALYSIS_CHECKPOINT=3` (the first checkpoint call belonging to the second, hover-triggered `analyze()` invocation — i.e. the worker thread has genuinely dequeued and begun the hover's analysis, not merely queued it), waits on a real marker file to prove the worker is blocked *inside* that running analysis, then sends `$/cancelRequest` for the hover concurrently with a newer `didChange`. It asserts, in the order actually observed off the wire: exactly one `-32800` response for the cancelled hover id, and exactly one `publishDiagnostics` at the strictly newer version (never the stale intermediate version, never a duplicate of either). The tooling-level unit test `cooperativeCancellationStopsAtInjectedCompilerStageCheckpoint` independently confirms the same interrupt→`CancellationException` behavior at the `DocumentWorkspace` layer alone. This is a deterministic race on a real running-analysis interrupt, not a queued-task cancellation dressed up as one — a materially different and stronger test than R2/R3 observed. |
| P10-09 | PASS | Malformed/truncated headers, invalid UTF-8, partial EOF, unknown request/notification, invalid params, and post-shutdown requests remain covered and green. |
| P10-10 | PASS | All server acceptance tests launch the installed `teyru-lsp` executable and exchange independently framed raw bytes; no test invokes a handler directly. |
| P10-11 | PASS | Ten-cycle restart/PID/descendant/`/proc/<pid>/fd`/timing checks remain green. |
| P10-12 | PASS | The installed distribution runs standalone on a bare JVM host with no IDEA classes/runtime dependency. |

### R4 executed evidence

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH ./gradlew --no-daemon --dependency-verification=strict :language-server:clean :language-server:installDist :language-tooling:test :language-server:test --rerun-tasks` — exit `0`; `language-tooling` `TEST-dev.teyru.language.tooling.P10DocumentWorkspaceTest.xml` reports `tests="5" failures="0" errors="0"`; `language-server` `TEST-dev.teyru.language.server.P10BlackBoxTest.xml` reports `tests="12" failures="0" errors="0"`.
- Re-ran `:language-tooling:test :language-server:test --rerun-tasks` a second full time and `:language-server:test --rerun-tasks` alone two further times (four independent runs of the cancellation-sensitive suite in total) — all green, no flakiness observed in the deterministic checkpoint/marker-file cancellation race.
- `compatibility/lsp-methods.json` parsed directly with Python (not `jq`, since the file is a keyed object rather than a bare array): `methodCount=95`, actual method entries `95`, `Counter({'NOT_IMPLEMENTED': 83, 'VERIFIED': 12})`, `advertised=true` count `6` (`textDocument/completion`, `didChange`, `didClose`, `didOpen`, `didSave`, `hover`), zero methods advertised without `VERIFIED` status.
- Read `language-tooling/src/main/java/dev/teyru/language/tooling/DocumentWorkspace.java` and `language-server/src/main/java/dev/teyru/language/server/TeyruLanguageServerMain.java` in full, and cross-checked every assertion in `P10DocumentWorkspaceTest.java` and the relevant section of `P10BlackBoxTest.java` against the frozen `P10-CONTRACT.md` requirements (workspace/JDK/classpath truth from the validated model; cooperative cancellation of genuinely running analysis with correct stale/newer publication ordering) line by line — no fabricated/fixed/string-matched fallback behavior remains in either hover or completion, and the cancellation mechanism operates on a real interrupted worker thread rather than a queued-task shortcut.

Final R4 result: **ACCEPT P10**. Both defects that blocked R2/R3 (`P10-06` symbol resolution not actually using workspace roots/classpath/toolchain; `P10-08` cancellation never proven against genuinely running analysis) are independently confirmed repaired with real, non-fabricated evidence, and all other previously-passing rows remain green across repeated runs. No further gaps were found against the frozen `P10-CONTRACT.md`/`P10-MINIMAL-SERVER.md` acceptance matrix.

REVIEW: independent reviewer, no prior context on this implementation; `.idea/vcs.xml` and all other project files outside the two `*-REVIEW.md` reports were left untouched; no commits were made.
