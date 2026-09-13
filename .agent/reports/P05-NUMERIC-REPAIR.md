# P05-R03A Numeric literal lexer repair

- Agent: `/root/p00_recon`
- Base revision: `591b2a6a0d87579f49712c90532759541fc6e803`
- Requirement: P05-01 / TY-J25-LEX-004
- Status: IMPLEMENTED; full-suite verification waiting on concurrent public-API golden integration

## Implementation

`StatefulTeyruLexer` now recognizes numeric literals with a bounded character state machine rather than regular expressions. It keeps each supported Java SE 25 non-preview family in one token:

- decimal, hexadecimal, binary, and octal-form integral literals;
- embedded underscores and integral/floating suffixes;
- decimal floats, dot-leading/trailing decimal floats, signed decimal exponents;
- hexadecimal significands and mandatory binary exponents.

Malformed identifier suffixes such as `12abc` remain one exact `ERROR` token with `TY-SYN-0002`. Incomplete radix/exponent forms are bounded error tokens and recovery continues. Value range and underscore-placement validity remain for parser/semantic validation; the lexer conserves their complete spelling rather than splitting it.

## Tests

1. `./gradlew --no-daemon --dependency-verification=strict :compiler-core:spotlessApply :compiler-core:test --tests dev.teyru.compiler.core.P05LexerRepairTest`
   - Exit 0; 8 tests passed.
   - Covers 18 legal representative literals, radix/exponent errors, operator/member boundaries, `12abc`, and the prior lexer regressions.
2. `./gradlew --no-daemon --dependency-verification=strict :compiler-core:clean :compiler-core:test`
   - Exit 1; 54 tests executed, 53 passed, 1 failed.
   - Sole failure: `CoreModelTest.classfileConstantPoolAndApiGoldenGate`, expected 706 public entries and observed 708.
   - This lexer repair changes no public/protected API; its helpers are private. Parent confirmed the drift belongs to concurrent P05-R03B public API work and directed this owner not to modify the golden.

## Review focus

- Reproduce `0xFF`, `0b1010`, `1_000`, decimal/hex floating forms as single `LITERAL` tokens without diagnostics.
- Confirm `12abc`, `0xG`, `0b2`, and missing exponent digits are bounded errors and later tokens remain available.
- Re-run the full suite after the R03B integration owner updates/reviews the shared API golden.
