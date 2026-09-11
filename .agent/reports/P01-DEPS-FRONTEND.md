# P01-W07 dependency and frontend baseline

TASK / AGENT_ID / BASE_REVISION: `P01-W07` / `/root/p00_repair` / `eb75e78a6772b62271b2b6affd127cc16ee54d42`

STATUS: **IMPLEMENTED — independent P01-12 review pending**

CHANGED_PATHS: `.agent/reports/P01-DEPS-FRONTEND.md`; ignored `.agent/logs/P01-DEPS-FRONTEND/` and `.agent/tmp/P01-DEPS-FRONTEND/`

CONTRACT_CHANGES: none; no build, catalog, website, ledger, or `.idea/vcs.xml` change.

REQUIREMENTS: `P01-07`, `P01-11`, local portion of `P01-12`.

## P01-07 selected minimal dependency baseline

### Product/runtime dependencies

| Boundary | Direct selection | Resolved/transitive policy | License/integrity disposition |
|---|---|---|---|
| Lexer/parser | No external parser runtime; implement the required source-aware lexer and recursive-descent/precedence parser in `compiler-core` | JDK only | Avoids a parser generator/runtime and preserves explicit trivia/span/error-recovery ownership. This is not permission to use regex as a parser. |
| JSON outside LSP | JDK-only until a module proves a JSON requirement | None initially | Do not add a second JSON stack pre-emptively. |
| LSP/JSON-RPC | `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` and `org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0` | Lock every Maven transitive after modules consume these aliases | EPL-2.0; accepted direct JAR SHA-256 values remain those in P01-VERSIONS. LSP4J owns the wire JSON boundary. |
| IntelliJ client | `org.jetbrains.intellij.platform` plugin `2.18.1`; fixed IU/JBR remains P01-04 repair | Platform modules must be explicit, never copied into product artifacts | Plugin Apache-2.0; IDE/JBR and bundled-module license/integrity remain a P01 blocker until resolved. |
| Javelle ordinary output | No runtime dependency by default | Any future helper requires a separately licensed tiny runtime decision | Prevents tool dependencies leaking into user programs. |

### Test/build-only dependencies

| Purpose | Fixed direct selection | Minimality and follow-up |
|---|---|---|
| Unit/parameterized/engine | JUnit BOM/Jupiter/Launcher `6.0.0` | Existing strict graph resolves to JUnit API/engine/params/platform plus `opentest4j 1.3.0`, `apiguardian 1.1.2`, `jspecify 1.0.0`; current checksums are in `gradle/verification-metadata.xml`. |
| Lombok oracle | `org.projectlombok:lombok:1.18.48` | Test/oracle and migration only; never normal Javelle compilation runtime. MIT; fixed JAR SHA recorded in P01-VERSIONS. |
| Java/Kotlin/build formatting | Spotless plugin `com.diffplug.spotless:8.10.2`, with `google-java-format:1.36.0` for Java | Add only in P02 build logic after artifact/transitive checksums are generated and independently reviewed. Spotless and google-java-format are Apache-2.0. Do not use the deprecated `com.diffplug.gradle.spotless` ID. |
| Compiler lint | `javac -Xlint:all -Werror` through shared conventions | JDK-native; no separate lint engine until a demonstrated rule gap exists. |
| SBOM | CycloneDX Gradle plugin `org.cyclonedx.bom:3.4.1` | Build/reporting only. Pin and checksum its full plugin graph before application. Apache-2.0. |
| License report | Repository-owned manifest validator over resolved artifacts/SBOM | The older `dependency-license-report` plugin is not selected; it would add another build graph without replacing manual license review. Unknown, compound, or copyleft licenses fail the release gate pending explicit disposition. |

This selection is the baseline, not a forever-closed list. A later module may add a dependency only with: owner and requirement ID; exact coordinate/version; primary-source maintenance and license check; complete direct/transitive lock; strict artifact metadata/checksums; module-boundary justification; clean-consumer test; and independent review. Dynamic versions, snapshots, duplicate JSON/parser stacks, and dependencies added only for one trivial API are rejected.

## Current JVM graph evidence

The current root build resolves only its JUnit test graph. `./gradlew --no-daemon --dependency-verification strict dependencies` exited 0. `compiler-core` and LSP modules do not exist yet, so LSP4J/Lombok/quality/SBOM graphs are selected coordinates but **not yet resolved graphs**. They must remain unclaimed until P02 creates their real configurations and extends `verification-metadata.xml` and lockfiles.

Evidence: `.agent/logs/P01-DEPS-FRONTEND/gradle-dependencies.txt`.

## P01-11 Node and npm lock

### Runtime artifact lock

- Node: `22.22.2`; npm bundled version: `10.9.7`; package manager choice: npm only.
- The fixed official Linux x64 archive was downloaded from `https://nodejs.org/download/release/v22.22.2/node-v22.22.2-linux-x64.tar.xz` and matched Node's official `SHASUMS256.txt`: `88fd1ce767091fd8d4a99fdb2356e98c819f93f3b1f8663853a2dee9b438068a`.
- CI artifact hashes captured from the same signed release checksum list:
  - Linux arm64 `.tar.xz`: `e9e1930fd321a470e29bb68f30318bf58e3ecb4acb4f1533fb19c58328a091fe`
  - macOS x64 `.tar.xz`: `b6a384bba1a7ec585e5a91a452b63f676b940584ff57b5c9cf0541c8db60023e`
  - macOS arm64 `.tar.xz`: `f8655beb4b86ff6588ed7e02c37f8574b58557bd3e880012814b1a4956fd9d88`
  - Windows x64 `.zip`: `7c93e9d92bf68c07182b471aa187e35ee6cd08ef0f24ab060dfff605fcc1c57c`

The release's Node license file includes its bundled third-party notices; the distribution is not to be relabeled as uniformly MIT without preserving those notices.

### Future `website/` manifest contract

The future `website/package.json` must contain exact values:

```json
{
  "private": true,
  "packageManager": "npm@10.9.7",
  "engines": { "node": "22.22.2", "npm": "10.9.7" },
  "dependencies": {
    "@astrojs/starlight": "0.42.0",
    "astro": "7.3.2"
  }
}
```

Commit npm lockfileVersion 3 `website/package-lock.json`; CI installs with `npm ci --ignore-scripts --audit=false --fund=false` from a clean workspace. Lifecycle scripts remain disabled during dependency installation; any package that genuinely needs one requires explicit review and a separately controlled build step. `npm audit` is a separate network-aware security gate, not hidden inside reproducibility verification.

The isolated fixture produced a 373-entry platform-inclusive lock; Linux x64 installed 281 packages. Every locked package entry had a registry integrity and SPDX-like license value. License counts from lock metadata: 305 MIT, 16 Apache-2.0, 12 MPL-2.0, 10 LGPL-3.0-or-later, 8 ISC, 8 BSD-2-Clause, 3 BlueOak-1.0.0, 3 BSD-3-Clause, 3 `Apache-2.0 AND LGPL-3.0-or-later`, 2 CC0-1.0, and one each Python-2.0, `Apache-2.0 AND LGPL-3.0-or-later AND MIT`, and 0BSD. This is an inventory, **not a completed bundling/legal approval**: the LGPL/MPL and compound-license packages require P51 packaging review against what the static site actually distributes.

Direct registry integrity values:

- `astro@7.3.2`: `sha512-ysTcdpGP61XZpHoMRYC/CK19DQ94qkBvzXMtXEo0gEqPNkmOU/Tv++CtWmzaI4nF2Ck8RXFdiWvdlTWa2oOr9w==`
- `@astrojs/starlight@0.42.0`: `sha512-EVsGnyGJ6rRNwGRZFYmGABo0qTQ55F7OSmfSL0VK+5lsqD+u6GWcB8tFqXETcUvP8kyn90W+QBLSL2aer9jNJQ==`

Full ignored component inventory: `.agent/logs/P01-DEPS-FRONTEND/frontend-components.tsv`; lock fixture: `.agent/tmp/P01-DEPS-FRONTEND/site/package-lock.json`.

## P01-12 executable probe results

| Probe | Exit | Result |
|---|---:|---|
| Official Node Linux x64 archive SHA-256 | 0 | Downloaded bytes match `SHASUMS256.txt` |
| Extracted Node/npm versions | 0 | `v22.22.2` / `10.9.7` |
| `npm install --package-lock-only` isolated | 0 | Exact lock generated |
| Clean `npm ci --ignore-scripts --audit=false --fund=false` | 0 | 281 packages installed |
| Full `npm ls --all --json` | 0 | Resolved tree valid |
| Astro lock integrity changed to fake SHA-512 | 1 | npm failed with `EINTEGRITY`, showing wanted fake hash and actual pinned Astro integrity |
| Existing strict Gradle dependency verification | 0 | Current JUnit graph succeeded |

Raw evidence: `.agent/logs/P01-DEPS-FRONTEND/node-official-sha.txt`, `node-os-matrix-sha.txt`, `npm-positive.txt`, `npm-tampered-integrity.txt`, `dependency-license-summary.txt`, and `gradle-dependencies.txt`.

Only Linux x64 was executed. Linux arm64, macOS x64/arm64, and Windows x64 artifacts are checksum-pinned but **NOT_EXECUTED** locally. This does not block P01-11's version/reproducibility selection because the supported matrix and immutable artifacts are explicit; it remains a P52 CI and P54 clean-user acceptance gate. P01-12 still requires a different reviewer to reproduce at least the Linux x64 positive and tampered-lock negative case. Native JDK 21 and IU/JBR failures from P01-REVIEW remain independent P01 blockers.

## Sources checked 2026-09-11

- Node fixed release files: <https://nodejs.org/download/release/v22.22.2/>
- npm `package-lock.json` and `npm ci` behavior: <https://docs.npmjs.com/cli/v10/commands/npm-ci>
- Astro package metadata: <https://registry.npmjs.org/astro/7.3.2>
- Starlight package metadata: <https://registry.npmjs.org/@astrojs/starlight/0.42.0>
- Spotless plugin: <https://plugins.gradle.org/plugin/com.diffplug.spotless/8.10.2>
- google-java-format release: <https://github.com/google/google-java-format/releases/tag/v1.36.0>
- CycloneDX Gradle plugin: <https://plugins.gradle.org/plugin/org.cyclonedx.bom/3.4.1>

TESTS: 6 positive checks exit 0; one tampered-integrity negative exited 1 as required; other OS executions skipped 4 with explicit downstream gates.

REVIEW: pending independent reviewer; implementer does not approve this package.

RISKS_OR_BLOCKERS: full JVM graphs for not-yet-created modules cannot be generated truthfully; IntelliJ/JBR and native JDK 21 remain P01 blockers; front-end copyleft/compound-license packages require P51 distribution review.

NEXT_DEPENDENCIES: independent P01-12 reproduction, then P01 stage review. P02 extends real module configurations, checksum metadata, and locks under single build ownership.

REPORT_PATH: `.agent/reports/P01-DEPS-FRONTEND.md`
