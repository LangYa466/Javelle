# P06-W02 early semantic/lowering/emitter

## Status

`IMPLEMENTED / REVIEW_PENDING`. The bound semantic model, early type checker, typed-IR lowering,
deterministic Java emitter, property expansion, diagnostics and real Java consumer checks are
implemented. `EarlySemanticBinder` consumes only the P05 `ParseResult` AST and its lossless CST
tokens; it does not rescan source text or use regular expressions.

## Paths

- `compiler-core/src/main/java/dev/teyru/compiler/core/semantic/**`
- `compiler-core/src/main/java/dev/teyru/compiler/core/lowering/EarlyLowerer.java`
- `compiler-core/src/main/java/dev/teyru/compiler/core/emitter/**`
- `compiler-core/src/test/java/dev/teyru/compiler/core/P06EmitterTest.java`
- `compiler-core/src/test/resources/public-api-v1.txt`

## Verified behavior

- P06-01: package/import/class/field/property/method/parameter/local/return/if and the P05
  expression nodes bind from AST+CST to symbols and types. Recovered, unsupported, unresolved and
  mismatched-return inputs fail closed.
- P06-02/P06-03: supported bound nodes lower to typed temporaries, reads, writes, converts,
  branches and sequences with stable order and source origins; unsupported constructs fail closed.
- P06-04/P06-05: readable Java is byte deterministic, uses legal Java semicolons, stable headers,
  stored-property backing fields and default/custom accessors. It contains no timestamp or absolute
  path.
- P06-06: generated Java and a separate Java consumer compile with real `javac --release 21`, run
  in a separate JVM, and are checked by reflection. Direct access to private backing storage is a
  compilation-negative.
- P06-07: source-map member segments distinguish direct declarations from property expansion and
  retain generated-member origins.
- P06-08: generic, compound/update and type mismatch paths return structured fail-close diagnostics.

## Tests and evidence

- `./gradlew --dependency-verification=strict :compiler-core:spotlessApply :compiler-core:test verifyQuick`
  exited 0. Compiler-core executed 62 tests with 0 failures/skips; `verifyQuick` exited 0.
- Log: `.agent/logs/P06-W03-final.txt` (ignored working evidence).
- One test executes parse -> bind -> emit -> real javac -> separate JVM and checks the generated
  property and method behavior. Three corpus variants verify unresolved, type mismatch and
  unsupported fail-close paths.
- The compiled public API gate intentionally changed from 708 to 938 entries for the new semantic,
  lowering, emitter and binder APIs. Reviewed golden is `sha256=6d379fa60affd679ead4dc07a4bcc509c77e832a208d3f60de699c364b0e620f`.

## Review dependency

Independent review must reproduce the end-to-end command and probe binding of nested expressions,
scope shadowing and custom-property access before this package can become ACCEPTED.
