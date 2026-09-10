# P04-R04 final repair

Agent `/root/p00_repair`. Owned changes are SourceId/SourceFile identity validation, typed IR records, generated/source range unit invariants, `FinalRepairTest`, and this report. No ledger/build/IDE changes.

Status: implementation complete, independent review pending.

## Repairs

- P04-01/P04-07: `SourceId.forContent` hashes the exact supplied UTF-8 byte sequence, including a BOM when present. `SourceFile.decode` recomputes SHA-256 before decoding/publication and rejects contradictory identity with `JVL-SOURCE-CONTENT-ID-MISMATCH`; its published fingerprint is therefore identical to `SourceId`.
- P04-06: the sealed typed IR now contains explicit temporary, symbol read, typed conversion, write, branch and ordered sequence forms. Constructors require node/type/origin and validate initializer/write/boolean-condition types; sequence index is observable evaluation order.
- P04-08: `GeneratedRange` and `SourceLocation` require `UNICODE_CODE_POINT` at construction, so every forward, overlap, reverse and composition query rejects RAW/translated UTF-16 or byte ranges rather than implicitly converting.

Regression command: `./gradlew --dependency-verification=strict :compiler-core:spotlessApply :compiler-core:test verifyQuick`, exit 0. Compiler-core: 37 tests, 0 failures/errors/skipped. Aggregate: 78 tasks, BUILD SUCCESSFUL. Evidence `.agent/logs/P04-FINAL-REPAIR-final.txt`.

Public API golden review: v1 changed from 562 entries/hash `4ab4a1…cff66` to 623 entries/hash `be1510…6d432`. The intentional additions are `SourceId.forContent`, strict coordinate identity/query contracts, and explicit `IrTemporary`/`IrRead`/`IrConvert`/`IrBranch`/`IrSequence` forms. No removal is intended; the complete bidirectional reflection gate produced the new count/hash and must be independently reviewed.
