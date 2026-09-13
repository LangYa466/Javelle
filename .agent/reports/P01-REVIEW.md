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
| `P01-02` | PASS | Compiler runtime JDK, Gradle daemon JDK, Teyru targets, and IDEA bytecode level are recorded as four distinct axes; unverified axes are not presented as working |
| `P01-03` | PASS | Gradle 9.6.0 is fixed against official compatibility/checksum sources; official distribution and wrapper hashes matched; isolated wrapper execution succeeded |
| `P01-04` | **FAIL** | Plugin marker `2.18.1` is pinned and hashed, but IU 2026.1.4/JBR were not resolved. Minimum/current IDEA execution, LSP module availability, unsupported artifact behavior, and Plugin Verifier remain untested |
| `P01-05` | **FAIL** | Lombok 1.18.48 artifact/tag/JAR SHA are pinned, but no actual public API, feature, annotation, or configuration-key export exists. Both VERSIONS and SOURCES explicitly defer the required complete inventory to P20 |
| `P01-06` | **FAIL** | LSP 3.18 and LSP4J/JSON-RPC 1.0.0 artifacts are pinned and hash-verified, but there is no official method inventory mapped to library support and no explicit DTO-gap inventory |
| `P01-07` | **FAIL** | Current manifest covers Gradle/JUnit/Lombok/LSP4J only. Parser choice, JSON boundary, IntelliJ artifacts, website dependency graph/transitives, formatter/lint, SBOM and license tools remain unresolved rather than selected as a complete minimal set |
| `P01-08` | PASS | GPLv2+Classpath, third-party source/binary/bundling, annotations, emitted helpers, runtime, wrapper, docs and release artifacts have explicit review boundaries and blockers; no legal compatibility guarantee is fabricated |
| `P01-09` | PASS | `dev.teyru` is limited to internal package use; `io.langya` and public Maven/Plugin IDs are explicitly not claimed as verified namespaces |
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

---

## Second-round stage review — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P01-REVIEW-R2` / `/root/p00_review` / current `dev`

STATUS: **FAILED — REJECT P01**

| Requirement | Decision | Second-round evidence |
|---|---|---|
| `P01-01` | PASS | Temurin 21.0.12.1+1 archive SHA `ce79869…ee94` independently matched; JDK 21 and JDK 25 each compiled `--release 21` output runnable on the other runtime (all exit 0); Java 25-only flexible-constructor source correctly failed under release 21 (exit 1) and passed under release 25 |
| `P01-02` | PASS | Machine toolchain file and matrix keep compiler runtime, Gradle daemon, target releases, and IDEA bytecode/platform/JBR axes distinct; the observed IU JBR 25 correction is recorded |
| `P01-03` | PASS | Gradle 9.6.0 distribution/JAR hashes match official endpoints; isolated wrapper positive and tampered-hash negative passed in round one; strict verification rerun exit 0 in round two |
| `P01-04` | PASS | IU 2026.1.4 archive SHA `3104d85…63fc` matched; archive `product-info.json` independently reports IU build `261.26222.65`; extracted LSP API JAR contains Manager/Descriptor/SupportProvider classes. Minimum=current=261 and unsupported IC/Android boundary are recorded |
| `P01-05` | PASS | Fixed Lombok 1.18.48 JAR/tag/hash; actual jar-derived public surface has 151 entries and executable verbose config export has 82 unique keys. Machine baseline preserves all entries as NOT_IMPLEMENTED/NOT_VERIFIED |
| `P01-06` | **FAIL** | Official meta-model inventory has 95 methods and LSP4J service APIs were exported, but all 95 machine entries still have `dtoGapStatus: NOT_REVIEWED`. This records an unknown state, not the required confirmation of binding support and identification of DTOs Teyru must supplement |
| `P01-07` | PASS | Parser=JDK-only implementation, JSON=LSP4J boundary, JUnit/Lombok/IntelliJ/format/lint/SBOM/license choices and dependency-admission rules form an explicit minimal set with licenses and future resolved-graph gates |
| `P01-08` | PASS | GPL+Classpath and all source/binary/runtime/helper/annotation/packaging boundaries and review items remain explicit; full release legal review is not falsely claimed |
| `P01-09` | PASS | Internal package root and unverified external namespaces remain separated |
| `P01-10` | PASS | Immutable source commits, fixed artifacts, dates and hashes are in concise source/machine records without copied websites |
| `P01-11` | **FAIL** | Node 22.22.2/npm 10.9.7 artifacts, OS matrix, command, 373-entry generated lock, successful `npm ci`, and EINTEGRITY negative are evidenced. However, the only complete lock and per-component inventory live under ignored `.agent/tmp`/`.agent/logs`; the tracked baseline retains only two direct versions and counts. It does not durably lock **all** frontend dependency names/versions/integrities as required |
| `P01-12` | PASS | This independent reviewer reran cross-JDK compile/runtime and release negative, fixed IU archive/product/LSP API extraction, Lombok config export, LSP method extraction, Gradle strict verification, and clean npm install; results are executable rather than documentation-only |

### Plugin Verifier disposition

Deferring Plugin Verifier to P11 is compatible with the exact P01 text. `P01-04` requires choosing/verifying the Platform Gradle Plugin, supported IDEA build, and available LSP module; those artifact-level facts are now verified. P01 has no real Teyru plugin ZIP, so running the verifier on a fabricated empty plugin would not validate compatibility. No Plugin Verifier success or complete IDEA plugin compatibility is claimed here.

### Reproduced commands and outcomes

- Structured JSON/TOML/XML parse: exit 0; Lombok counts `151/82`, LSP count `95`.
- Cross-runtime Java 21 positive probes: exit 0; release-21 Java 25 feature negative: exit 1 as required.
- Fixed IU archive SHA: matched; selective extraction/product metadata/LSP class checks: exit 0.
- Gradle strict dependency verification with isolated home: exit 0, `BUILD SUCCESSFUL`.
- Cached exact frontend lock `npm ci --ignore-scripts --audit=false --fund=false`: exit 0; prior tampered integrity: exit 1 with `EINTEGRITY`.
- Dynamic `latest`/`SNAPSHOT` scan of lock/config/catalog/source records: no matches, search exit 1.
- `.idea/vcs.xml` was excluded and remains untouched.

### Stage-exit decision

**REJECT.** Repair `P01-06` by completing a per-method LSP4J DTO/support comparison and marking actual gaps/supported mappings with evidence. Repair `P01-11` by persisting the exact complete frontend lock/component set in a tracked P01 artifact (it need not create the full website). Until both are independently verified, the dependency/toolchain stage exit is incomplete.

REVIEW: independent `/root/p00_review`; implementers did not approve their own work.

RISKS_OR_BLOCKERS: only `P01-06` and `P01-11` remain failed in this review. Plugin Verifier is a documented P11 gate, not a P01 blocker.

NEXT_DEPENDENCIES: repair those two IDs and rerun P01 stage review.

---

## Third-round stage review — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P01-REVIEW-R3` / `/root/p00_review` / current `dev`

STATUS: **FAILED — REJECT P01**

### P01-06 — FAIL

Positive evidence is real: the official meta-model extraction and machine inventory each contain exactly 95 unique methods; none are advertised; seven N/A methods have specific product reasons (four notebook lifecycle, two document-color, one debugger-context inline value); all 95 entries now avoid `NOT_REVIEWED` in the top-level status.

The binding conclusion is not yet supported:

- `compatibility/lsp-methods.json` marks all 95 methods `SUPPORTED_LIBRARY`, but the retained annotation/binding extraction `.agent/logs/P01-LOMBOK-LSP/lsp4j-method-binding.tsv` labels many methods `UNBOUND`, including core `textDocument/completion`, `definition`, `diagnostic`, `formatting`, `hover`, `references`, and `rename` methods.
- Per-method `libraryBinding.evidence` is a category-level assertion such as `TextDocumentService + protocol DTOs`, not an observed method annotation/signature or a generated type comparison.
- `P01-LSP-GAPS.md` explicitly says the 3.18.2 meta-model postdates LSP4J's draft 3.18.0 claim and leaves the field/type-level meta-model-to-LSP4J DTO diff as remaining work.

P01-06 requires confirming library protocol support and recording DTO differences that Teyru must supply. A blanket `SUPPORTED_LIBRARY` status cannot replace the acknowledged field/type/signature comparison, especially where the captured service-annotation evidence says `UNBOUND`. Required fix: reconcile every `UNBOUND` method with an actual interface/default/annotation or custom JSON-RPC binding, and complete the field/type DTO diff with explicit `SUPPORTED_LIBRARY` or `CUSTOM_DTO_REQUIRED` evidence.

### P01-11 — PASS

- Tracked `config/frontend/package-lock.json` has one root plus 373 platform-inclusive package entries.
- Tracked `third-party/frontend-components.json` has exactly 373 components; every entry has string `path`, `name`, `version`, `integrity`, `license`, and `source` fields.
- Regeneration through tracked `config/frontend/generate-components.jq` is byte-identical (`cmp` exit 0).
- Independent isolated `npm ci --ignore-scripts --audit=false --fund=false`: exit 0, 281 packages installed.
- Independent fresh tampered-Astro-integrity run: exit 1 with `EINTEGRITY` and the actual pinned digest.
- No `node_modules` exists or is tracked below `config/frontend`; the temporary review tree was removed.

### Regression result

No regression was found in prior PASS requirements `P01-01/02/03/04/05/07/08/09/10/12`: machine artifacts parse; fixed toolchain/artifact hashes and axes remain recorded; Lombok remains 151 public-surface entries and 82 config keys; Gradle strict verification passed in the preceding round; fixed IU/JBR/LSP and cross-JDK probes remain evidenced; dynamic-version scans remain clean. `.idea/vcs.xml` was excluded and untouched.

Final requirement result: PASS `P01-01/02/03/04/05/07/08/09/10/11/12`; **FAIL `P01-06` only**.

REVIEW: independent `/root/p00_review`; **REJECT P01** until the method binding and field/type DTO gap evidence is completed. The P01 stage exit remains closed.

NEXT_DEPENDENCIES: repair only `P01-06`, then rerun focused independent review.

---

## Final focused P01 review — authoritative decision

TASK / AGENT_ID / BASE_REVISION: `P01-REVIEW-FINAL` / `/root/p00_review` / current `dev`

STATUS: **VERIFIED — ACCEPT P01**

### P01-06 — PASS

- Re-ran `.agent/logs/P01-R3/generate_lsp_inventory.py` in an isolated temporary directory using the fixed LSP 3.18.2 meta-model, extracted LSP4J 1.0.0 sources, and a copy of the tracked method inventory as the application/N-A seed: exit 0.
- Regenerated method and DTO files were byte-identical to both tracked compatibility files (`cmp` exit 0/0).
- Method inventory is exactly 95 entries and 95 unique protocol strings; all 95 actual binding symbols resolve to their recorded LSP4J source file/method (zero symbol errors).
- Core samples independently checked: `textDocument/completion`, `definition`, `diagnostic`, and `rename` bind to actual `TextDocumentService` methods with `JsonRequest` registration.
- All 95 remain `supportStatus=NOT_IMPLEMENTED` and `advertised=false`; there is no handler or compatibility claim.
- DTO inventory is exactly 450 unique official types: 309 `SUPPORTED_LIBRARY`, 16 `PROTOCOL_DELTA_WITH_REASON`, 125 conservative `CUSTOM_DTO_REQUIRED`, and zero `NOT_REVIEWED`. Delta/custom entries carry explicit reasons.
- The seven application N/A entries retain bounded reasons: notebook lifecycle (4), document color (2), and debugger-context inline value (1). Their protocol bindings are still inventoried.

The P01 contract requires confirming method binding support and recording DTO differences that require Teyru-owned work. The new source-annotation bindings plus complete field/type classification meet that baseline. Focused serialization/alternate-symbol tests for the 141 non-supported/delta types remain mandatory before those DTOs are implemented or advertised, but do not invalidate the truthful P01 inventory.

### P01-11 and regression checks

- Frontend component regeneration remains byte-identical (`cmp` exit 0); tracked lock and component JSON parse successfully.
- No `node_modules` directory exists or is tracked below `config/frontend` (both searches return no match, exit 1).
- Previous independent round already reproduced isolated `npm ci` exit 0 and tampered-integrity `EINTEGRITY` exit 1.
- LSP methods, DTO gaps, toolchains, frontend lock, and third-party component JSON all parse with exit 0.
- Wrapper JAR and complete license texts still match recorded hashes; no regression was found in `P01-01/02/03/04/05/07/08/09/10/11/12`.
- Plugin Verifier remains truthfully deferred to P11 for the first real plugin ZIP; no verifier or IDEA plugin compatibility success is claimed.
- `.idea/vcs.xml` remained excluded and untouched.

### Final stage decision

PASS: `P01-01/02/03/04/05/06/07/08/09/10/11/12`.

**ACCEPT P01.** The toolchain and dependency pins now have reproducible executable/artifact probes, machine-readable inventories, integrity negatives, and explicit licensing/publication gates. Remaining serialization, real plugin ZIP verification, non-local OS execution, full resolved module graphs, packaging-license review, and compatibility implementation remain assigned to their documented later stages and are not claimed complete here.

REVIEW: independent reviewer `/root/p00_review`; coordinator may update P01 ledgers/checkmarks and unlock P02. This reviewer did not modify total ledgers.

RISKS_OR_BLOCKERS: none for the P01 stage exit. Preserve the later-stage gates above.

NEXT_DEPENDENCIES: coordinator integrates P01 evidence and schedules P02 according to the dependency graph.
