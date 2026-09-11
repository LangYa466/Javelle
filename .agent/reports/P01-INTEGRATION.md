# P01-W04 — version and license lock integration

TASK / AGENT_ID / BASE_REVISION: `P01-W04` / `/root/p00_git_publish` / `eb75e78a6772b62271b2b6affd127cc16ee54d42`

STATUS: **VERIFIED BASELINE — P01 stage rejected**

CHANGED_PATHS: `gradle/wrapper/**`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `docs/engineering/**`, `third-party/dependencies.json`, `LICENSE`, `LICENSE-CLASSPATH-EXCEPTION-2.0`, `THIRD-PARTY-NOTICES.md`, and coordinator ledgers. P02 monorepo/build-logic work was not started.

CONTRACT_CHANGES: exact P01 pins and licensing baseline only; no product API or publication claim.

## Implemented locks

- Retained Gradle 9.6.0 and added official distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`.
- Did not execute the pre-existing mismatched wrapper JAR (`91a239...`). Downloaded the fixed official distribution, verified its SHA, then used that distribution to generate the wrapper JAR. The replacement matches Gradle's official wrapper checksum `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.
- Added exact JVM catalog pins and strict SHA-256 dependency verification metadata generated from the actual root JUnit graph. Catalog adoption by module build files belongs to P02.
- Added the four-axis toolchain matrix, immutable source register, structured dependency/license manifest, and preliminary third-party notices.
- Added complete GPL-2.0-only and Classpath Exception 2.0 texts from SPDX license-list-data commit `16f3aa6c3bdd62e50f8b1cf618f32d2a510250ee`.

## Lightweight verification

| Command/check | Exit | Result |
|---|---:|---|
| official Gradle 9.6.0 distribution SHA check | 0 | matched `bbaeb2...a01` |
| official distribution `wrapper` generation + JAR SHA check | 0 | matched `497c8c...a9c7` |
| replacement `./gradlew --no-daemon --version` with isolated Gradle home | 0 | reported Gradle 9.6.0; wrapper distribution checksum enforced |
| `./gradlew --dependency-verification strict dependencies` | 0 | `BUILD SUCCESSFUL`; catalog and verification metadata parsed, declared JUnit graph verified |
| `python3 -m json.tool third-party/dependencies.json` | 0 | valid JSON |
| license hashes | 0 | GPL `aaf135...df3`; exception `f36ecd...194` |

Raw bounded output: ignored `.agent/logs/P01-INTEGRATION/`.

## Explicitly unverified

- No native JDK 21 distribution is installed/pinned with an archive checksum; cross-runtime Java 21 verification remains open.
- IU 2026.1.4 and bundled JBR were not downloaded. Installer checksum, bundled JBR build/license manifest, LSP module availability, Plugin Verifier, and real IDE smoke evidence remain open.
- Full resolved runtime/IDE/Node graph, lockfiles, archive SBOM, compatibility behavior, and public namespace ownership remain later gates. P01 locks do not claim Lombok feature compatibility or IDEA compatibility.

REVIEW: `/root/p00_review` independently reproduced the Gradle distribution/wrapper hashes, isolated wrapper execution, strict dependency verification, tampered-hash rejection, JSON/TOML parsing, license hashes, and pinned artifact hashes. The reviewer rejected the P01 stage for requirements `P01-01/04/05/06/07/11/12`; see `.agent/reports/P01-REVIEW.md`.

RISKS_OR_BLOCKERS: P01 cannot be accepted while the seven failed IDs in the independent review remain unresolved. No large IDE download was attempted in this package.

NEXT_DEPENDENCIES: complete the three READY repair packages recorded in `.agent/TASKS.json`, then rerun independent P01 review. P02 remains locked.

REPORT_PATH: `.agent/reports/P01-INTEGRATION.md`

---

## P01-W08 repair integration

STATUS: **VERIFIED — accepted by independent P01 stage review**

Integrated the verified W05/W06/W07 pins without creating P02 modules or a website:

- machine-readable JDK 21/JDK 25/Gradle/IU/JBR/Node locks in `config/toolchains.json`;
- machine-readable frontend reproducibility and license-review boundary in `config/frontend-baseline.json`;
- all 151 Lombok public-surface class entries and 82 config keys in `compatibility/lombok-baseline.json`, each explicitly `NOT_IMPLEMENTED` and `NOT_VERIFIED` for Javelle compatibility;
- all 95 LSP 3.18 methods in `compatibility/lsp-methods.json`, each non-advertised with per-method DTO/support review still required;
- corrected toolchain matrix: IU 2026.1.4 bundles executed `JBR-25.0.3+9-329.124-jcef`, not Java 21;
- fixed Temurin 21.0.12.1+1, IU/JBR/LSP, Node/npm, Astro/Starlight, Spotless, google-java-format, and CycloneDX pins recorded in catalogs/manifests.

Plugin Verifier is `DEFERRED_P11_REAL_PLUGIN_ZIP`: P01 has no real plugin ZIP, so an empty fabricated plugin would not prove compatibility. No verifier success is claimed.

TESTS: JSON/TOML/XML and inventory-count validation, strict current Gradle graph verification, fixed hash consistency, and dynamic-version scan; bounded output belongs under ignored `.agent/logs/P01-INTEGRATION/`.

REVIEW: `/root/p00_review` regenerated the 95-method and 450-type inventories byte-identically, resolved all 95 binding symbols, verified zero unreviewed DTO statuses, regenerated the 373-component frontend inventory byte-identically, and accepted P01. See the final focused review in `.agent/reports/P01-REVIEW.md`.
