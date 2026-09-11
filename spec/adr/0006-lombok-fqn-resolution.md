# ADR-0006: Lombok recognition by resolved FQN
Status: Accepted design; P03-08; implementation NOT_IMPLEMENTED.

Decision: expand only annotations resolving to supported `lombok.*`, `lombok.experimental.*` or documented logging FQNs through explicit, wildcard or fully-qualified imports. There is no magic prelude.

Alternatives: simple-name matching corrupts custom annotations; bundling the processor violates native lowering. Consequences: unresolved/ambiguous supported annotations produce diagnostics and known unsupported baseline features cannot be ignored.

Counterexamples/tests: imported `lombok.Data` expands while `com.example.Data` does not; an ambiguous `Data` fails rather than choosing Lombok. Test wildcard, FQN, shadowing and compiler-consumed annotation removal only.
