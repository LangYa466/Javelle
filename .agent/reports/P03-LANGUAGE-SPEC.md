# P03-W02 — normative language/grammar specification

- Agent `/root/p00_spec`; base: current P02-accepted `dev` candidate
- Requirements: P03-01/02/03/04/05/06/09/10 language subset; B1–B4; UAT-03–15
- Status: specification IMPLEMENTED; parser/compiler behavior NOT_IMPLEMENTED/NOT_VERIFIED

## Changed contracts

- `spec/language-spec.md`: normative Java-first semantics, Unicode translation and original span mapping, trivia/CST/AST contract, progress-guaranteed recovery, zero syntax semicolons, newline continuation and return/throw/yield/++/-- rules.
- `spec/grammar/teyru.ebnf`: formal Java-25-delta EBNF for statement termination, basic/enhanced for disambiguation, newline resources, enum colon, local inference and property accessors. `JAVA_*` symbols are explicit imports from the forthcoming Java SE 25 grammar inventory, not regex/text placeholders.
- `spec/grammar/grammar-contract.json`: machine declaration of lexical phases, contextual names, separators and unimplemented status.
- `spec/diagnostics/catalog.json`: 20 stable syntax/type/property/compiler diagnostic contracts with original half-open ranges and fixes.
- `spec/diagnostics/fixtures.json`: 24 positive/negative/behavior/recovery cases including Unicode semicolon, ternary-in-basic-for, val/null, storage/accessor rules and property event order.
- `spec/diagnostics/validate.py`: dependency-free uniqueness/reference/formal-production/status validator.

## Frozen semantic decisions

1. Unicode escapes follow Java eligibility/parity before lexing and retain raw-to-translated boundary maps. Strings/chars/text blocks/comments own internal semicolons; only a resulting syntax token reports `TY-SYN-0001`.
2. Newline terminates only a complete non-continuing construct. Return newline is value-less; throw/value-yield require same-line expression start. Postfix/prefix increments cannot silently rebind across terminating newline.
3. Basic-for has two nesting-aware top-level colons and preserves nested ternary colons; enhanced-for has one. Resources end only on newline after a complete expression. Enum members require one explicit top-level colon.
4. `var`/`val` are local inference; val is a final binding, not deep immutability. Null/untargeted functional inference fails; typed null remains legal. Field/parameter/return inference is rejected.
5. Only accessor-block declarations are properties. Storage, initializer, final/computed behavior, contextual `field`, visibility, JavaBeans ABI, annotation targets, inheritance/interface/record behavior and cross-JAR metadata are normative.
6. Property lowering preserves receiver/getter/RHS/setter count/order, assignment expression value, conversions, null/throw timing, branches and control flow. Simple assignment never reads getter; compound/postfix do exactly once.
7. Mixed compilation uses finite declaration projection/attribution/lowering/javac phases; stubs are not release classes and untrusted editor analysis uses `-proc:none`.

## Validation

```bash
python3 spec/diagnostics/validate.py
python3 -m json.tool spec/grammar/grammar-contract.json >/dev/null
python3 -m json.tool spec/diagnostics/catalog.json >/dev/null
python3 -m json.tool spec/diagnostics/fixtures.json >/dev/null
```

Expected/observed validator summary: `SPEC_MANIFEST_OK diagnostics=20 fixtures=24` with exit 0. These checks validate document consistency only; they do not claim a parser executes the fixtures.

## Remaining dependencies

- P03-W03 must provide complete Java SE 25 non-preview/JLS inventory and Java 21 differences; the EBNF deliberately imports that versioned base.
- ABI/property metadata schema and broader diagnostic serialization/source-map schema need their assigned P03/P04 contracts.
- Compiler owner must convert fixture manifest into executable lexer/parser/semantic tests; independent reviewer must add ambiguity and evaluation-order counterexamples before P03 acceptance.

## P03-R01 formal-closure repair

- All formerly undefined non-`JAVA_*` references are now either local productions or explicit Java SE 25 imports. `grammar-contract.json` enumerates 34 imported productions and their JLS 3/14/15/18/19 source boundary; the validator requires exact import use and reports `undefinedRefs=0`.
- `variableDeclarator` now has an optional initializer. Normative semantic constraints require initializers for `var`/`val`, while explicit Java declarations may omit them subject to definite assignment. A property initializer is optional; computed getter/no-storage and uninitialized final-storage forms are derivable.
- JLS chapter coverage is explicitly 1–19. Chapters 16 (definite assignment) and 19 (syntax) are tied to uninitialized/final property and imported grammar behavior; chapters 1/2 and all other chapters retain inventory scope.
- Adversarial fixtures now explicitly cover ternary/basic-for, return newline, multiline resource initializer, enum constant anonymous body, postfix property evaluation, byte compound narrowing, computed property without initializer and explicit uninitialized field.

Focused validation result: `SPEC_MANIFEST_OK diagnostics=20 fixtures=29 undefinedRefs=0 javaImports=34`, exit 0. This remains specification validation only; implementation and behavioral verification remain `NOT_IMPLEMENTED/NOT_VERIFIED`.
