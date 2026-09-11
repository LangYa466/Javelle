# P04 independent QA review

TASK / AGENT_ID / BASE_REVISION: `P04-REVIEW` / `/root/p00_review` / post-P03 full `dev` candidate

STATUS: **FAILED — REJECT P04**

## Requirement decisions

| ID | Decision | Independent result |
|---|---|---|
| P04-01 | **FAIL** | UTF-8 decoding, defensive bytes, raw UTF-16 `LineMap`, EOF/CRLF and surrogate-boundary checks are real. However the frozen contract requires explicit cross-unit conversion through `SourceFile`/`UnicodeMap`; no API converts UTF-8-byte or Unicode-code-point offsets, despite those public units existing. |
| P04-02 | **FAIL** | Basic Unicode escape/newline and raw↔translated bias mapping exist, but translated boundaries inside an escape-produced surrogate pair are accepted by `UnicodeMap`; the contract requires surrogate-interior boundaries to be rejected. Full eligibility/parity chains are not independently covered. |
| P04-03 | **FAIL** | Token/trivia, CST/AST variants, structural IDs, parent IDs, traversal and error nodes are non-marker APIs. Tests cover only one CST token and missing token; they do not verify trivia conservation, CST/AST parent-child consistency, stable IDs across reconstruction, AST/CST error nodes, or bounded incomplete recovery. Parent IDs are accepted without validation against the indexed tree. |
| P04-04 | **FAIL** | Diagnostic JSON is a real bounded recursive parser with duplicate-key/type/version checks and related/fix roundtrip. The delivered `Fix` omits the contracted fix kind, does not sort edits, and rejects a legal non-overlapping reverse-order edit list as “overlapping”; canonical fix normalization is therefore incorrect. |
| P04-05 | **FAIL** | Content-based `SymbolId`, property invariants, deterministic ABI ordering and several `TypeRef` variants exist. The frozen requirement says `TypeRef` preserves annotations and capture/anonymous inferred state; the sealed model has neither annotation storage nor a captured/anonymous representation, so it cannot fulfill that ABI contract without widening/loss. |
| P04-06 | **FAIL** | Origins and generated-path validation exist, but typed lowering consists only of generic `IrValue` and `IrWrite`; the frozen evaluation-order IR requires temporary/read/convert/write/branch forms. `GeneratedFile` accepts a `contentSha256` unrelated to `javaText`, so the deterministic generation artifact fingerprint is not enforced. |
| P04-07 | **FAIL** | Cancellation, deadline/counter limits, immutable snapshots and no-partial `SnapshotPublisher` are implemented. `ResourceTracker.addBytes` checks each increment independently instead of accumulating: with max 5, two calls of 3 both succeed. The all-budget contract therefore fails closed only per call, not for total consumed bytes. |
| P04-08 | **FAIL** | Generated point/range lookup uses an immutable interval tree and a 10,000-segment functional test; no benchmark claim is made. Reverse `originalAt` remains a linear scan, so the contract's indexed `O(log n+k)` structure is not met for all queries. Composition has no schema/fingerprint fields and therefore cannot reject the mandated intermediate fingerprint/schema mismatch. |
| P04-09 | **FAIL** | Chinese/emoji and LF/CR/CRLF/EOF plus Unicode-created newline are tested. No BOM or text-block coordinate golden exists, no UTF-8-byte/code-point roundtrip exists, and escape-produced surrogate interior handling is absent. |
| P04-10 | PASS | Stable schema-v1 diagnostic serialization, lexicographically ordered data, additive unknown fields, duplicate/missing/type/major/size/depth/edit-bound negatives are implemented and exercised. |
| P04-11 | **FAIL** | Platform reflection/constant-pool checks are real but incomplete. The public API golden contains eight selected methods; the test only proves every listed line resolves and never compares the complete exported API back to the golden. Any newly added public method/type passes silently. Constant-pool review tests one class, not every public compiler-core class. |
| P04-12 | **FAIL** | Three separate generated intervals share one property source and queries return them, but the purported field/getter/setter evidence uses identical generic segments with empty `memberOrigin`; only one generated point is queried. It does not identify and locate each field/getter/setter name/body or exercise the required synthetic-helper counterexample. |

## Executed evidence and reviewer counterexamples

- Java 25 `./gradlew --no-daemon --dependency-verification=strict :compiler-core:test`: exit 0; JUnit XML 19 tests, 0 failures/errors/skipped (task was up-to-date).
- Java 25 strict `verifyQuick`: exit 0; 77 actionable tasks, architecture/governance/reproducibility gates green.
- Isolated JShell against built compiler-core classes: exit 0. It demonstrated `BYTE_TOTAL_NOT_ENFORCED`, legal reverse-ordered non-overlapping edits rejected as `overlapping edits`, malformed Unicode escape retained without a preprocessing diagnostic, and mismatched generated content hash accepted.
- Source audit: interval tree exists and the 10k functional assertion is genuine. No latency/complexity benchmark is claimed. Reverse lookup is visibly `segments.stream()` and composition has no fingerprint/schema input to validate.
- Public API audit: golden validation is one-directional (`golden → reflection`) and contains only eight methods; it has no `actual public API → golden` equality check.
- URI traversal corpus covers `../`, encoded traversal, absolute/drive/UNC, backslash, archive marker, query and fragment. No filesystem is opened by core; symlink enforcement correctly remains caller-owned.

## Stage decision

**REJECT.** PASS only `P04-10`. Failed IDs: `P04-01/02/03/04/05/06/07/08/09/11/12`. The current classes are substantial implementations rather than markers, but the accepted P04 contract and common DoD require the missing semantics and adversarial tests above. Green aggregate builds do not override independently reproduced counterexamples.

REVIEW: independent `/root/p00_review`; implementer did not approve its own package. `.idea/vcs.xml` was excluded and untouched.

---

## Second full review after R01–R03 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P04-REVIEW-R2` / `/root/p00_review` / full repaired `dev` candidate

STATUS: **FAILED — REJECT P04**

| ID | Decision | Current independent result |
|---|---|---|
| P04-01 | **FAIL** | UTF-8/UTF-16/code-point/byte conversion, emoji/CRLF boundaries, URI traversal and immutability now work. However `SourceFile.decode` accepts a `SourceId.contentSha256` unrelated to the supplied bytes. The same content can therefore carry arbitrary content identities, violating the stable content-based `SourceId` contract. |
| P04-02 | PASS | Escape-created newline/surrogate mapping, bias, parity and translated surrogate-interior rejection are implemented and tested. |
| P04-03 | PASS | Token/trivia, deterministic IDs, validated CST/AST parent-child indexes, missing/error nodes and immutable traversal now have executable coverage. |
| P04-04 | PASS | Fix kind/ordering, overlap, source-fingerprint bounds, related/fix/data roundtrip, hostile JSON type/duplicate/depth/size checks and canonical serialization are implemented. |
| P04-05 | PASS | TypeRef now preserves annotations, capture and anonymous identity; SymbolId/property/origin/ABI structures remain deterministic and content based. |
| P04-06 | **FAIL** | Generated hash/path/collision and atomic publication are repaired. The frozen typed-lowering contract still requires explicit temporary/read/convert/write/branch forms to encode evaluation order. The sealed IR permits only generic `IrValue` and `IrWrite`, with free-form string value; no temporary, read, conversion or branch representation/test exists. |
| P04-07 | **FAIL** | Counters are cumulative/overflow-safe and cancellation/deadline/no-partial publication work. The input identity portion remains unsound because `SourceFile` publishes an independently computed fingerprint while retaining an unchecked, potentially contradictory `SourceId.contentSha256`. |
| P04-08 | **FAIL** | Forward and reverse interval indexes, overlaps, zero-width anchors, schema/fingerprint-aware composition, deterministic ordering, codec and 10k functional scale are real. Yet query-unit validation is incomplete: a `generatedOverlapping` query carrying `RAW_UTF16` is accepted against code-point segments and returns a hit. Only stored segments enforce `UNICODE_CODE_POINT`; `GeneratedRange`/query entry does not. |
| P04-09 | PASS | BOM, text block, Chinese, emoji, CRLF/LF/CR, EOF, UTF-8/code-point roundtrips, Unicode newline and escaped-surrogate goldens are present. |
| P04-10 | PASS | Version/type/unknown/duplicate/depth/size/fingerprint bounds and deterministic serialization pass with real parser/codec checks. |
| P04-11 | PASS | The gate walks every compiler-core classfile for forbidden references and hashes a canonical set of all public/protected types, constructors, fields and methods. Fixed resource records `entries=562` plus a SHA-256 digest; the test only compares against it and contains no update path. Equality is bidirectional, so an added, deleted or signature-mutated API changes count and/or digest. |
| P04-12 | PASS | Distinct property field/getter/setter intervals have accessor-specific `memberOrigin.featureId`, each is independently queried, reverse mapping returns all three, and deterministic codec roundtrip preserves them; synthetic helpers require an origin and reason. |

### Executed evidence

- Java 25 strict `:compiler-core:test --rerun-tasks`: exit 0; 34 tests across three suites, 0 failures/errors/skipped; all eight involved Gradle tasks executed.
- Java 25 strict `verifyQuick`: exit 0; 77 actionable tasks, architecture/governance/reproducibility green.
- Review-only JShell: cumulative bytes rejected; legal reverse-ordered non-overlapping fix canonicalized; wrong generated hash rejected; exit 0.
- Review-only source-map unit counterexample: a stored `UNICODE_CODE_POINT [0,2)` segment queried with `RAW_UTF16 [0,1)` returned one hit (`WRONG_UNIT_QUERY_ACCEPTED=1`) instead of rejecting the unit mismatch.
- Source identity counterexample remains directly derivable and previously reproduced: `SourceFile.decode(new SourceId(uri, zeroHash), nonemptyBytes, budget)` succeeds while `fingerprint().hex()` differs.
- API gate audit: fixed two-line golden (`entries=562`, digest `4ab4a1cf…cff66`), full classfile traversal and exact canonical digest comparison. No self-update or baseline rewrite code exists. This is structural mutation sensitivity, not a compatibility claim beyond the tested API set.
- Complexity statement is limited to structure and functional scale: both directions use interval trees and 10,000 segments are functionally exercised; no benchmark or measured `O(log n+k)` performance claim is made.

Final result: PASS `P04-02/03/04/05/09/10/11/12`; **FAIL `P04-01/06/07/08`**. Repair content-ID verification, complete the typed IR variants, and reject non-code-point source-map queries before P04 can be accepted.

REVIEW: independent `/root/p00_review`; **REJECT P04**.

---

## Final focused R04 review — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P04-REVIEW-R04` / `/root/p00_review` / final repaired `dev` candidate

STATUS: **VERIFIED — ACCEPT P04**

### Focused repairs

- `P04-01` — **PASS**. `SourceId.forContent` hashes exact input bytes and `SourceFile.decode` rejects any ID/content mismatch before decoding. Tests distinguish plain bytes from UTF-8 BOM bytes and assert the accepted ID equals the published fingerprint.
- `P04-06` — **PASS**. The sealed typed IR now includes value, temporary, symbol read, explicit conversion, write, branch and ordered sequence nodes. Constructors require type/origin/inputs, temporary/write types and boolean branch conditions are checked, and sequence indexing preserves evaluation order. Generated-file hash/path/collision and atomic batch publication remain enforced.
- `P04-07` — **PASS**. Exact input identity, cumulative overflow-safe budgets, cancellation/deadline checks and failure/cancellation-before-publish behavior pass; no partial snapshot/generated batch is published.
- `P04-08` — **PASS**. `GeneratedRange` and `SourceLocation` now reject every non-`UNICODE_CODE_POINT` range at construction, closing all point/range/reverse query entry paths. Forward/reverse interval indexes, deterministic overlap order, zero-width anchors, schema/fingerprint composition failures, codec and 10k functional coverage remain green.

### Regression and evidence

- Java 25 strict `:compiler-core:test --rerun-tasks`: exit 0; 37 tests in four suites, 0 failures/errors/skipped; eight Gradle tasks executed.
- Java 25 strict `verifyQuick`: exit 0; 77 actionable tasks, architecture/governance/reproducibility green.
- Source review confirms BOM/exact byte hashing, mismatched ID rejection, complete typed IR permits list, member origins and source-map unit guards.
- API golden is a fixed resource with `entries=623` and digest `be1510d…d432`. The gate recomputes a sorted canonical set of every public/protected type, constructor, method and field from every compiler-core classfile and compares exact count+digest. Add/delete/signature mutations necessarily change this value. It also scans every classfile for forbidden Gradle/IntelliJ/LSP constants. No writer/update task or baseline rewrite path exists.
- The increase from 562 to 623 is accounted for by the expected newly public repair surface: `SourceId.forContent`, source coordinate conversion, annotated/captured/anonymous TypeRef variants, source-map schema/fingerprint/codec/member-origin APIs, generation publisher/fix-kind, and the temporary/read/convert/branch/sequence IR types. No platform type is exposed.
- `.idea/vcs.xml` remained excluded and untouched.

Final result: **PASS `P04-01/02/03/04/05/06/07/08/09/10/11/12`**. Complexity evidence remains structural plus a 10,000-segment functional check; no benchmark claim is made.

REVIEW: independent `/root/p00_review`; **ACCEPT P04**.
