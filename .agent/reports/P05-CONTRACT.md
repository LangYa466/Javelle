# P05 frontend contract and executable fixture selection

TASK / AGENT_ID / BASE_REVISION: `P05-W01` / `/root/p00_spec` / shared accepted-P04 candidate

STATUS: **CONTRACT_FROZEN — implementation remains NOT_IMPLEMENTED / NOT_VERIFIED**

## 1. Scope and entry points

Package ownership is `org.javelle.compiler.core.frontend` plus required concrete nodes under the existing `syntax` package. No Gradle, IDE, LSP, javac-internal or filesystem API enters these signatures.

```java
record FrontendOptions(int languageMajor, int maxDiagnostics) {}
record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {}
record ParseResult(CompilationUnit ast, CstNode cst, NodeIndex nodes,
                   List<Diagnostic> diagnostics, boolean recovered) {}

interface JavelleLexer {
  LexResult lex(SourceFile source, FrontendOptions options,
                ResourceTracker resources, CancellationToken cancellation);
}
interface JavelleParser {
  ParseResult parse(SourceFile source, LexResult lexical,
                    FrontendOptions options, ResourceTracker resources,
                    CancellationToken cancellation);
}
final class JavelleFrontend {
  ParseResult parse(SourceFile source, FrontendOptions options,
                    ResourceBudget budget, CancellationToken cancellation);
}
```

The façade runs Unicode translation already owned by `SourceFile`, lexes translated text while retaining raw and translated ranges, then parses the produced tokens. It does not rescan source with regex. Every result belongs to the exact input `SourceId` fingerprint.

## 2. P05 grammar capability boundary

### Accepted in this stage

1. Compilation unit: optional `package`, repeated explicit or wildcard `import`, then one or more non-generic `class` declarations. Modifiers accepted: Java visibility, `static`, `final`, `abstract` where Java permits.
2. Members: explicitly typed field with optional initializer; constructor-shaped declarations may be diagnosed unsupported in P05. Methods have explicit return type or `void`, typed parameters, and block body. A plain field remains `FieldDeclaration`, never a property.
3. Blocks/statements: nested block, local explicit type/`var`/`val`, expression statement, `return`, and `if`/`else`. A bare `return` is legal; a return expression starts on the same logical line under P03 rules.
4. Expressions: literals (`null`, boolean, integral, floating, char, string), names, parentheses, member access, method invocation, `new` class construction, cast, unary, multiplicative/additive, comparison/equality, `&&`, `||`, ternary, and right-associative simple assignment. Precedence follows Java SE 25. No semantic type resolution is claimed beyond P05 checks explicitly listed below.
5. Property: explicit type/name followed by accessor block; one `get` and/or `set(value)`, optional visibility, either default newline form or custom block. `field` and `value` are contextual only inside the relevant accessor body.

### Explicitly not advertised in P05

Enums/records/interfaces/annotations, generics/type-use annotations, arrays/varargs, lambdas/method references, switch/loops/try/synchronized/assert/labeled statements, anonymous/local classes, text blocks/templates, pattern matching, modules and all preview features emit `JV-DEV-0001` with data `{feature, languageVersion:"0.1", stage:"P05"}` over the introducing token. The parser consumes a balanced construct into `UnsupportedSyntaxNode`; it never ignores it or calls it successful. `JV-DEV-0001` is a development capability diagnostic and must be added by the diagnostics catalog owner before implementation merges; until then this is a named dependency, not a fabricated completed code.

## 3. Lexer contract (P05-01/05)

- A deterministic state-machine lexer emits identifier/keyword, numeric/string/char literals, operators/punctuation, `NEWLINE`, `SEMICOLON`, `EOF`, and `ERROR`; whitespace/comments are lossless `Trivia` attached by one documented rule: trivia before the first token on a line is leading; trivia after a token through but excluding the next logical line is trailing.
- Unicode translation precedes tokenization. Thus raw `\\u003b` becomes a `SEMICOLON` token whose translated range is one unit and raw range covers the full escape. Semicolons inside strings/chars/comments/text data never become tokens.
- Identifiers use Java SE 25 Unicode identifier rules and code points; malformed literals/comments produce bounded error tokens and a diagnostic, not truncation or exception.
- Concatenating raw token and trivia slices reconstructs every raw input byte after UTF-8 decoding, including CR/LF/CRLF, BOM, comments and EOF trivia. Token ranges do not cross surrogate interiors.

## 4. Parser, AST and recovery contract (P05-02…12)

- Recursive descent/Pratt or generated formal parser is permitted; regex recognition is not. Expression precedence tests must distinguish `a + b * c`, chained member/call, ternary nesting and right-associative assignment.
- Every AST node has a deterministic `NodeId`, exact half-open raw range, parent ID and corresponding CST coverage. AST snapshots are supplemental: tests also assert node kinds, relationships and ranges from independently specified anchors.
- `var`/`val` locals require an initializer; `var x = null` emits `JV-TYP-0002`; field `var`/`val` emits `JV-TYP-0003`. Full inference/reassignment belongs later, so P05 must not advertise it.
- A syntax `SEMICOLON` emits `JV-SYN-0001` on exactly that token with a `QUICK_FIX` deleting it. Parsing continues at newline/member/block synchronization.
- Recovery nodes are mandatory for missing expression, unterminated block/string/comment, incomplete accessor and unsupported balanced syntax. Each recovery iteration consumes a token or returns; nesting/token/node/diagnostic budgets are charged. At most one primary diagnostic is emitted at the same `(code,range)` and the fixture ceiling is enforced.
- Names `get`, `set`, `field`, `value` lex as contextual identifiers. Outside an accessor header/body, `obj.get`, `set()`, local `field`, and parameter `value` parse normally. Inside a computed getter without storage, `field` is retained as an identifier node plus later `JV-PROP-0006`; it is never silently rebound.

## 5. Fixture manifest

`compiler-core/src/test/resources/p05/cases.json` selects nine of the 29 P03 normative cases and adds two P05 capability negatives. Each source is a complete compilation unit, not a snippet accepted via hidden wrapper. `expectedAst` entries are required ordered preorder anchors; `Type:name` identifies a declaration/contextual node. Diagnostic `anchor` must occur exactly once and the harness converts it independently to a raw UTF-16 range of `length`. Snapshot goldens generated by the parser are accepted only when these anchors, parent/child invariants, token/trivia conservation and diagnostic arrays also match.

The selected set covers literal versus syntax semicolon, plain field versus property, default/custom computed accessor, typed and inferred local, cast-null versus untyped null, illegal inferred field, incomplete accessor recovery, and two unsupported constructs. P06 may expand it; P05 does not claim all 29 normative fixtures.

## 6. Requirement-to-acceptance matrix

| ID | Required executable proof |
|---|---|
| P05-01 | Token golden for Unicode identifiers/literals/comments/newlines; raw token+trivia conservation; malformed literal terminates. |
| P05-02 | Parse package/import/class/field/method/block/local sources and verify ordered concrete AST kinds and raw ranges. |
| P05-03 | `PROP-DEFAULT` and `PROP-COMPUTED-NO-INIT`; accessor visibility/body/value nodes and distinct property node. |
| P05-04 | Table-driven precedence/associativity plus member-call/new/assignment/return/if trees. |
| P05-05 | Both semicolon fixtures, including Unicode-semicolon counterexample; exact deletion fix round-trip. |
| P05-06 | Truncation at every token boundary of property sample: no crash/hang, progress, ≤ configured diagnostics, error node. |
| P05-07 | AST golden plus explicit node-kind/range/parent/CST assertions; mutation of precedence must fail. |
| P05-08 | `TYP-VAR-NULL`, `TYP-FIELD-INFERENCE`, `TYP-VAR-CAST-NULL`; exact codes, no false success. |
| P05-09 | Unsupported enum/basic-for fixtures return `JV-DEV-0001`, `UnsupportedSyntaxNode`, and `success=false`. |
| P05-10 | Plain-field/property pair assert different sealed node classes and property accessor list only on property. |
| P05-11 | Fixture with ordinary `get/set/field/value` names outside accessors plus invalid computed `field` binding. |
| P05-12 | Independent reviewer runs token-boundary truncation corpus under timeout and verifies diagnostic cascade ceiling. |

## 7. Commands and merge gates

```bash
python3 -m json.tool compiler-core/src/test/resources/p05/cases.json >/dev/null
python3 -m json.tool compiler-core/src/test/resources/p05/manifest.schema.json >/dev/null
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-core:test --rerun-tasks
```

Implementation acceptance additionally requires a focused `P05FrontendTest`, lexer conservation corpus, token-boundary truncation timeout test, and independent reviewer mutation/negative evidence. No P05 checkbox is eligible for ACCEPT from this contract alone.

## 8. Risks/dependencies

- Current P04 `DiagnosticCode` accepts only `JVL-*` while the normative P03 catalog uses `JV-*`; frontend implementation is blocked from emitting contract codes until the diagnostics owner resolves that drift without renaming P03 codes.
- Existing generic CST/AST variants are insufficient to prove all concrete declarations/expressions; compiler owner must add real sealed nodes, not encode kinds in strings.
- Semantic property storage, full Java attribution and emission remain P06+; P05 only preserves syntax/context needed by those stages.
