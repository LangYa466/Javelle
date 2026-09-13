# P08 workspace-model independent review

TASK / AGENT_ID / BASE_REVISION: `P08-MODEL-REVIEW` / `/root/p00_review` / current shared `dev` candidate

STATUS: **FAILED — REJECT MODEL LAYER; whole P08 not reviewed**

| ID | Decision | Model-layer result |
|---|---|---|
| P08-01 | **FAIL** | Dependency-free typed DTO/codec and immutable list/map copies exist, and strict JSON rejects duplicate keys, malformed UTF-8 and wrong primitive types. There is no workspace-model public API golden/drift gate, and the five-test suite does not exercise every field's copy/round-trip contract or all-classfile forbidden references. Root compiled-architecture coverage exists but the required root gate currently fails earlier on an unrelated graph mismatch. |
| P08-02 | **PENDING INTEGRATION** | No real Gradle main/test exporter is in this model package; empty/missing roots are represented but not exported from a consumer project. |
| P08-03 | **FAIL** | Resolved absolute URI relocation is excluded from fingerprints in the covered case. Portable writing only checks URI optionals; it does not validate/reject an absolute/user-home/JDK-looking `logicalPath` before serialization. URI query/fragment/authority/archive separator normalization is incomplete. |
| P08-04 | **FAIL** | Compile/runtime/processor/module/source-JAR paths, releases, options and trust round-trip structurally. Validation contains an empty processor-path authorization branch and does not compare release 25 against a resolved Java 21 toolchain, so security/toolchain consistency fails. |
| P08-05 | **FAIL** | Basic traversal, exact/case-insensitive duplicates, missing resolved paths and rejected symlinks exist. `CaseSensitivity.UNKNOWN` does not report ambiguity, descendant source-root overlap is not detected, URI query/fragment/archive/authority cases are accepted, and containment/duplicate boundary coverage is incomplete. |
| P08-06 | **FAIL** | Component fingerprints cover many semantic fields and ignore resolved absolute relocation. They omit `onDiskSnapshotFingerprint`; set-like source roots are hashed in input order instead of canonical logical identity. The suite does not mutate classpath/JDK/options/config/trust/property fields one by one as required. |
| P08-07 | **PENDING INTEGRATION** | No external CLI process consumes this model without build evaluation yet. |
| P08-08 | **FAIL** | Missing BUILD_ORDER targets and stable build-cycle detection exist. SOURCE_VISIBILITY edges are merely excluded from the build graph; no distinct SCC report/result is produced, and target source-set identity is not validated. |
| P08-09 | **FAIL** | Overlay DTOs are separated from source roots, but there is no overlay-set/analysis fingerprint, version monotonicity, normalized URI validation, content-hash check or stale-base rejection. |
| P08-10 | **FAIL** | No checked-in portable model golden/example plus automated absolute POSIX/drive/UNC/user-home/JDK-path scan was delivered; the writer's incomplete portable check cannot substitute for this requirement. |
| P08-11 | **FAIL** | Duplicate keys, unknown major, malformed UTF-8, limits and enum parsing fail closed. New-minor additive fields survive only in `WorkspaceReadResult.rawDocument`; converting the typed model back to JSON drops them, so lossless forward round-trip is not met. Stale fingerprint is diagnosed only generically; migration/default and refresh-result coverage is incomplete. |
| P08-12 | **PENDING INTEGRATION** | No independent plain-Java published-JAR client was executed; unit tests use the project test runtime. |

## Executed evidence

- Java 25 strict `./gradlew --no-daemon --dependency-verification=strict :workspace-model:test --rerun-tasks`: exit 0; five tests, zero failures/errors/skips. Log: `.agent/logs/P08-REVIEW/workspace-model-test.log`.
- Java 25 strict `verifyQuick`: **exit 1** at `verifyArchitecture`. Actual candidate graph contains `compiler-cli -> compiler-core`, while the expected architecture map does not. This is outside workspace-model ownership, but the common gate is not green. Log: `.agent/logs/P08-REVIEW/verifyQuick.log`.
- Structural negative review confirmed `if (!processorPath.isEmpty() && !allowProcessors()) {}` has an empty body; toolchain validation only accepts version prefixes and never enforces `release <= toolchain`; overlay validation only checks duplicate raw URI and inline byte length.
- `WorkspaceModelFingerprinter` excludes resolved absolute URIs as intended, but also excludes on-disk snapshot fingerprints and hashes set-like roots in supplied order. Unknown additive top-level fields are held only in `rawDocument`, outside typed reserialization.
- Production workspace-model declares no Gradle/IDE/LSP dependency; source scan found only the intentional forbidden-reference patterns in the root architecture checker, not in model production code. This does not replace the missing API drift and full classfile evidence.

## Scope decision

No whole-stage P08 decision is made. Model-owned IDs `P08-01/03/04/05/06/08/09/10/11` are not acceptable. `P08-02/07/12` explicitly remain integration-pending and were not failed as model implementation work.

REVIEW: independent `/root/p00_review`; implementer did not self-approve. `.idea/vcs.xml` remained excluded and untouched.

---

## Second review: repaired model plus P08-02/12 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P08-REVIEW-R2` / `/root/p00_review` / repaired shared `dev` candidate

STATUS: **FAILED — one model blocker remains; P08-07 PENDING**

| ID | Decision | Current independent result |
|---|---|---|
| P08-01 | **FAIL** | Every frozen DTO field round-trips, collections are copied, and every production classfile is scanned for Gradle/IDE/LSP references. The checked-in API golden fixes only `WorkspaceModel` and nested record components; it omits the frozen public methods/types (`WorkspaceModelCodec`, `WorkspaceModelValidator`, fingerprinter, diagnostics/read options/results). Adding/removing/changing one of those methods does not affect the golden, so the requested API drift gate is incomplete. |
| P08-02 | PASS | Real Gradle `ProjectBuilder` capture exports main/test for `:app` and `:lib`, retains empty and missing roots, captures portable toolchain data, round-trips through the shared codec, and registers no P09 Teyru tasks. |
| P08-03 | PASS | Portable/resolved separation, relocation-stable fingerprints, unsafe absolute/drive/query/fragment/archive paths and URI/containment rules are executable and green. |
| P08-04 | PASS | All compile/runtime/processor/module/source-JAR/toolchain/release/options/trust fields round-trip; processor authorization and release-vs-toolchain negatives pass. |
| P08-05 | PASS | Empty/missing roots, decoded traversal, exact/case-unknown duplicates, descendant overlap, URI shape, symlink/containment validation and exact diagnostic families pass. |
| P08-06 | PASS | Root order is canonical while compiler path order remains semantic; snapshot, classpath, options, trust, toolchain and property mutations invalidate the appropriate fingerprints, and absolute relocation does not. |
| P08-07 | **PENDING** | Deliberately deferred until the packaged CLI repair is independently accepted; no decision in this review. |
| P08-08 | PASS | Missing targets/source sets, stable BUILD_ORDER cycle and separately reported SOURCE_VISIBILITY SCC behavior pass. |
| P08-09 | PASS | Overlay URI/version/content/base validation and separate analysis fingerprint pass without altering the on-disk model fingerprint. |
| P08-10 | PASS | Fixed portable golden is canonical and scanned for POSIX/drive/UNC/user-home/JDK leaks; portable writer rejects URI-like/absolute logical paths. |
| P08-11 | PASS | Duplicate/type/depth/size/UTF-8/unknown-major/security-enum failures are bounded; newer-minor unknown additive top-level data survives read → typed model → canonical portable serialization. |
| P08-12 | PASS | The integration test builds the actual workspace-model JAR, compiles a standalone client with `javac --release 21`, and launches it with only client classes plus that JAR; output is exactly `EXTERNAL_WORKSPACE_OK`, with byte-identical model round-trip. |

### Reproduced evidence

- Java 25 strict `:workspace-model:test :gradle-plugin:test --rerun-tasks`: exit 0; model 12 tests and exporter/client 3 tests, zero failures/errors/skips; 15 tasks executed. Log `.agent/logs/P08-REVIEW/r2-model-exporter.log`.
- External-client assertions use the built `workspace-model-0.1.0-SNAPSHOT.jar`, `javac --release 21`, a separate JVM/process, and no Gradle/compiler JAR on its runtime classpath.
- `verifyQuick`: exit 1 in an actively modified CLI/driver formatting check (`CliCompilerFacade` wrapping), after relevant model/exporter tests were independently green. This failure is outside P08 ownership and is reported rather than attributed to the model. Log `.agent/logs/P08-REVIEW/r2-verifyQuick.log`.
- `workspace-public-api.txt` contains 15 lines, exclusively the top-level/nested record shapes. `javap` confirms public codec/validator/fingerprinter methods exist but are absent from the golden.

Current result: PASS `P08-02/03/04/05/06/08/09/10/11/12`; **FAIL `P08-01`**; PENDING `P08-07`. Whole P08 cannot be accepted yet. Expand the fixed API golden/gate to every public model class, constructor and method, then rerun; complete P08-07 after CLI repair.

REVIEW: independent `/root/p00_review`; implementers did not self-approve. `.idea/vcs.xml` remained excluded and untouched.

---

## Final review R02 including P08-07 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P08-REVIEW-R02` / `/root/p00_review` / current shared `dev` candidate

STATUS: **VERIFIED — ACCEPT P08**

| ID | Decision | Final independent result |
|---|---|---|
| P08-01 | PASS | All DTO/codec/validator/fingerprinter public types, constructors, fields and methods are frozen by a 422-line golden with add/delete/signature mutation checks; every production classfile is scanned for Gradle/IDE/LSP references. |
| P08-02 | PASS | Real ProjectBuilder exporter captures app/lib main/test plus empty/missing roots and portable toolchain data without adding P09 tasks. |
| P08-03 | PASS | Portable/resolved paths, relocation-stable keys and traversal/URI/absolute-path negatives pass. |
| P08-04 | PASS | Compiler paths, source JARs, toolchain/release/options and processor trust round-trip and validate fail-closed. |
| P08-05 | PASS | Empty/missing, duplicate/case-unknown, overlap, symlink and containment cases have stable diagnostics. |
| P08-06 | PASS | Configuration/classpath/toolchain/options/property/snapshot/trust mutations invalidate fingerprints; root order and absolute relocation behave as contracted. |
| P08-07 | PASS | Packaged CLI consumes the shared model through compiler-driver only. Reviewer-generated valid two-root model enumerated and compiled both roots with exit 0; invalid version/trust/path cases are rejected by the same bounded codec before enumeration. No Gradle/build/network execution path exists in the facade. |
| P08-08 | PASS | Build cycles, missing target/source set and separately reported source-visibility SCC pass. |
| P08-09 | PASS | Overlay URI/version/content/base validation and separate analysis fingerprint pass. |
| P08-10 | PASS | Canonical portable golden and absolute POSIX/drive/UNC/home/JDK leak scan pass. |
| P08-11 | PASS | Strict JSON/resource/major/enum checks and newer-minor typed reserialization retention pass. |
| P08-12 | PASS | Actual JAR is consumed by a separately compiled `--release 21` client and separate JVM using only that JAR; exact output is `EXTERNAL_WORKSPACE_OK`. |

### Reproduced evidence

- Java 25 strict `:workspace-model:test :gradle-plugin:test :compiler-cli:installDist :compiler-cli:test --rerun-tasks`: exit 0; model 13, exporter/client 3 and packaged CLI 7 tests, zero failures/errors/skips; 29 tasks executed. `.agent/logs/P08-REVIEW/r3-all-tests.log`.
- API golden contains 422 lines and its executable differential gate checks addition, deletion and signature change; classfile scan covers every production class.
- Independent review helper compiled with `javac --release 21` against only the workspace-model JAR, generated a canonical two-Teyru-root model, and packaged `teyru check --project workspace.json --diagnostics json` returned one SUCCESS document, exit 0. `.agent/logs/P08-REVIEW/cli-multiroot.json`.
- CLI source/dependency audit: compiler-cli has only compiler-driver as its direct project dependency; project loading invokes the bounded workspace codec and local `Files.walk`, with no Gradle API, build-script evaluation, network client or process execution.
- `verifyQuick` exit 1 only inside `verifyProductArchives` because its clean copy captured an actively changing P07 `TeyruCli` call/signature mismatch. This concurrent P07-owned failure is preserved in `.agent/logs/P08-REVIEW/r3-verifyQuick.log` and is not represented as P08 success; all P08-owned tasks and artifacts passed independently.

Final result: PASS `P08-01..12`; **ACCEPT P08**. This accepts workspace model/export/CLI consumption neutrality only, not the later P09 compilation Gradle plugin.

REVIEW: independent `/root/p00_review`; implementers did not self-approve. `.idea/vcs.xml` remained excluded and untouched.
