# P08-R02 complete workspace-model API gate

TASK / AGENT_ID / BASE_REVISION: `P08-R02` / `/root/p00_recon` / shared `dev` worktree

STATUS: IMPLEMENTED — independent review required

## Baseline and gate

The former 15-line DTO-component-only list was insufficient because additions or changes to codec, validator, utility and enum APIs could pass unnoticed. `workspace-public-api.txt` is now a reviewed 422-line canonical snapshot with an explicit v1 baseline reason and SHA-256 of the canonical body.

The test discovers every compiled class in `dev/teyru/workspace/model` without a runtime update path. For every public/protected type it records type kind/modifiers, generic superclass, sorted interfaces, sealed permits, record components, constructors, fields, methods, generic parameters/returns and declared exceptions. Synthetic and bridge artifacts are excluded. Any addition, deletion or signature change changes the checked-in snapshot and digest.

An isolated mutation test proves the diff model reports additions and deletions independently; a signature change is necessarily one removal plus one addition. The existing scan still inspects every production classfile for Gradle, IntelliJ and LSP forbidden references.

## Evidence

- `./gradlew --no-daemon --dependency-verification=strict :workspace-model:spotlessApply :workspace-model:test --rerun-tasks` — exit 0; 13 tests, 0 failures/errors/skips.
- `./gradlew --no-daemon --dependency-verification=strict verifyQuick` — exit 0; 91 tasks; `PRODUCT_ARCHIVES_REPRODUCIBLE`, architecture boundaries and `VERIFY_QUICK_PASS`.
- Test XML: `workspace-model/build/test-results/test/TEST-dev.teyru.workspace.model.P08WorkspaceModelTest.xml`.

No API baseline is generated or rewritten by production/test runtime. Deliberate API changes require an explicit reviewed resource edit.
