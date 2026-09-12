# P14 complete expressions, control flow, multi-line lambda

TASK / AGENT_ID / BASE_REVISION: `P14-W01` / `/root` / `dev` at `563fb98` (P13 ACCEPTED)

STATUS: **CONTRACT_FROZEN — P14 implementation IN_PROGRESS (round 2 of N)**

Source: `prompts/JAVELLE_IMPLEMENTATION_PLAN.md` section E, `## P14 — 完整表達式、控制流程與多行 lambda` (lines 819-838). This contract restates that section's 12 requirements (P14-01..P14-12) as a concrete acceptance matrix; it does not change the plan's scope. Depends on P13 (ACCEPTED).

## 1. Starting point

`RecursiveJavelleParser`'s expression engine (`ExpressionCursor`, ~90 lines) supports only: assignment (`=`), `||`, `&&`, `==`/`!=`, `<`/`>`/`<=`/`>=`, `+`/`-`, `*`/`/`/`%`, ternary `?:`, unary `!`/`-`/`+`, postfix member-access (`.`) and call (`(...)`), a `new` prefix, and a parenthesized-cast heuristic. Missing entirely: bitwise/shift operators, compound assignment operators, `instanceof` (with or without pattern binding), prefix/postfix `++`/`--`, lambdas (currently diagnosed `JV-DEV-0001` unsupported via the `->` token), method references (`::`, same unsupported path), array indexing (`[...]`), array literals, switch expressions, text blocks as real string values (parser still rejects them via `JV-DEV-0001`), and any pattern-matching syntax. `parseStatements()` supports only: `return`, `if`/`else`, local variable declaration, expression-statement, and local type declarations (P13 round 9); `for`, `while`, `do`, `switch`, `try`, `synchronized`, `assert` are all `unsupported(...)` placeholders. There is no `break`/`continue`/labels, no `throw`, no `yield`, no multi-catch, no try-with-resources.

## 2. Acceptance matrix (P14-01..P14-12)

| ID | Required evidence |
|---|---|
| P14-01 | All Java operator precedence levels, associativity, cast, conditional, assignment, instanceof/pattern grammar. |
| P14-02 | Method invocation, constructor reference, method reference, lambda, explicit generic invocation, chained expressions. |
| P14-03 | if/else, while, do-while, enhanced-for, colon basic-for, break/continue/labels. |
| P14-04 | return, throw, yield, assert, synchronized, try/catch/finally, multi-catch, resource headers. |
| P14-05 | switch statement/expression, arrow/colon cases, patterns/guards, valid Java 25 combinations. |
| P14-06 | Multi-line lambda block/return/capture semantics preserved; nested lambda, overload candidate, and method-reference context tests. |
| P14-07 | Omitted basic-for sections, multiple init/update clauses, ternary colon inside conditions, nested generic expressions. |
| P14-08 | Empty blocks and value-less returns preserved; a lone `;` is rejected (only migration output converts it to `{}`). |
| P14-09 | Full lookahead/recovery for expression continuation vs. statement boundary — no "insert a `;` per line" shortcut. |
| P14-10 | Partial-expression AST recovery so the LSP can work inside `foo.` or an incomplete lambda. |
| P14-11 | Precedence AST tests with real execution-result cross-checks, so two canceling bugs can't look correct on the surface. |
| P14-12 | Independent reviewer adds side-effect/throw/short-circuit cases confirming the parser hasn't changed expression grouping. |

## 3. Round 1 progress (full operator precedence table)

Implemented (P14-01): the `ExpressionCursor` precedence table expanded from 7 levels to 11: assignment tier now covers all compound assignment operators (`+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=`) alongside `=`, all right-associative; new tiers added for bitwise OR (`|`), bitwise XOR (`^`), bitwise AND (`&`), and shift (`<<`, `>>`, `>>>`) at the correct JLS 15 relative positions (bitwise-or < bitwise-xor < bitwise-and < equality < relational < shift < additive < multiplicative). Added `instanceof` as a special-cased binary form at the relational tier — since its right operand is a type (optionally with a single pattern-binding identifier, e.g. `x instanceof String s`), not a general expression, it can't reuse the generic recursive-descent path and gets its own `instanceOfRest` parse step producing an `InstanceOfExpression` node. Added prefix `++`/`--` (`PrefixExpression`) and postfix `++`/`--` (`PostfixExpression`) to `unary()`/`postfix()`, and unary bitwise-complement `~` alongside the existing `!`/`-`/`+`. The lexer already tokenized every one of these as single multi-char tokens (confirmed via `StatefulJavelleLexer`'s `MULTI` table) — this round was purely about consuming them correctly in the parser, no lexer changes needed. Nine new tests in `P14PrecedenceTest.java` cover: all bitwise/shift operators parsing, bitwise-and binding tighter than bitwise-or, all 11 compound assignment operators, right-associativity of chained assignment, instanceof with and without a pattern binding, prefix/postfix increment/decrement, unary `~`, and instanceof correctly sitting below `&&` in the tier ordering.

Explicitly NOT done yet (next rounds): lambdas (`->` still diagnosed `JV-DEV-0001` via `buildExpression`'s pre-check), method references (`::`, same unsupported path), array indexing (`[...]`) and array literals, switch expressions, casts beyond the existing simple `(Type) expr` heuristic (no cast-vs-parenthesized-expression full disambiguation, no generic types in casts), generic type arguments in expressions (e.g. explicit generic method invocation `this.<T>foo()`), record deconstruction patterns in `instanceof`, and text blocks as real string values (still `JV-DEV-0001`). P14-02 through P14-12 remain entirely unstarted.

## 3b. Round 2 progress (expression-bodied lambdas)

Implemented (P14-02 partial, P14-06 partial): real lambda expression parsing — `identifier -> expr` (implicit single param, no parens), `() -> expr`, `(a, b) -> expr` (untyped params), and `(int a, int b) -> expr` (typed params), producing a `LambdaExpression` node with `LambdaParameterDeclaration` children followed by the body expression. Detection (`looksLikeLambda()`) peeks for either a bare identifier immediately followed by `->`, or a balanced `(...)` immediately followed by `->`, before falling into the existing cast-heuristic/parenthesized-expression path in `primary()`. `buildExpression`'s blanket "any `->` token anywhere means unsupported lambda" pre-check is removed (method references via `::` and varargs via `...` remain on that unsupported path). Lambdas work as call arguments (`list.forEach(x -> x + 1)`) for free, since call-argument parsing already calls `expression(1)` per argument and lambda detection happens inside that same recursive-descent chain.

**Architecture limitation found and explicitly scoped out:** block-bodied lambdas (`x -> { ... }`) are diagnosed `JV-DEV-0001` rather than parsed, because `ExpressionCursor` operates over a single pre-sliced, single-line `List<Token>` window (built by `parseExpressionUntilBoundary`, which stops at a line boundary) — there is no path from inside the token-index-based expression cursor back into the streaming `parseStatements()` needed to parse a genuine multi-line block body. Making multi-line lambda bodies real (P14-06's actual point) requires either restructuring `ExpressionCursor` to share the outer parser's token stream directly, or having `parseExpressionUntilBoundary` special-case a trailing `-> {` and hand off to `parseStatements()` before slicing. This is real, non-trivial architecture work and is deferred to its own round rather than attempted piecemeal here.

Fixed a stale P05 fixture: `P05FrontendTest.everyFrozenUnsupportedRepresentativeIsSingleAndBalanced()` had `"lambda" -> "class C { Object m() {\n Object x = value -> value\n return x\n }}"` asserting exactly one `JV-DEV-0001` diagnostic — this expression now parses as a real lambda with zero diagnostics, so the case was removed from that frozen-unsupported map (the same pattern used throughout P13 whenever a round graduated a construct from unsupported to real). Seven new tests in `P14LambdaTest.java` cover: implicit single param, zero param, multiple untyped params, typed params, lambda-as-call-argument, block-bodied-lambda-still-unsupported, and method-reference-still-unsupported (regression guard that `::` wasn't accidentally affected).

Explicitly NOT done yet: block-bodied/multi-line lambdas (see limitation above), method references (`::`), array indexing/literals, switch expressions, casts beyond the existing simple heuristic, generic type arguments in expressions, record deconstruction patterns, text blocks as real values. P14-03 through P14-12 remain entirely unstarted.

## 4. Test requirements

Same discipline as P13: fixture-backed JUnit tests per requirement, full `gradlew test` green, `verifyQuick` green, no silent regression of already-accepted P05-P13 behavior. Given the size of this phase, expect substantially more rounds than P13 (12+).

## 4. Acceptance

Independent review only after all 12 rows have real evidence — each round is a checkpoint, not a stage-exit claim.
