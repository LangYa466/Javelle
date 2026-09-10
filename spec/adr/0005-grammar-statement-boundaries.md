# ADR-0005: Grammar-defined statement boundaries
Status: Accepted design; P03-02/P03-03; implementation NOT_IMPLEMENTED.

Decision: lexer/parser grammar and source spans determine termination; newline is trivia except in explicitly significant productions. No text-level newline-to-semicolon rewrite exists.

Alternatives: line rewriting breaks multiline lambdas/chains/text blocks; automatic insertion creates hidden ambiguity. Consequences: CST retains trivia and Unicode/source positions through recovery.

Counterexamples/tests: a multiline invocation remains one statement; two statements on one line without a legal boundary fail. Test comments, CRLF, Unicode escapes and text blocks containing newlines/semicolons.
