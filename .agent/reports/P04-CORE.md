# P04-W02 compiler-core implementation report

- Agent: `/root/p00_repair`
- Status: IMPLEMENTED, pending independent review; this report does not self-accept P04.
- Owned changes: `compiler-core/**` only, plus this report and ignored logs.

## Implemented API families

- Immutable `SourceId`, `SourceFile`, defensive UTF-8 bytes, SHA-256 fingerprint, checked `TextRange`, explicit `OffsetUnit`, `LineMap`, `UnicodeMap` and Unicode escape translation.
- URI corpus rejection covers relative traversal, percent-decoded traversal, absolute/drive/UNC, backslash, NUL/query/fragment and archive escape forms. Core does not open source URIs.
- Immutable trivia/token/CST and iterative `NodeIndex`, deterministic content-based `NodeId`, missing-token/error invariants.
- Versioned diagnostics, stable sorted data, fixes with overlap rejection; stable symbol/type/property/origin records.
- Typed value/write IR with mandatory original or explained synthetic origins.
- Deterministically ordered interval source-map point/range/reverse lookup, including distinct field/getter/setter intervals sharing one property origin.
- Cancellation token, positive byte/deadline resource budgets, stable resource error codes, immutable analysis snapshot.

## Tests

- `./gradlew --dependency-verification=strict :compiler-core:test`: exit 0; 14 tests, 0 failed/errors/skipped. Evidence `.agent/logs/P04-CORE-test-3.txt` and `compiler-core/build/test-results/test/TEST-org.javelle.compiler.core.CoreModelTest.xml`.
- `./gradlew --dependency-verification=strict verifyQuick`: exit 0; 76 tasks (11 executed, 65 up-to-date). Evidence `.agent/logs/P04-CORE-verifyQuick.txt`.
- Covered: Chinese/emoji, LF/CR/CRLF/EOF, surrogate and CRLF invalid boundaries, Unicode-created newline and many-to-one bias, invalid UTF-8, byte/deadline limits, URI traversal corpus, range unit/reversal negatives, defensive collections, missing tokens, schema-major rejection and canonical data ordering, property invariants, one-to-many source lookup, synthetic-origin and cancellation negatives.

## Final narrow completion

- `DiagnosticJson` now has a real character-by-character recursive JSON parser (no regex parser or external dependency), byte/depth limits, duplicate-key rejection, required/type/schema checks, complete related/fix/edit/data round-trip, deterministic key ordering, source/range reconstruction, and fingerprint-keyed edit bound validation. Tests exercise additive unknown fields, missing, duplicate, size, nesting, out-of-bounds and invalid range paths.
- `public-api-v1.txt` is the reviewed v1 compatibility baseline. The test parses every listed signature and resolves exactly one matching public reflection method; drift turns the test red. `ClassfilePolicy` parses JVM constant-pool tags directly and rejects Gradle/IntelliJ/LSP references; corrupt classfiles fail closed. Golden changes require an intentional diff plus reviewer approval.
- Final evidence: `.agent/logs/P04-CORE-final-3.txt`; strict compiler-core test and `verifyQuick` exit 0; 19 tests, 0 failures/errors/skipped; 78 aggregate tasks.

## Review focus / remaining risk

- Independent reviewer must attack full JLS Unicode eligibility/parity chains and raw ranges that land inside an escape; current implementation is intentionally a core translator, not a lexer/parser.
- `SourceMap` now builds immutable per-URI interval trees, exposes composition with conservative kind propagation, and has a 10,000-segment functional lookup regression. Complexity still requires reviewer profiling/adversarial overlaps.
- Diagnostic JSON reader/writer and hostile-input limits are implemented; independent mutation review remains required.
- AST variants, complete TypeRef variants/ABI projection, generation sink/file, all resource counters, no-partial snapshot publication, persisted API golden and raw constant-pool scan are implemented. Acceptance still belongs to the independent reviewer.

Continuation evidence: `.agent/logs/P04-CORE-test-5.txt`, exit 0; 17 tests, 0 failures/errors/skipped.
