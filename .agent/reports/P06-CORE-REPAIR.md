# P06-R01 compiler-core repair

## Status

`IMPLEMENTED / REVIEW_PENDING` for the assigned core failures P06-01 through P06-04 and the
canonical P06-06 source pipeline. Driver-owned P06-05/P06-07/P06-10 remain outside this package.

## Repairs

- P06-01: `BoundCompilationUnit` deduplicates imports and orders non-static then static groups,
  lexically within each group. Static imports retain the required separating space.
- P06-02: the parser preserves a property initializer as a concrete `PropertyInitializer` node;
  the binder carries it into `BoundProperty`. Stored/default/custom and computed properties retain
  storage classification and per-accessor visibility. A computed-property regression proves no
  backing field is emitted.
- P06-03/P06-06: canonical parsed source now binds `other.name` to the real
  `PropertyDescriptor`, emitting `other.getName()` / `other.setName(next)`. The parsed custom setter
  emits `this.name = value.trim()`. A real javac + separate JVM consumer passes spaced input and
  produces `Alice`.
- P06-04: field/property initializers, return/local/expression/if expressions have direct segments.
  Property storage/getter/setter have distinct expanded intervals and stable feature origins. The
  stable readable Java golden remains byte deterministic.
- Unsupported/recovered/unresolved/type-mismatch inputs remain fail-closed.

## Tests

- `./gradlew --dependency-verification=strict :compiler-core:test verifyQuick`
  — exit 0; compiler-core 63 tests, zero failures/errors/skips; `verifyQuick` pass.
- Focused canonical source pipeline and computed property tests also exited 0.
- Evidence: `.agent/logs/P06-R01-property.txt`, `.agent/logs/P06-R01-full-3.txt`,
  `.agent/logs/P06-R01-verify.txt` (ignored logs).
- Public API golden remains `entries=938`,
  `sha256=6d379fa60affd679ead4dc07a4bcc509c77e832a208d3f60de699c364b0e620f`;
  this repair introduced no public signature drift.

Independent review must rerun the canonical pipeline and mutate import ordering, initializer
retention, property binding, trim lowering, computed storage and the three component map origins.

## P06-R03 source-map unblock

- Generated header and class boilerplate now have explicit `SYNTHETIC` segments with
  `COMPILER_SYNTHETIC` member origins and nonempty reasons.
- Returned expressions, field/property initializers, locals, expression statements and if
  conditions have narrower direct segments. Property storage/getter/setter retain separate
  expanded intervals and feature identities.
- A CRLF + astral emoji regression starts with raw UTF-16 node offsets, emits Java, queries the
  generated missing-name position in Unicode code points, and proves the original interval equals
  `SourceFile.convertBoundary(... RAW_UTF16 → UNICODE_CODE_POINT)` rather than copied raw offsets.
- Focused test and strict `:compiler-core:test` both exited 0; compiler-core now executes 64 tests.
  Evidence: `.agent/logs/P06-R03-target-2.txt`, `.agent/logs/P06-R03-full.txt`.
