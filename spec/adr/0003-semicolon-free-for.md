# ADR-0003: Semicolon-free traditional for
Status: Accepted design; P03-03; implementation NOT_IMPLEMENTED.

Decision: traditional `for` uses exactly two structural colons; enhanced-for retains its one colon. Colon is not a type-declaration operator.

Alternatives: newline-separated clauses are ambiguous with expressions; retaining semicolons violates the lexical contract. Consequences: parser, formatter, migration and docs share one grammar rule.

Counterexamples/tests: `for (int i=0 : i<3 : i++)` succeeds; Java-style structural semicolons fail. Enhanced `for (String x : xs)` succeeds; `name: Type` fails.
