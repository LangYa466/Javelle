# Lombok compatibility contract

Status: normative boundary frozen; inventory/oracle/native implementation NOT_IMPLEMENTED. Requirements: P03-08, P03-09, P20-01..P20-11, P29-11.

## Baseline and identity

The exact baseline is the P01-pinned Maven coordinate and checksum recorded in the version catalog/verification metadata; no dynamic `latest` is allowed. Before differential claims, P20 must record artifact SHA-256, `Implementation-Version`, complete public annotation/utility class inventory, official feature/config indexes and license. A mismatch is `JVL-LOMBOK-BASELINE-MISMATCH`.

Recognition follows ADR-0006: resolved FQN only, including explicit/wildcard/FQN forms for `lombok.*`, `lombok.experimental.*` and logging namespaces. Custom same-name annotations retain their semantics. Normal compilation uses Teyru symbols/AST/lowering; the Lombok processor/delombok is an isolated oracle only.

## Profiles and configuration

`native` consumes supported Lombok syntax and emits Teyru-native ABI without promising retained Lombok metadata. `strict-metadata` additionally preserves baseline-observable annotation namespace/retention and declares any isolated compile-only compatibility artifact and its MIT notices; it never activates the processor or duplicates classes already supplied by consumer Lombok.

Configuration lookup is deterministic from source directory toward workspace root, honors the baseline's `config.stopBubbling`, clear/list/import semantics, reports the value and source location, rejects cycles/path escape, and applies `flagUsage`. Unknown baseline keys/features/options are concrete diagnostics, never ignored.

## Feature matrix contract

The machine matrix created in P20 has one row per actual baseline feature, option and config key (not merely this document's examples). Required columns: stable requirement ID, family, resolved FQNs, artifact/class evidence, parameters/defaults, legal targets, positive/negative cases, Java-visible ABI, runtime behavior, config cases, combinations, retained metadata, IDE visibility, oracle command/evidence and status. Status is one of `NOT_IMPLEMENTED`, `IMPLEMENTED`, `VERIFIED`, `KNOWN_DIFFERENCE`; untested never maps to supported.

Families must cover inference; accessor/lazy; null/cleanup; constructors; toString/equality; Data/Value; Builder/SuperBuilder/Singular; With/WithBy; exceptions/locks; every baseline logger; access/defaults; Delegate/ExtensionMethod/UtilityClass/Helper; metadata/StandardException; Jacksonized; onX; every exported config key; marker/utility surface and aliases.

## Oracle and joint compilation

Oracle side compiles Java with the pinned official processor and, where useful, delombok. Candidate side compiles Teyru native lowering then javac. Compare runtime results, reflection, visibility, descriptors/generic signatures, annotations/framework behavior and necessary private structure—never require whole classfile equality or normalize away API differences. Runs are isolated, bounded and archive evidence with toolchain/config fingerprints.

Processors for consumer code follow the finite rounds contract in `abi/generated-java.md`; the Lombok oracle is not inserted there. IDE analysis is `-proc:none`.

Mandatory traps include throwable identity for SneakyThrows, lazy-null caching/concurrency/retry/reentry, Cleanup exception ordering, Singular mutation/null/order behavior, logger missing dependencies, onX spellings/targets, annotation collisions, config bubbling/import cycles, and processor nontermination.
