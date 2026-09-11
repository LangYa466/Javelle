# P11 minimal IntelliJ IDEA plugin

TASK / AGENT_ID / BASE_REVISION: `P11-W01` / `/root` / `main` at `39e348dd5a23aed56aeb52fbac76edb640539c93`

STATUS: **IMPLEMENTED, INDEPENDENT REVIEW REQUIRED**

## Implementation summary (self-reported, not yet independently reviewed)

- `intellij-plugin` module now applies `org.jetbrains.intellij.platform` (2.18.1), pinned to `intellijIdeaUltimate("2026.1.4")`, via a settings-level `org.jetbrains.intellij.platform.settings` plugin registered in root `settings.gradle.kts` (required for the `FAIL_ON_PROJECT_REPOS` repository mode already in force repo-wide).
- `JavelleFileType` (`.javelle`, plain-text based) and `JavelleLspServerSupportProvider`/`JavelleLspServerDescriptor` (using the platform's built-in, though now-deprecated-in-this-SDK-generation, `com.intellij.platform.lsp.api` — suppressed, not migrated, since it is still the documented working integration point) are registered via `plugin.xml`.
- `:intellij-plugin:buildPlugin` bundles the real `:language-server:installDist` output (`javelle-lsp/`) into the plugin ZIP. Two real packaging bugs were found and fixed during implementation: (1) an accidental extra `Javelle/` directory nesting that would have made the runtime launcher-path lookup wrong, (2) the executable bit on `bin/javelle-lsp` was silently dropped by the Sync/Zip copy pipeline and had to be restored explicitly on both the intermediate `Sync` output and the final `Zip` copy spec.
- The plugin ID was changed from the originally-contracted `org.javelle.intellij` to `org.javelle.ide` after `verifyPluginStructure` correctly rejected the former: Marketplace policy forbids the word "intellij" in a plugin ID.
- `:intellij-plugin:verifyPlugin` (real JetBrains Plugin Verifier, run against the network-resolved pinned IU build) reports **Compatible** against `IU-261.27258.48` (a newer build within the same pinned `2026.1.4`/`261.*` line than the exact `261.26222.65` P01 had locally probed) with only 4 usages of deprecated API (the same suppressed `LspServerDescriptor`/`LspServerSupportProvider` deprecation) and zero errors.
- Two real tests pass: `P11BundledLspLauncherTest` (P11-05: extracts the launcher from the actual built plugin ZIP and proves it starts the real P10 server end-to-end over the real framed stdio protocol) and `P11FileTypeAndLspWiringTest` (P11-04: a headless `BasePlatformTestCase` fixture proving `.javelle` resolves to the registered file type and that `JavelleLspServerSupportProvider` offers a descriptor accepting the opened file), both wired into `verifyImplementedGates` in root `build.gradle.kts`.
- `gradle/verification-metadata.xml` gained checksums for the IntelliJ Platform Gradle Plugin's own large dependency tree (structure-ide/intellij, jackson, retrofit, jaxb, smallrye, undertow, xnio, etc. — resolved with `--write-verification-metadata`, not hand-guessed) plus the settings-plugin marker POM and the pinned `idea-2026.1.4.tar.gz` installer (reused P01's already-verified checksum rather than re-downloading 1.5GB).
- Not yet done: `idea-ui-debug` as a gate remains genuinely open — this delivers headless plugin integration and binary-compatibility verification, not interactive in-IDE debugging support, which stays out of scope per section 6 below.

## 1. Scope and non-goals

P11 wires the already-accepted P10 `javelle-lsp` stdio server into IntelliJ IDEA Ultimate as a real, packaged plugin using the platform's built-in LSP client (`com.intellij.platform.lsp.api`), pinned to the exact IDE build P01 already probed: **IU 2026.1.4, product build 261.26222.65** (`since-build=261`, `until-build=261.*`). The plugin is real: a `plugin.xml` descriptor, a `LspServerSupportProvider` that launches the actual `javelle-lsp` distribution built by P10, and file-type registration for `.javelle`. It is verified by running the pinned JetBrains Plugin Verifier against the real packaged plugin ZIP and the real pinned IDE build — not a fabricated/empty plugin, and not a documentation-only claim.

P11 does not claim: marketplace publication, support for any other IDE product/build range, a custom language grammar/PSI (`.javelle` files are treated as plain-text/LSP-driven, no `ParserDefinition`), a debugger, refactoring support, or an in-process editor smoke test with a rendered UI (IntelliJ's UI test framework requires a display and remains out of scope; verification is headless: Plugin Verifier plus a headless application-level test using `IntelliJ Platform Test Framework`'s light fixture, which loads the plugin and platform services without a window server).

## 2. Toolchain and inputs

- IntelliJ Platform Gradle Plugin `2.18.1` (`org.jetbrains.intellij.platform`), applied only to `:intellij-plugin`.
- Platform dependency: `intellijIdeaUltimate("2026.1.4")`, resolved from JetBrains' Maven repositories (`defaultRepositories()`), content/version pinned like every other external input in this repo; `gradle/verification-metadata.xml` gains entries for whatever this pulls in.
- Bundled plugin dependency on the platform's own `com.intellij.platform.lsp` module (no external LSP client library; IntelliJ's own API is used, matching what P01 probed: `LspServerManager`, `LspServerDescriptor`, `LspServerSupportProvider`).
- The plugin bundles the P10 `javelle-lsp` install (`:language-server:installDist` output) as plugin resources and launches it as a subprocess via the platform API, mirroring how P09's Gradle plugin treats the Javelle compiler executable as an explicit, version-fingerprinted input rather than a PATH lookup.
- JetBrains Plugin Verifier CLI (pinned exact version + published checksum, downloaded like every other pinned tool in this repo — see P01's JDK/IDE pinning method) runs against the packaged plugin ZIP and the pinned IU 2026.1.4 build. `.agent/tmp/P01-JDK-IDE/idea-selected` (already extracted locally, gitignored scratch space) may be used to speed up local iteration, but the checked-in build must not depend on that gitignored path — the real build resolves/downloads its own pinned IDE dependency through the IntelliJ Platform Gradle Plugin so a fresh checkout works unmodified.

## 3. `plugin.xml` and registration

- Plugin ID `org.javelle.ide` (Marketplace policy forbids the word "intellij" in a plugin ID), name `Javelle`, vendor left as the local/unpublished placeholder (no Marketplace claim).
- `<idea-version since-build="261" until-build="261.*"/>`.
- Registers a `com.intellij.fileType` for `.javelle` (plain-text based, no custom lexer) so files open and are recognized.
- Registers a `com.intellij.platform.lsp.api.LspServerSupportProvider` implementation scoped to Javelle files; `LspServerDescriptor` launches the bundled `javelle-lsp` distribution's `bin/javelle-lsp --stdio` script, matching the P10 contract's transport (framed stdio JSON-RPC), and sends the same `initializationOptions.javelle.workspaceModelUri` shape P10's server already accepts, sourced from the P09 Gradle plugin's exported `build/javelle/workspace/main.json` when present (best-effort: if no exported model exists for the opened project, the server still starts with no workspace configured, matching P10's documented fallback).
- No other capability is advertised beyond what P10 already advertises (hover, completion, diagnostics, lifecycle) — the plugin does not claim IDE features P10 doesn't implement.

## 4. Packaging

- `:intellij-plugin:buildPlugin` (Gradle IntelliJ Platform Plugin's standard task) produces the plugin ZIP.
- The ZIP's `lib/` contains only the plugin's own compiled classes/resources and the bundled `javelle-lsp` install (jars, launcher script); it must not bundle Gradle/Kotlin/JUnit test-only jars or IDE platform jars (`compileOnly`/`intellijPlatform` dependency, not repackaged).
- `pluginVerifier` (IntelliJ Platform Gradle Plugin's task) runs against the built ZIP for the pinned `IU-2026.1.4` (`261.26222.65`) target and must report no `ERROR`-level compatibility problems for the plugin's own registered extension points; only genuinely relevant problems fail the build, not merely a nonzero warning count.

## 5. Test requirements (P11 acceptance matrix)

| ID | Required evidence |
|---|---|
| P11-01 | `plugin.xml` declares the pinned `since-build`/`until-build`, the `.javelle` file type, and the `LspServerSupportProvider`; `verifyPlugin` (structural descriptor check) passes. |
| P11-02 | `buildPlugin` produces a ZIP containing only the plugin's own classes/resources plus the bundled `javelle-lsp` distribution; no IDE platform or test-only jar is bundled. |
| P11-03 | `pluginVerifier` runs against the real ZIP and the pinned `IU-2026.1.4` build; zero `ERROR`-severity compatibility problems reported for this plugin's code. |
| P11-04 | A headless platform test (light fixture, no display) opens a `.javelle` file in an in-memory project, asserts the file type resolves to the registered Javelle file type, and asserts `LspServerSupportProvider` offers a descriptor for it whose command line references the bundled `javelle-lsp` launcher — proving wiring without needing a rendered UI. |
| P11-05 | A second headless test starts the real bundled `javelle-lsp` subprocess end-to-end (no IDE UI involved — same style as P10's `P10BlackBoxTest`) through the `LspServerDescriptor`'s created process, sends a framed `initialize`/`initialized`/`didOpen`, and asserts a `textDocument/publishDiagnostics` notification is received — proving the packaged launcher script inside the plugin ZIP actually starts the real P10 server, not a stub. |
| P11-06 | Fresh-checkout reproducibility: building `:intellij-plugin:buildPlugin` and running `pluginVerifier` from a clean clone (no dependency on `.agent/tmp` or any other gitignored path) succeeds with only network access to the pinned repositories, mirroring P09/P10's offline-after-resolve discipline. |
| P11-07 | Independent reviewer reproduces P11-01..P11-06 from a fresh checkout; implementer does not self-approve. |

## 6. Explicitly out of scope (future stage, not P11)

Marketplace publication/signing, additional IDE products (Community/Android Studio) or build ranges, syntax highlighting beyond what the LSP already reports as diagnostics, code completion popup styling/priority tuning, a custom PSI/grammar, refactoring or navigation actions, debugger integration, and any rendered-UI (non-headless) test.

## 7. Current-state risks and dependencies

- P10's `javelle-lsp` distribution and P09's workspace-model export are dependencies; P11 must consume their public contracts (the stdio protocol and the `build/javelle/workspace/*.json` shape) and must not fork or reimplement either.
- The IntelliJ Platform Gradle Plugin's dependency resolution is heavier and slower than this repo's other modules; first-resolution network cost is expected and should be treated the same way P09's spotless-plugin-resolution gap was (a one-time toolchain cache population, not a per-build network dependency) once `gradle/verification-metadata.xml` is populated.
- `intellij-plugin` is currently an empty `IntellijPluginBoundary.java` scaffold declaring `ALLOWED_DEPENDENCIES = ["language-protocol"]` in the architecture boundary map in root `build.gradle.kts`; P11 must also add `:language-server` (for the bundled distribution) to that allowlist and to `expectedProductionEdges`, and the architecture checker must still pass.
