# P01 independent review

TASK / AGENT_ID / BASE_REVISION: `P01-REVIEW` / `/root/p00_review` / `236632d27dc89ca7e092423b78267c3bb6ba3284`

STATUS: **FAILED — REJECT P01**

CHANGED_PATHS: `.agent/reports/P01-REVIEW.md`; ignored `.agent/logs/P01-REVIEW/verification-summary.txt`

CONTRACT_CHANGES: none

The unowned `.idea/vcs.xml` was explicitly excluded and not read, modified, staged, or removed.

## Requirement decisions

| Requirement | Decision | Independent basis |
|---|---|---|
| `P01-01` | **FAIL** | JDK 25 pin/checksum and `--release 21` approach are recorded, but the report itself requires a fixed native JDK 21 distribution/checksum and cross-runtime probe. `/usr/lib/jvm/java-21-openjdk/bin/java` is not executable (probe exit 1) |
| `P01-02` | PASS | Compiler runtime JDK, Gradle daemon JDK, Javelle targets, and IDEA bytecode level are recorded as four distinct axes; unverified axes are not presented as working |
| `P01-03` | PASS | Gradle 9.6.0 is fixed against official compatibility/checksum sources; official distribution and wrapper hashes matched; isolated wrapper execution succeeded |
| `P01-04` | **FAIL** | Plugin marker `2.18.1` is pinned and hashed, but IU 2026.1.4/JBR were not resolved. Minimum/current IDEA execution, LSP module availability, unsupported artifact behavior, and Plugin Verifier remain untested |
| `P01-05` | **FAIL** | Lombok 1.18.48 artifact/tag/JAR SHA are pinned, but no actual public API, feature, annotation, or configuration-key export exists. Both VERSIONS and SOURCES explicitly defer the required complete inventory to P20 |
| `P01-06` | **FAIL** | LSP 3.18 and LSP4J/JSON-RPC 1.0.0 artifacts are pinned and hash-verified, but there is no official method inventory mapped to library support and no explicit DTO-gap inventory |
| `P01-07` | **FAIL** | Current manifest covers Gradle/JUnit/Lombok/LSP4J only. Parser choice, JSON boundary, IntelliJ artifacts, website dependency graph/transitives, formatter/lint, SBOM and license tools remain unresolved rather than selected as a complete minimal set |
| `P01-08` | PASS | GPLv2+Classpath, third-party source/binary/bundling, annotations, emitted helpers, runtime, wrapper, docs and release artifacts have explicit review boundaries and blockers; no legal compatibility guarantee is fabricated |
| `P01-09` | PASS | `org.javelle` is limited to internal package use; `io.langya` and public Maven/Plugin IDs are explicitly not claimed as verified namespaces |
| `P01-10` | PASS | Concise SOURCES register records immutable commits/hashes for copied license bytes and fixed artifact references; mutable documentation is citation-only and no website content was vendored |
| `P01-11` | **FAIL** | Node/npm and Astro/Starlight top-level pins are proposed, but there is no Node distribution checksum/CI image pin, frontend manifest/lockfile, complete transitive dependency lock, reproducible install command, or explicit supported OS matrix |
| `P01-12` | **FAIL** | Independent Gradle wrapper/verification probes pass, but the required minimum toolchain/API probe cannot pass without native JDK 21 and the selected IU/JBR/LSP module. Documentation-only compatibility is explicitly forbidden |

## Reproduced positive and negative checks

| Check | Exit | Result |
|---|---:|---|
| Official Gradle 9.6.0 distribution hash | 0 | `bbaeb2f…a01`, matches wrapper properties |
| Official Gradle 9.6.0 wrapper JAR hash | 0 | `497c8c2…a9c7`, matches checked-in JAR |
| Isolated fresh `./gradlew --no-daemon --version` | 0 | Gradle 9.6.0 downloaded/verified and executed |
| Isolated strict dependency verification | 0 | `BUILD SUCCESSFUL`; current declared JUnit graph verified |
| Isolated tampered distribution hash | 1 | Correctly rejected; expected injected hash differed from actual official ZIP hash |
| TOML / JSON parse | 0 / 0 | Version catalog and dependency manifest are syntactically valid |
| License hashes | 0 | GPL and Classpath Exception files match recorded hashes |
| Lombok/LSP4J/plugin marker artifact hashes | 0 | All match recorded P01 values |
| Native JDK 21 executable | 1 | Missing |
| IU/JBR fixed artifact search | no result | Not installed/resolved; no checksum or runtime/module evidence |

Evidence: `.agent/logs/P01-REVIEW/verification-summary.txt`.

## Stage-exit decision

**REJECT.** Wrapper integrity and the currently declared JVM dependency verification are real and reproducible, but the stage contract requires the whole selected toolchain/dependency set to have executable probes and fixed license/version records. Native JDK 21 directly blocks `P01-01` and the cross-runtime portion of `P01-12`. Missing IU 2026.1.4/JBR/LSP module evidence directly blocks `P01-04` and the IDE/API portion of `P01-12`. Lombok inventory, LSP method/DTO mapping, complete dependency selection, and frontend reproducibility independently block `P01-05/06/07/11`.

The missing JDK/IDE evidence cannot be deferred merely because later phases perform fuller compatibility tests: P01 explicitly requires choosing and verifying these versions before its stage exit. P02 remains locked.

REVIEW: independent reviewer `/root/p00_review`; implementers did not approve their own work.

RISKS_OR_BLOCKERS: exact failures above. `.idea/vcs.xml` remains an unrelated user file and must be preserved.

NEXT_DEPENDENCIES: repair `P01-01/04/05/06/07/11/12`, then repeat this independent review.

REPORT_PATH: `.agent/reports/P01-REVIEW.md`
