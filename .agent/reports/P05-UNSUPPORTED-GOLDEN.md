# P05-R03B unsupported constructs and AST golden

Agent `/root/p00_repair`. Owned parser/frontend AST, P05 tests/snapshot resources and this report. Lexer/token files were R03A read-only dependencies.

Status: IMPLEMENTED; independent review pending.

The parser routes while/do, basic for, switch, try/catch/finally-shaped input, synchronized, assert, enum, record, interface/annotation-shaped declarations, modules, lambda, method reference, varargs and text blocks through balanced `UnsupportedSyntaxNode` paths with one `JV-DEV-0001`, preserving surrounding class/method boundaries and progress. No unsupported representative is silently accepted or cascades generic syntax diagnostics. Nested brace/parenthesis scanning is bounded by EOF/resource checkpoints.

`FrontendSnapshot.canonical` emits deterministic complete preorder records with node kind/name, exact raw range, stable ordinal, parent ordinal, accessor visibility and exact diagnostic code/range/message. `p05/ast-snapshots.txt` contains the full reviewable output for all eleven manifest cases; the manifest harness compares it byte-for-byte after separately checking expected ordered anchors and diagnostic spans. Any added/reordered node, payload, parent, span or diagnostic mutates the golden test.

Public API golden intentionally changes 706/hash `025e73…d37e0` → 708/hash `817c03…c4671`, exactly the new public `FrontendSnapshot` type and `canonical(ParseResult)` method. No removal intended.

Final shared-source verification after R03A stabilized: `./gradlew --dependency-verification=strict :compiler-core:spotlessCheck :compiler-core:test verifyQuick`, exit 0. Compiler-core ran 55 tests (parser/frontend 10, lexer repair 8), 0 failures/errors/skipped; aggregate 77 tasks. Evidence `.agent/logs/P05-R03B-final.txt`.
