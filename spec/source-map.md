# Source map and debug contracts

Status: normative contract frozen; implementation NOT_IMPLEMENTED. Requirements: P03-10, P06-04, P19-04.

## Diagnostic source map

The versioned map uses UTF-8 JSON, `schemaVersion: 1`, normalized workspace-relative source/generated URIs, and half-open offsets measured in Unicode code points plus explicit line/UTF-16 coordinates for protocol conversion. A segment records generated range, zero or more original ranges, mapping kind (`exact`, `expanded`, `synthetic`), stable node/symbol ID and optional generated-member origin. Many generated ranges may map to one origin; one expanded range may carry ordered related origins. Synthetic code maps to the nearest owning declaration and is flagged synthetic, never fabricated as exact user text.

Unknown additive fields are ignored within version 1; higher major versions fail `JVL-SMAP-UNSUPPORTED-VERSION`. Overlapping generated ranges require explicit priority and deterministic tie-breaking (exact, expanded, synthetic; then smallest range). Paths escaping the workspace or archive are rejected.

Test obligations: CRLF, emoji/UTF-16, Unicode escapes, multiline lambda, property accessors, synthetic helpers, many-to-one, nested expansion, malformed/future schemas, relocation and deterministic serialization.

## Debug mapping is separate

This JSON map supports diagnostics/navigation only. JVM breakpoint/step behavior requires separately verified classfile line tables and SMAP/JSR-45 strata plus an IDEA Java debugger position adapter (or a later accepted DAP ADR). It must test breakpoints, steps, locals, exceptions, multiline lambdas and generated getter/setter regions. Successful diagnostic remapping MUST NOT be reported as debugger support or arbitrary third-party debugger compatibility.
