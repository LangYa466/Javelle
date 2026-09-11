# P05-R01 Lexer repair

- Agent: `/root/p00_recon`
- Base revision: `591b2a6a0d87579f49712c90532759541fc6e803`
- Requirements: P05-01, P05-05
- Status: IMPLEMENTED; independent review pending

## Implemented

- Replaced repeated-character operator recognition with ordered longest matching for Java multi-character operators, shifts/assignments, method reference, lambda arrow, and ellipsis.
- Emits one bounded `JV-SYN-0002` error token for malformed numeric identifier suffixes such as `12abc`, then resumes lexing.
- Assigns same-line comment trivia (including surrounding whitespace) to the preceding token's trailing trivia while preserving every raw byte exactly once.
- Emits unsupported text blocks as one `ERROR` token with `JV-DEV-0001`; semicolons inside them cannot leak into syntax tokens.
- Preserves semicolons in strings, chars, and comments. Unicode-translated operators and semicolons retain original raw UTF-16 spans/text.
- Checks cancellation on every lexer loop iteration.

## Verification

1. `./gradlew --no-daemon --dependency-verification=strict :compiler-core:spotlessApply :compiler-core:test --tests org.javelle.compiler.core.P05LexerRepairTest`
   - Exit 0; 5 tests passed, 0 failed/skipped.
2. `./gradlew --no-daemon --dependency-verification=strict :compiler-core:test`
   - Exit 0; XML aggregate 51 tests, 0 failures/errors/skipped.
3. `./gradlew --no-daemon --dependency-verification=strict verifyQuick`
   - Exit 0; `VERIFY_QUICK_PASS`; 77 tasks, architecture/governance/format/test gates passed.

Test coverage includes all supported multi-character operators, malformed-number recovery, leading/trailing comment ownership and exact reconstruction, semicolon data versus syntax, unsupported text blocks, and Unicode escape raw-span preservation.

## Review handoff

- Reproduce the two former failures: ellipsis must be one operator token; `class /*x*/ C` must retain ` /*x*/ ` (including final space) as trailing trivia.
- Confirm malformed numeric recovery and text-block unsupported behavior through the public lexer interface.
- Public API signatures did not change; no API golden update requested.
