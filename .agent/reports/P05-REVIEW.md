# P05 independent frontend review

TASK / AGENT_ID / BASE_REVISION: `P05-REVIEW` / `/root/p00_review` / accepted-P04 `dev` candidate

STATUS: **FAILED — REJECT P05**

## Requirement decisions

| ID | Decision | Independent result |
|---|---|---|
| P05-01 | **FAIL** | Lexer is a real state machine, not regex, and conserves the tested raw stream. But `!=`, `<=`, `>=` are split because only repeated-character operators are combined; malformed numeric `12abc` is emitted without a diagnostic; and all trivia is attached as leading (the documented same-line trailing rule is never implemented). |
| P05-02 | **FAIL** | Class/field/method/local shells exist, but package/import are accepted by `scanLine` and method parameters by delimiter balancing rather than grammar. Invalid contents can become declaration nodes without structural validation. |
| P05-03 | **FAIL** | Field/property and default/custom get/set nodes are distinct with ranges, but parsed accessor visibility modifiers are discarded and absent from AST, so the required access-modifier contract cannot be consumed downstream. |
| P05-04 | **FAIL** | Pratt structure handles tested arithmetic, simple assignment, ternary, member/call/new/cast. Comparison/equality is broken at lexing for `!=`, `<=`, `>=`. `if` consumes its condition/body but returns an `IfStatement` with zero children and discards else/body AST, so basic if/block grammar is not represented. |
| P05-05 | PASS | Literal/comment semicolons are protected in covered forms; literal and Unicode-produced syntax semicolons become exact `TY-SYN-0001` spans with deletion fixes and parsing continues. |
| P05-06 | **FAIL** | Unterminated literal/comment/block/accessor recovery is bounded in several paths, but a missing RHS can create an `ErrorNode` without the required primary diagnostic. Malformed numerics are silently accepted, so malformed input recovery is incomplete. |
| P05-07 | **FAIL** | Concrete structural nodes/ranges/parents are tested, but the 11-case JSON manifest is never loaded or checked by the test harness and no AST snapshot/golden exists. Hard-coded `contains` assertions cannot prove every ordered expected anchor/span or detect all structural drift. |
| P05-08 | PASS | `var x = null` and inferred field `private var` produce exact non-success codes; cast-null remains accepted. |
| P05-09 | **FAIL** | Only enum and basic-for are recognized as balanced unsupported constructs. `record R(int x) {}` yields eight cascading `TY-SYN-0002` diagnostics, not one `TY-DEV-0001`; a text-block source returns no diagnostic and `recovered=false`. Other explicitly listed unsupported families are likewise not dispatched. |
| P05-10 | PASS | Plain field and property use different node kinds, and accessors appear only on property nodes. |
| P05-11 | **FAIL** | Ordinary get/set/field/value identifiers parse outside accessors, but the required computed-getter `field` invalid-binding diagnostic is absent; it remains an unqualified name with no `TY-PROP-0006`. |
| P05-12 | **FAIL** | Token-boundary truncation loop terminates for one ASCII property sample, but has no timeout, no malformed nesting corpus, and asserts only diagnostic count—not required recovery/error nodes or deduplication at each invalid cut. |

## Executed evidence

- Java 25 strict `:compiler-core:test --rerun-tasks`: exit 0; 43 tests, 0 failures/errors/skipped; all eight tasks executed.
- Java 25 strict `verifyQuick`: exit 0; 77 actionable tasks, architecture/governance/reproducibility green.
- Structural audit found no regex/`Pattern`/`Matcher` usage in the frontend. Lexer and recursive Pratt parser are substantive implementations, not markers.
- Review-only façade/lexer harness, exit 0:
  - `a != b && c <= d` operator tokens were `[!, =, <, =]` rather than `!=`, `&&`, `<=` as complete operator units (the filter omitted the correctly joined `&&`).
  - `class /*x*/ C {}` produced `CLASS_TRAILING=0`, while the following token carried three leading trivia entries.
  - `12abc` produced zero lexical diagnostics.
  - record source produced eight `TY-SYN-0002`; text-block source produced no diagnostic; neither met the unsupported capability contract.
  - parsed `IfStatement` had zero children.
- `cases.json` parses and contains exactly 11 complete units, but no production test references/loads that resource; expected ordered AST anchors and exact diagnostic anchors are not mechanically enforced.
- Public API gate remains bidirectional and now records the intended frontend-expanded 694-entry API; full compiler-core classfile forbidden-reference scan remains active. This does not override frontend semantic failures.

## Stage decision

**REJECT.** PASS `P05-05/08/10`; failed IDs `P05-01/02/03/04/06/07/09/11/12`. Repair operator/literal lexing and trivia attachment, preserve accessor/if/parameter structure, execute the manifest, implement all advertised unsupported dispatch, and strengthen recovery/truncation evidence before acceptance.

REVIEW: independent `/root/p00_review`; implementer did not approve its own package. `.idea/vcs.xml` remained excluded and untouched.

---

## Second full review after R01/R02 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P05-REVIEW-R2` / `/root/p00_review` / repaired `dev` candidate

STATUS: **FAILED — REJECT P05**

| ID | Decision | Current independent result |
|---|---|---|
| P05-01 | **FAIL** | Longest-match operators including ellipsis, exact trailing trivia conservation and `12abc` recovery are repaired. However legal Java integral literals `0xFF`, `0b1010`, and `1_000` are emitted as `ERROR` with `TY-SYN-0002`; the accepted integral-literal lexer surface is still incomplete. |
| P05-02 | PASS | Package/import/class/field/method/typed parameter/block/local vertical-slice nodes parse with concrete ranges and parent links; unsupported extensions remain separately governed by P05-09. |
| P05-03 | PASS | Default/custom getter/setter bodies and explicit accessor visibility are now represented by `AccessorNode`; property spans and field distinction remain concrete. |
| P05-04 | PASS | Multi-character comparison/equality operators, Pratt precedence, right-associative assignment, ternary, cast/new/member/call and if condition/then/else block children are structural and independently reproduced. |
| P05-05 | PASS | Syntax and Unicode semicolons have exact deletion fixes; string/char/comment/text-block data semicolons are never emitted as syntax semicolon tokens. |
| P05-06 | PASS | Missing RHS/accessor/block/literal/comment paths produce bounded diagnostics/error nodes and continue; resource checkpoints and diagnostic ceilings are active. |
| P05-07 | **FAIL** | The test now loads all 11 JSON cases and checks ordered anchors, parent/range invariants and diagnostic anchors. It still has no AST snapshot/golden at all, despite P05-07 explicitly requiring one; ordered subsequence checks permit arbitrary extra/reordered structure outside anchors and are not an exact snapshot substitute. |
| P05-08 | PASS | Inferred-field and bare-null inference negatives remain exact while cast-null succeeds. |
| P05-09 | **FAIL** | Record and text block now produce explicit development diagnostics without the old cascade, but the frozen unsupported list is not implemented. `while`, `switch`, and `try` produce generic `TY-SYN-0002`, while `x -> x` is silently accepted with no diagnostic and `recovered=false`; all must be balanced `UnsupportedSyntaxNode` + `TY-DEV-0001`. |
| P05-10 | PASS | Plain field and accessor-block property remain distinct node families. |
| P05-11 | PASS | Ordinary contextual names remain identifiers outside accessors; computed getter `field` now emits `TY-PROP-0006`, and accessor visibility is retained. |
| P05-12 | PASS | Every nonempty UTF-16 truncation of the property sample runs under a 3-second preemptive timeout, reports recovery/nonempty bounded diagnostics and checks duplicate `(code,range)` keys. |

### Executed evidence

- Java 25 strict `:compiler-core:test --rerun-tasks`: exit 0; 51 tests in six suites, 0 failures/errors/skipped; all eight tasks executed.
- Java 25 strict `verifyQuick`: exit 0; architecture/governance/reproducibility green.
- Review-only lexer harness: `0xFF`, `0b1010`, `1_000` each became `ERROR/TY-SYN-0002`; decimal/octal/float samples passed.
- Review-only unsupported harness: while/switch/try returned generic syntax errors; lambda returned no diagnostic and false recovery. This directly contradicts the P05 capability boundary.
- Review-only assignment tree confirmed `x = (y = z)`; operator/trivia/text-block/if/accessor repairs also have focused executable tests.
- Parser remains structural recursive descent/Pratt. No regex drives parsing; two small regexes only validate numeric suffixes in the lexer.
- Public API golden is a fixed bidirectional full API digest (`entries=706`, SHA `025e73da…37e0`); all-classfile forbidden-reference scan remains active and no auto-update path exists.

Final result: PASS `P05-02/03/04/05/06/08/10/11/12`; **FAIL `P05-01/07/09`**. Complete legal numeric tokenization, add an exact AST snapshot gate, and route every frozen unsupported family through balanced `TY-DEV-0001` nodes before acceptance.

REVIEW: independent `/root/p00_review`; **REJECT P05**.

---

## Final focused varargs review — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P05-REVIEW-FINAL` / `/root/p00_review` / current `dev` candidate

STATUS: **VERIFIED — ACCEPT P05**

| ID | Decision | Independent result |
|---|---|---|
| P05-01 | PASS | Lossless lexer, numeric families/boundaries, longest-match operators, trivia and Unicode behavior remain covered by the strict suite. |
| P05-02 | PASS | Accepted Java-first declaration/block/local slice remains structural. |
| P05-03 | PASS | Property/accessor nodes, visibility, bodies, spans and parents remain concrete. |
| P05-04 | PASS | Pratt precedence/associativity and structural expression/control-flow trees remain green. |
| P05-05 | PASS | Syntax semicolons are rejected while protected literal/comment/text-block data remain preserved. |
| P05-06 | PASS | Recovery, error nodes, budgets and parser progress remain bounded. |
| P05-07 | PASS | Eleven manifest cases and fixed byte-exact AST/diagnostic snapshots remain exercised. |
| P05-08 | PASS | `val`/`var` frontend null and placement boundaries remain exact. |
| P05-09 | **PASS** | Independent 15-family corpus now gives every family exactly `dev=1`, `syn=0`, one `UnsupportedSyntaxNode`, and `recovered=true`. In particular `void m(String... x)` gives `dev=1/syn=0/node=1` and recovers. |
| P05-10 | PASS | Plain fields and accessor-block properties remain distinct node families. |
| P05-11 | PASS | Contextual identifiers and property-only `field` rules remain enforced. |
| P05-12 | PASS | Truncation/progress/timeout and public API gates remain green. |

### Reproduced evidence

- Java 25 strict `./gradlew --no-daemon --dependency-verification=strict :compiler-core:test --rerun-tasks`: exit 0; 56 tests, 0 failures, 0 errors, 0 skipped.
- Java 25 strict `./gradlew --no-daemon --dependency-verification=strict verifyQuick`: exit 0; `BUILD SUCCESSFUL`; architecture, governance, reproducible archives, formatting and tests passed.
- Review-only JShell corpus covered while/do/for/switch/try/synchronized/assert/enum/record/interface/module/lambda/method reference/varargs/text block: all 15 exact and `CORPUS_PASS=true`. Evidence: `.agent/logs/P05-REVIEW/final-varargs-corpus.log`.
- Fixed public API resource remains `entries=708`, semantic digest `817c0325731bb94744df3d83e7c68e8d7d9af40b1e3dbbde4557d966ba6c4671`; the strict suite re-exercised its bidirectional gate.
- `.idea/vcs.xml` remained excluded and untouched.

Final result: PASS `P05-01..12`; **ACCEPT P05**. This accepts the P05 frontend contract and its explicit development-unsupported boundary only; it does not claim later compiler stages are implemented.

REVIEW: independent `/root/p00_review`; implementer did not approve its own package.

---

## Final third review R03A/B — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P05-REVIEW-R3` / `/root/p00_review` / final repaired `dev` candidate

STATUS: **FAILED — REJECT P05**

| ID | Decision | Current independent result |
|---|---|---|
| P05-01 | PASS | Stateful lexer now covers decimal/octal/hex/binary integers, underscores, suffixes, decimal/hex floating forms, longest-match operators including ellipsis, invalid numeric boundaries, exact trivia conservation and Unicode raw spans. `12abc` and malformed exponent/radix cases are bounded errors. |
| P05-02 | PASS | Accepted package/import/class/field/method/parameter/block/local vertical slice remains structural. |
| P05-03 | PASS | Property/accessor visibility, custom/default bodies, spans and accessor parent IDs remain concrete. |
| P05-04 | PASS | Pratt precedence/associativity, ternary/cast/new/member/call/assignment and if trees remain green. |
| P05-05 | PASS | Unicode/literal syntax semicolons and protected string/char/comment/text-block data remain exact. |
| P05-06 | PASS | Error nodes, exact diagnostics, balanced recovery, resource charging and progress remain bounded. |
| P05-07 | PASS | All 11 cases now contribute byte-exact full AST/diagnostic snapshots from fixed `ast-snapshots.txt`, in addition to manifest anchors/ranges/parents. An isolated snapshot mutation made the targeted test fail. |
| P05-08 | PASS | Bare-null and inferred-field negatives plus cast-null positive remain exact. |
| P05-09 | **FAIL** | Fourteen independently exercised unsupported families return exactly one `TY-DEV-0001`, one balanced `UnsupportedSyntaxNode`, and no generic cascade. Varargs remains wrong: `class C { void m(String... x){} }` returns `dev=0`, `TY-SYN-0002=1`, and zero unsupported nodes. The frozen P05 boundary explicitly lists varargs as development-unsupported, so it cannot be accepted as a generic syntax error. |
| P05-10 | PASS | Field/property distinction remains structural. |
| P05-11 | PASS | Contextual names and computed-field diagnostic remain correct. |
| P05-12 | PASS | Timeout/progress/error/dedup truncation corpus remains green. |

### Executed evidence

- Java 25 strict `:compiler-core:test --rerun-tasks`: exit 0; 55 tests, 0 failures/errors/skipped.
- Java 25 strict `verifyQuick`: exit 0; architecture/governance/reproducibility green.
- Isolated candidate snapshot mutation (`CompilationUnit` changed only in temporary golden): targeted manifest/snapshot test exit 1 and `P05FrontendTest.manifestLoadsAllElevenCasesAndChecksOrderedExpectations` failed. The repository golden was untouched.
- Independent unsupported façade corpus: while/do/for/switch/try/synchronized/assert/enum/record/interface/module/lambda/method-reference/text-block each `dev=1, syn=0, nodes=1`; varargs alone `dev=0, syn=1, nodes=0`.
- Numeric focused tests include valid families and invalid radix/exponent/identifier boundaries; structural review confirms scanning is stateful. No regex drives the parser.
- Public API golden is fixed and bidirectional at 708 entries (`817c0325…c4671`); all-classfile forbidden-reference scan remains active.

Final result: PASS `P05-01/02/03/04/05/06/07/08/10/11/12`; **FAIL `P05-09`**. Route varargs through the same balanced development-unsupported contract and rerun the focused matrix before P05 acceptance.

REVIEW: independent `/root/p00_review`; **REJECT P05**.
