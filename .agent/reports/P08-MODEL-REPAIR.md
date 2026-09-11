# P08-R01 workspace-model repair

TASK / AGENT_ID / BASE_REVISION: `P08-R01` / `/root/p00_recon` / shared `dev` worktree

STATUS: IMPLEMENTED — independent re-review required

## Repaired review findings

- P08-01: checked-in record-component API golden; every compiled production class is scanned for Gradle, IntelliJ and LSP references. Round-trip fixture covers every frozen DTO field and immutable collections.
- P08-03/05/10: portable writing rejects unsafe logical/project paths; validator rejects decoded traversal, query, fragment, archive separator, authority, malformed/non-absolute file URI, case-unknown ambiguity and descendant source-root overlap. A deterministic portable JSON golden is checked in and scanned for host/JDK/drive/UNC leaks.
- P08-04: non-empty processor path requires explicit `TRUSTED_PROCESSORS` authorization, not mere presence; source release cannot exceed parsed toolchain major.
- P08-06: on-disk snapshot participates in configuration/model fingerprints; set-like source roots are canonicalized while compiler path order remains significant. Tests mutate roots, snapshot, classpath, options, trust, JDK and property metadata.
- P08-08: target source-set identity is validated; SOURCE_VISIBILITY SCCs produce `JV-WS-SCC-SOURCE-VISIBILITY`, distinct from build-order cycles.
- P08-09: normalized overlay URI, non-negative version, SHA-256 syntax/content match and current on-disk base are enforced. `analysisFingerprint` combines overlay metadata without changing the on-disk model fingerprint.
- P08-11: newer-minor unknown top-level additive fields are retained in the typed model's reserved extension envelope and restored at their original top-level location when serialized.

## Evidence

- `./gradlew --no-daemon --dependency-verification=strict :workspace-model:spotlessApply :workspace-model:test --rerun-tasks` — exit 0; 12 tests, 0 failed/skipped. XML: `workspace-model/build/test-results/test/TEST-org.javelle.workspace.model.P08WorkspaceModelTest.xml`.
- `./gradlew --no-daemon --dependency-verification=strict verifyQuick` — exit 0; 88 tasks, `ARCHITECTURE_OK`, `ARCHITECTURE_BOUNDARIES_OK`, `VERIFY_QUICK_PASS`.

## Review handoff

Reproduce processor/toolchain/path/overlay/SCC negatives, mutate every fingerprint family, inspect both goldens, and verify a newer-minor unknown additive field survives `read -> typed model -> writePortable`. P08-02/07/12 remain integration-owned and outside this repair.
