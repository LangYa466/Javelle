# P04-R02 diagnostic / generation / budget repair

TASK / AGENT_ID / BASE_REVISION: `P04-R02` / `/root/p00_spec` / shared post-P04-review candidate

STATUS: **IMPLEMENTED — independent review required**

## Requirement repairs

| ID | Implemented evidence |
|---|---|
| P04-04 | `FixKind` is serialized; fix edits are defensively copied and canonically sorted by fingerprint-bound source/unit/range/replacement before overlap validation. Legal unsorted non-overlapping edits normalize; actual overlaps reject. Diagnostic primary, related and fix ranges must use raw UTF-16, match an exact `SourceId` fingerprint, and be in bounds. Existing bounded parser retains cumulative byte/depth, duplicate-key, missing/type/version and additive-unknown handling. |
| P04-06 | `GeneratedFile` validates URI, non-null fields and SHA-256 of exact UTF-8 Java text; `create` computes it. `GeneratedFilePublisher` validates a complete sorted batch for duplicate paths before one atomic reference publication; a failed candidate leaves the previous complete batch visible. |
| P04-07 | Byte accounting is cumulative and overflow-safe. Token/node/diagnostic counters remain cumulative; nesting rejects negative/excess depth; deadline and cancellation remain injected/checkpointed. `SnapshotPublisher` and generated batch publisher expose no partial candidate after exceptions. |

## Adversarial coverage

- Reverse-ordered legal fixes normalize and JSON round-trip with kind; overlapping edits reject.
- Wrong source fingerprint, primary out-of-bounds range, wrong range unit and fix bounds reject.
- Mismatched generated hash, traversal URI and duplicate generated path reject; prior batch remains intact.
- Two byte increments exceeding the total, negative bytes, arithmetic overflow path, every count limit, negative/excess nesting and expired deadline fail closed.
- Duplicate JSON key, wrong field type, cumulative byte ceiling and deeply nested unknown additive field reject.

## Tests

1. Initial command under ambient Java 21: `./gradlew --no-daemon --dependency-verification=strict :compiler-core:test` — exit 1 at configuration; build requires Java 25. This is an environment invocation error, not a product result.
2. Required runtime, forced execution: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH ./gradlew --no-daemon --dependency-verification=strict :compiler-core:test --rerun-tasks` — exit 0, 8 actionable tasks executed, no skipped Gradle test task.

No claim is made for P04-01/02/03/05/08/09/11/12 or aggregate `verifyQuick`; those belong to other repair owners/review.

REVIEW: pending independent `/root/p00_review` or coordinator-assigned reviewer. Implementer does not ACCEPT.
