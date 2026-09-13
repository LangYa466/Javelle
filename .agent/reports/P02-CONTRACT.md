# P02-W01 — Monorepo / architecture contract freeze

- Agent `/root/p00_spec`; base `29321d8479e92ac05b1f1ec963636464b24faee8`
- P01 is ledger-accepted; P02 is `READY`. This document is an implementation contract, not completion evidence.
- Existing unowned `?? .idea/vcs.xml` is excluded: build owner must not read, edit, stage, delete, ignore, or commit it.

## P02 checklist contract

| ID | Required implementation | Minimum acceptance/evidence |
|---|---|---|
| P02-01 | Create only the named, compilable Gradle projects below and enforce the dependency DAG. Existing root `src/` must be inventoried/moved by its owner, not overwritten. | `./gradlew projects`; compile every included JVM project; graph check; no empty module used as a feature claim. |
| P02-02 | Wrapper 9.6.0 with recorded distribution/JAR hashes; version catalog; included `build-logic`; common Java conventions, Spotless/google-java-format, reproducible archives. | strict dependency verification; formatting positive/negative; two archives byte-identical after normalized timestamps/order/modes. |
| P02-03 | `.editorconfig`, `.gitattributes`, `.gitignore`: UTF-8/LF defaults, preserve executable `gradlew`, Windows launcher and explicit CRLF fixtures. | line-ending script; `gradlew.bat`/CRLF fixture survives; ignored logs/tmp/build/node_modules. |
| P02-04 | Maintain atomic `.agent` state files plus tracked templates/reports and ignored logs/tmp. | JSON/Markdown parse; temp+rename atomic updater test; logs/tmp absent from Git index. |
| P02-05 | Task/ownership schema validation: unique IDs, allowed states, existing dependencies, acyclic/topological DAG, owner/evidence/acceptance fields, non-overlapping active locks. | positive manifests plus duplicate ID, missing dependency, cycle, missing owner/evidence, overlapping writer negative fixtures. |
| P02-06 | Architecture test reads actual Gradle project dependency graph and forbidden bytecode/import/package edges. | negatives for core→Gradle, core→IDE, core→LSP, server→IDE, workspace-model→platform, and any cycle. |
| P02-07 | `testkit` provides source fixtures, expected diagnostics, external process runner, Java consumer, JAR/reflection/archive inspectors. | one intentionally broken fixture fails; expected/actual do not share normalizer; subprocess exit/stdout/stderr/timeouts verified. |
| P02-08 | Implement truthful lifecycle tasks below. Missing suites create explicit failing/incomplete gates, never an empty green aggregate. | early commands produce documented PASS only for implemented P02 gates and `INCOMPLETE` nonzero for future gates. |
| P02-09 | Project-local agent role templates only after schema/version validation; no model pin, rate increase, sandbox/approval relaxation. | config parse plus review that global config untouched. |
| P02-10 | Import every A/B/C requirement, `Pxx-nn`, and `UAT-nn` into manifest with initial NOT_IMPLEMENTED/NOT_VERIFIED. | uniqueness/completeness diff against normative plan; no bulk ACCEPTED/checkmarks. |
| P02-11 | Document shared-worktree ownership and optional isolated-worktree integration; shared agents never branch/reset/stash/commit. | simulated two-writer collision on root build file is rejected; stale lock audit; integration-only commit rule. |
| P02-12 | Minimal CI runs compile, format/lint, architecture, manifest/schema tests with pinned actions/toolchains. | workflow syntax/static validation; local equivalence command; status says CI_NOT_RUN until an actual run URL/evidence exists. |

## Exact Gradle project layout

Use these stable project paths/directories; non-code content (`spec`, `docs`, `website`, `examples`, `compatibility`, `.agent`) is not made into fake Java projects.

| Gradle path | Directory | Kind |
|---|---|---|
| `:compiler-core` | `compiler-core/` | Java library |
| `:workspace-model` | `workspace-model/` | Java library |
| `:java-resolver` | `java-resolver/` | Java library |
| `:compiler-driver` | `compiler-driver/` | Java library/application boundary |
| `:compiler-cli` | `compiler-cli/` | application/distribution |
| `:language-tooling` | `language-tooling/` | Java library |
| `:language-protocol` | `language-protocol/` | Java DTO library |
| `:language-server` | `language-server/` | application/distribution |
| `:gradle-plugin` | `gradle-plugin/` | `java-gradle-plugin` |
| `:intellij-plugin` | `intellij-plugin/` | IntelliJ Platform plugin |
| `:migration` | `migration/` | Java library/CLI component |
| `:testkit` | `testkit/` | test fixtures/library |
| `:compatibility-tests` | `compatibility-tests/` | black-box test suite |
| `:integration-tests` | `integration-tests/` | end-to-end test suite |
| `:release-tools` | `release-tools/` | internal release verification tools |

`runtime/` and `compat-annotations/` are reserved but must not be included as publishable Gradle projects until a feature requires them and an ADR freezes ABI, dependency and license. `build-logic/` is an included build (`includeBuild("build-logic")`), not a normal product subproject. Do not create all directories with placeholder classes solely to make `projects` green.

## Frozen dependency DAG

```text
compiler-core            (no product-project dependencies)
workspace-model          (no product-project dependencies)
language-protocol        (no semantic implementation; DTO-only)

java-resolver        -> compiler-core, workspace-model
compiler-driver      -> compiler-core, java-resolver, workspace-model
compiler-cli         -> compiler-driver
language-tooling     -> compiler-driver, workspace-model
language-server      -> language-tooling, language-protocol, workspace-model, LSP transport
gradle-plugin        -> workspace-model + isolated compiler process contract
intellij-plugin      -> language-protocol + IDEA/LSP client adapters
migration            -> compiler-core, compiler-driver

testkit              -> public test-facing contracts only
compatibility-tests  -> testkit; launches distributions/oracles externally
integration-tests    -> testkit; consumes CLI/server/plugin artifacts externally
release-tools        -> archive/SBOM/license schemas; no product runtime dependency
```

Forbidden direct/transitive edges: `compiler-core` to workspace-model/Gradle/IntelliJ/LSP; `workspace-model` to Gradle/IntelliJ/LSP; `language-server` to IntelliJ; `language-protocol` to semantic/compiler implementation; `intellij-plugin` to compiler internals; `gradle-plugin` loading compiler internals in daemon classloader; any project cycle. Test dependencies do not waive production architecture rules.

## Java, toolchain and test conventions

- Build/compile runtime pin: Gradle 9.6.0; Java toolchain 25 for compiler-oriented modules where Java 25 APIs are required. Published general library bytecode and client levels must follow the P01 four-axis matrix; do not infer one `jvmToolchain` fits compiler runtime, target release, Gradle daemon and IDEA JBR.
- Every Java compile task declares UTF-8 and explicit `--release`; default reusable JVM libraries should use release 21 unless a module-specific P01/ADR contract requires 25. `intellij-plugin` follows sinceBuild 261/JBR compatibility rather than inheriting compiler target. Tests needing 21 and 25 are separate suites/toolchains.
- JUnit 6.0.0 via catalog/BOM; `useJUnitPlatform`; deterministic locale/timezone/encoding where assertions depend on them. Unit tests live with module; architecture/schema tests in build-logic or dedicated test suite; process/consumer tests in testkit/integration modules.
- Lombok 1.18.48 is test oracle only and must never enter production implementation/runtime configurations. LSP4J belongs at server/protocol boundary, never compiler-core/workspace-model.
- Repositories only from dependency policy (currently Maven Central and verified plugin sources); strict verification metadata; no dynamic versions, configuration-time subprocess or `afterEvaluate` assembly.

## Truthful aggregate task semantics

| Task | Early P02 behavior | Final intended behavior |
|---|---|---|
| `verifyQuick` | Depends on actual compile, format/lint check, architecture tests, state/task/ownership/requirements schema checks. It may succeed only for these declared P02 checks; output/report explicitly says later suites are outside this invocation, not verified. | Fast relevant unit/spec/architecture regression set. |
| `verifyAll` | Depends on `verifyQuick` plus a `verifyAllReadiness` gate that enumerates required future families and fails nonzero with machine-readable `INCOMPLETE` while they do not exist/have evidence. | Compiler/Java/Lombok/Gradle TestKit/LSP/IDEA/site/docs/security suites, with real skip accounting. |
| `releaseCheck` | Depends on `verifyAll` plus RC archive/license/SBOM/clean-consumer/release-metadata readiness; therefore must fail nonzero in P02. | Complete distributable/install/license/checksum/SBOM/release metadata validation. |

Never use `onlyIf { false }`, empty lifecycle tasks, swallowed failures, or default-success placeholders. Readiness data must list `gateId`, `status`, `reason`, expected task/evidence and be checked independently from current P55 acceptance to avoid circularity.

## `.agent` schemas and atomic ownership

- `TASKS.json`: `schemaVersion`, phase, tasks array; each task has unique ID, requirement IDs, allowed status (`TODO/READY/RUNNING/IMPLEMENTED/VERIFIED/ACCEPTED/BLOCKED/FAILED/REOPENED`), existing/acylcic dependencies, owner/agent/reviewer IDs, base revision, owned/read-only paths, acceptance, evidence, blockers.
- `OWNERSHIP.json`: schemaVersion, limits, agents/locks. Active lock has exact normalized repository-relative path or bounded subtree, agent ID, mode, task ID, acquired timestamp/base revision; reject path traversal, absolute paths, broad repo root and overlapping write locks. Completed/absent agents release locks atomically.
- Atomic update: validate old revision/fingerprint and active owner; write same-filesystem temp; fsync file; atomic rename; fsync parent. On validation/write failure retain prior valid file. Only coordinator/authorized integration owner updates ledgers.
- `STATE.md` and `NEXT_SESSION.md` are concise derived recovery views, not authority over Git/tests. NEXT_SESSION includes revision, accepted/in-progress tasks, live locks, blockers, next READY, reproduce commands, evidence, preserved user changes and publication status.
- Logs/tmp are ignored; reports/templates and concise manifests may be tracked. No secrets/private conversation/absolute local credentials.

## Architecture negative fixtures

1. `compiler-core` imports `org.gradle.api.Project`, IntelliJ PSI, or LSP4J → fail with exact edge/class.
2. `workspace-model` exposes Gradle/PSI/LSP type in public signature → fail.
3. `language-server` depends on `intellij-plugin`, or IDEA plugin depends on compiler internals → fail.
4. Add `compiler-driver -> language-tooling` to create cycle → fail with cycle path.
5. Gradle plugin puts compiler implementation on plugin runtime/daemon classpath → dependency-graph test fails.
6. Duplicate task ID, nonexistent dependency, cycle, missing evidence on VERIFIED/ACCEPTED, or overlapping ownership → manifest validator fails.

## Clean-checkout acceptance

In a fresh temporary clone of the accepted revision, with no copied `.gradle`/`build`/IDE state:

```bash
./gradlew --no-daemon --write-verification-metadata sha256 help   # build-owner setup only; reviewed diff required
./gradlew --no-daemon --dependency-verification=strict projects
./gradlew --no-daemon --dependency-verification=strict verifyQuick
./gradlew --no-daemon --dependency-verification=strict verifyAll     # expected nonzero INCOMPLETE during P02
./gradlew --no-daemon --dependency-verification=strict releaseCheck  # expected nonzero INCOMPLETE during P02
git status --short
```

Acceptance requires: project/DAG exactly matches contract; compilation/quick gates exit 0; both incomplete aggregates fail for named missing families; negative fixtures fail for the intended reason; second clean archive build is reproducible; repository remains clean except explicitly generated ignored outputs. CI status remains separate until actual remote execution evidence exists.

## Current-state cautions for build owner

- Current root is still single-project (`settings.gradle.kts` only names `Teyru`; root applies `java`). This is baseline material, not P02-01 completion.
- Current `publicationStatus=PUBLISHED` reflects user-authorized P00 Git publication, but P02 must not infer authorization for future release artifacts.
- Preserve P01 pins: Gradle 9.6.0, JUnit 6.0.0, Spotless 8.10.2/google-java-format 1.36.0, toolchain matrix and strict verification metadata. Changes require new evidence/review.
- Build owner must capture pre-change `git status`; the sole observed untracked `.idea/vcs.xml` is user-owned and out of scope.
