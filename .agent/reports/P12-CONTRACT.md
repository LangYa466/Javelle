# P12 complete lexer and error recovery

TASK / AGENT_ID / BASE_REVISION: `P12-W01` / `/root` / `main` at `80cb32e8d7c155ad8f4c2d8e9b59f953948a31ac`

STATUS: **CONTRACT_FROZEN — P12 implementation IN_PROGRESS (round 1 of N)**

## Round 1 progress (self-reported, not yet independently reviewed)

Implemented and tested this round: full Java SE 25 reserved-keyword set (P12-01 partial — see gap below), real text-block lexing per JLS 3.10.6 replacing the old "unsupported at P05" ERROR rejection (P12-01), full string/char escape validation including octal escapes and the text-block-only line-continuation escape (P12-01), Javadoc-vs-plain-block-comment distinction (P12-03), UTF-8 BOM consumed as leading trivia (P12-04), explicit CRLF/LF/lone-CR and astral-identifier (surrogate pair) coverage (P12-04), contextual-word (`var`/`yield`/`record`/`sealed`/`permits`/`val`) non-capture confirmed by test (P12-05), unterminated string/text-block/comment bounded to one diagnostic each with no hang (P12-07), semicolon-in-string-content protection re-verified (P12-08), and a first seeded deterministic mutation-fuzz test over 500 trials asserting no exception/hang (P12-10, partial).

A real regression was found and fixed in the same commit: making primitive-type keywords (`int`, `boolean`, etc.) correctly lex as `KEYWORD` instead of `IDENTIFIER` broke the already-ACCEPTED P05 parser's field/local-variable-declaration type matching, which only recognized `IDENTIFIER`-kind tokens for types. Fixed narrowly in `RecursiveJavelleParser.isIdentifierLike` by extending its existing keyword-as-identifier allowlist to the primitive-type keywords, matching the pattern already used there for `void`/`var`/`val`/etc. Full primitive-type grammar support remains P13's job; this is only the minimal compatibility fix needed to not regress P05.

Known gaps for the next round: P12-06 (documented decision only, no `>>` splitting API yet — deferred to P13/P14 as scoped), P12-09 (architecturally already true, wants an explicit test), P12-11 (no IDE-facing syntax facade exists yet in this repo to cross-check against — likely N/A until one exists), P12-12 (no fuzz-found failure has occurred yet to reduce into a fixture — the corpus is a synthetic mutation set, not yet a discovered-bug regression suite). The keyword expansion also needs a second pass to confirm every consuming site in the parser (not just field/local declarations) still accepts primitive-type and other newly-KEYWORD tokens wherever Java allows them as identifiers is out of scope contextually (e.g. cast expressions, generic bounds) — P13/P14 grammar work will exercise those paths for real.

Source: `prompts/JAVELLE_IMPLEMENTATION_PLAN.md` section E, `## P12 — 完整詞法與錯誤恢復` (lines 777-796). This contract restates that section's 12 requirements (P12-01..P12-12) as a concrete acceptance matrix against the current codebase; it does not change the plan's scope.

## 1. Starting point

`compiler-core/src/main/java/org/javelle/compiler/core/frontend/StatefulJavelleLexer.java` is the P05 minimal lexer (322 lines): identifiers/keywords (18-word list), a numeric-literal scanner that already handles decimal/hex/binary/octal-by-underscore, underscores, hex-float, exponents and suffixes, single/double-quoted literals with naive backslash-skip (no escape validation), `//` and `/* */` comments with no Javadoc distinction, whitespace/newline trivia, and a greedy multi-character operator table. Text blocks are explicitly rejected as `JV-DEV-0001 "text blocks unsupported at P05"`. `>>`/`>>>` are always lexed as single multi-char operators (no generic-close splitting). There is no fuzz harness.

## 2. Acceptance matrix (P12-01..P12-12)

| ID | Required evidence |
|---|---|
| P12-01 | Full Java SE 25 lexical inventory: all integer/float radices already present must stay correct; add text blocks (`"""..."""` with incidental/mandatory closing-delimiter whitespace stripping per JLS, escapes, line terminators), full character/string escape validation (`\n \t \b \f \r \" \' \\ \s`, octal escapes for char/string, invalid escape diagnostics), the full Java 25 non-preview keyword and contextual-keyword set (reserved words, `var`, `yield`, `record`, `sealed`, `permits`, `non-sealed`, module keywords, plus Javelle's own `val`), and all separator/operator tokens including `::`, `->`, `...`, `@`. |
| P12-02 | Unicode-escape (`\uXXXX`) translation happens before tokenization (verify `SourceFile`'s raw→translated pipeline actually performs JLS 3.3 escape processing, including escapes that themselves produce `\`, further escapes, and escapes that produce newlines/semicolons); the raw↔translated offset map stays correct through it. |
| P12-03 | Every line comment, block comment, and Javadoc comment (`/** ... */`, distinguished from plain block comments) is preserved as trivia attached to a real token, never dropped, so formatter/migration round-trips are lossless. |
| P12-04 | Explicit, tested policy for CRLF vs LF vs lone-CR line endings, UTF-8 BOM at file start, tab expansion (none — tabs are trivia, not width-normalized), astral (non-BMp) identifiers via surrogate pairs, and invalid UTF-8/unpaired surrogates (diagnosed, not silently replaced or crashed on). |
| P12-05 | `get`, `set`, `field`, `val`, `var` lex as plain identifiers/keywords the same everywhere; grammar-context disambiguation (property-accessor vs local-variable vs plain identifier) is a parser concern, not a lexer keyword-list hack that would break `int val = 1` style ordinary uses of those words as identifiers where Java allows it. |
| P12-06 | `>>`/`>>>`/`>>=`/`>>>=` remain lexed as single tokens (Java's own lexical grammar does this); splitting for nested generic closes (`List<List<Integer>>`) is a parser-level token-splitting operation, not a lexer change — this item's evidence is a documented decision + a parser-facing API the lexer exposes for it (deferred to P13/P14 grammar work), not a lexer behavior change. |
| P12-07 | Unterminated string/char/text-block/comment at EOF produces one bounded diagnostic and a synthetic close, never an infinite loop or unbounded scan; multiple consecutive lexical errors in one file each get their own diagnostic up to `options.maxDiagnostics()` and lexing terminates deterministically. |
| P12-08 | `;` inside string/char/text-block/comment content is content, never a syntax token; a real dedicated fixture set (JSON/SQL/URL-shaped strings containing `;`) proves this. |
| P12-09 | Newline tokens carry enough metadata (already: raw/translated range, leading/trailing trivia) for the parser to decide statement continuation; the lexer itself never inserts a synthetic `;` or decides continuation. |
| P12-10 | A seeded, deterministic mutation-fuzz harness (module `testkit` or `compiler-core` test sources) runs the lexer over a corpus of valid + deliberately-corrupted Javelle/Java sources under a byte/time budget and asserts: no exception escapes, no non-terminating loop (a hard iteration/time cap that fails the test if hit), and total tokens/diagnostics stay within `FrontendOptions` budgets. |
| P12-11 | The token/keyword table used by any IDE-facing syntax highlighting facade (if one exists yet in this repo) is the same `TokenKind`/keyword set as the compiler lexer — one source of truth, checked by a test, not just by convention. |
| P12-12 | Fuzz-found failures get a fixed seed, a minimal reduced repro fixture checked into `compiler-core/src/test/resources` (or equivalent), and a regression test — not just a log line. |

## 3. Explicitly out of scope for P12

- Actual `>>` splitting logic for nested generics (that's the parser's job, P13/P14) — P12 only ensures the lexer's token shape doesn't block it.
- Full grammar-level property/accessor keyword disambiguation (parser, P13/P18).
- IDE semantic highlighting beyond reusing the same token/keyword table (P30+).

## 4. Test requirements

- Every requirement above gets fixture-backed JUnit tests (not just informal manual checks), following this repo's existing pattern (see `P05LexerRepairTest.java`).
- A dedicated fuzz test class runs under normal `test` (bounded, deterministic, seeded — not a long-running fuzz campaign in CI).
- `verifyQuick` must stay green; no regression in P05's existing passing tests unless a test is deliberately superseded by a stricter one covering the same requirement.

## 5. Acceptance

Independent review (fresh context, does not trust the implementer's summary) checks each P12-0x row against real code + real test runs, same process as P09/P10/P11.
