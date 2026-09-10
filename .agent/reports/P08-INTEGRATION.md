# P08-W03 Gradle exporter and independent client

TASK / AGENT_ID / BASE_REVISION: `P08-W03` / `/root/p00_recon` / shared `dev` worktree

STATUS: IMPLEMENTED — independent review required

## P08-02

- `WorkspaceExportInput` is an immutable Gradle/editor-neutral capture DTO.
- `WorkspaceModelExporter` converts neutral input to the single `workspace-model` schema, recomputes fingerprints, and rejects invalid exports.
- `GradleWorkspaceInputAdapter` is the deliberately thin Gradle boundary. It captures real Java `main`/`test` source sets, conventional present/missing Javelle roots, generated roots, module identities and the running toolchain without registering tasks or implementing the future P09 compilation plugin.
- A real Gradle `ProjectBuilder` multi-project fixture (`:app`, `:lib`) verifies main/test and missing-root preservation plus portable codec round-trip.

## P08-12

The integration test builds the actual `workspace-model` JAR, writes an exported two-module portable document, compiles a standalone Java client with `javac --release 21 -cp <workspace-model.jar>`, then launches it in a separate JVM with only that JAR. The client reads, validates and byte-for-byte round-trips the model and prints `EXTERNAL_WORKSPACE_OK`. It has no compiler or Gradle dependency.

## Evidence

- `./gradlew --no-daemon --dependency-verification=strict :gradle-plugin:spotlessApply :gradle-plugin:test --rerun-tasks` — exit 0; 3 tests, 0 failed/skipped. XML: `gradle-plugin/build/test-results/test/TEST-org.javelle.gradle.workspace.P08WorkspaceIntegrationTest.xml`.
- `./gradlew --no-daemon --dependency-verification=strict verifyQuick` — exit 1 in `verifyProductArchives`: two rebuilds differed only for `compiler-cli` JAR; `gradle-plugin` and `workspace-model` JAR hashes were identical. This is concurrent shared-tree CLI ownership, not an exporter/test failure. Architecture and compiled-boundary gates passed before that failure.

No task wiring or claim of a complete P09 Gradle plugin is included.
