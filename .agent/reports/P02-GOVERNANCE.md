# P02-R01 — governance / manifest repair

- Agent `/root/p00_spec`; base `29321d8479e92ac05b1f1ec963636464b24faee8`
- Scope: P02-02/03/04/05/09/10/11/12 governance subset. Root build/module/testkit/ledgers were not changed.
- User-owned `.idea/vcs.xml` was not read or modified.

## Implemented

1. `.editorconfig` and `.gitattributes` freeze UTF-8/LF, CRLF for Windows launchers/fixtures, executable Unix launcher and binary archive handling. The executable checker verifies actual `gradlew` LF+mode and `gradlew.bat` CRLF.
2. `compatibility/requirements.json` contains 720 unique normative rows: B1–B5, C1–C7, all 672 `P00-01` through `P55-12`, and UAT-01–36. Every row starts `NOT_IMPLEMENTED`/`NOT_VERIFIED` with role/section and empty evidence mappings.
3. `.agent/schema` defines tracked task, ownership, requirement and worktree-policy contracts. The semantic validator checks schema versions, statuses, unique IDs, dependency existence/DAG, owners, verified evidence, exact normative coverage, safe relative locks, active writer collisions, stale locks and integration-only Git mutation policy.
4. `.agent/tools/governance.py` provides dependency-free `validate`, `selftest`, `line-endings`, `archive-repro`, and fingerprint-guarded `atomic-update`. Atomic update takes an exclusive lock, validates expected SHA and JSON, writes a same-directory temp file, fsyncs it, atomically replaces, then fsyncs the parent. Failure retains prior bytes.
5. Project-local `.codex` enables at most four subagents and defines a reviewer role without model/sandbox/approval escalation. Python `tomllib` validates the exact safe keys; global settings are untouched.
6. `.github/workflows/verify-quick.yml` uses immutable `actions/checkout` commit `34e114876b0b11c390a56381ad16ebd13914f8d5` (v4.3.1), least-privilege contents permission, timeout, governance selftest and strict `verifyQuick`. Status remains `CI_NOT_RUN` until an actual run URL exists.
7. Reproducibility selftest creates two independent ZIPs from real license/notice inputs with fixed path order, timestamp and Unix mode, then compares every byte. Build owner still must wire a test over actual Gradle product JARs before P02-02 can be accepted.

## Independent executable interface

```bash
python3 .agent/tools/governance.py validate
python3 .agent/tools/governance.py selftest
python3 .agent/tools/governance.py line-endings
python3 .agent/tools/governance.py archive-repro
```

Observed in this work package: `validate` exit 0; `selftest` exit 0. `selftest` includes duplicate task ID, missing dependency, overlapping writer, stale lock, wrong fingerprint, prior-state retention, LF/CRLF and double-archive byte equality negatives/positives.

## Build-owner integration contract

- Add the four read-only checks to `verifyQuick` without copying/weakening their logic. The Python entrypoint has no third-party dependency and resolves repository root from its own location.
- Keep `verifyAll`/`releaseCheck` fail-closed readiness semantics. Requirements at NOT_IMPLEMENTED/NOT_VERIFIED are expected and must not make P02 `verifyQuick` claim future functionality.
- Actual Gradle archive reproducibility needs two clean archive builds and byte comparison; the governance ZIP test validates the normalization/check mechanism but is not product-archive evidence.
- CI workflow existence is P02 implementation evidence, not remote execution evidence. Record `CI_NOT_RUN` until GitHub supplies a run/attempt URL and result.

## Remaining gates

- Independent reviewer must mutate a copied manifest for cycle/missing evidence/path traversal and confirm nonzero outcomes, exercise concurrent atomic-update locking, and check workflow YAML/action pin.
- Build owner must wire governance checks and actual Gradle archive comparison into lifecycle tasks without editing `.idea/vcs.xml`.
- P02-06 bytecode architecture and P02-07 testkit gaps belong to other repair owners; this package does not claim them.

## P02-R05 focused repair

- `selftest` and `product-archives` now create `.agent/tmp` with `parents=True` before using it. `atomic-update` likewise creates only the target parent directory before opening its same-directory lock; it still requires an existing ledger and matching fingerprint, so it cannot silently initialize or overwrite state.
- CI now provisions Temurin Java 25 with official `actions/setup-java` tag `v5.2.0` resolved to immutable commit `be666c2fcd27ec809703dec50e508c2fdc7f6654`. Checkout remains pinned to `34e114876b0b11c390a56381ad16ebd13914f8d5`; the project uses its trusted wrapper and `--dependency-verification=strict`.
- Candidate-copy testing excludes `.git`, `.gradle`, all build outputs, `.idea`, `.agent/logs`, and `.agent/tmp`; governance selftest and strict `verifyQuick` must recreate disposable state and pass there. Remote workflow status remains `CI_NOT_RUN` until an actual GitHub run supplies evidence.
