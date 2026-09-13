# P01-W05 — JDK 21 and IntelliJ/JBR executable probe

- Agent: `/root/p00_recon`
- Base revision: `82c264d007fd7114e468f8ef2c855a6d4f6718e2`
- Requirements: P01-01, P01-04, P01-12 repair
- Checked: 2026-09-11 (Asia/Taipei)
- Evidence: `.agent/logs/P01-JDK-IDE/`; downloads and extracted runtimes: `.agent/tmp/P01-JDK-IDE/` (ignored)

## JDK 21 fixed distribution

Pinned Eclipse Temurin `21.0.12.1+1` Linux x64 HotSpot JDK. The fixed vendor artifact is `OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz` from the Adoptium release API/GitHub release. Expected and actual SHA-256 are both:

`ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`

The extracted binaries report Java and javac `21.0.12.1`. License boundary is OpenJDK GPL-2.0-only WITH Classpath-exception-2.0 plus bundled third-party notices; release packaging must retain the distribution notices.

Executable cross-runtime probe:

| Probe | Exit/result |
|---|---|
| JDK 21 `javac --release 21`, run class on system JDK 25 | 0 / `jdk21` |
| JDK 25 `javac --release 21`, run class on Temurin JDK 21 | 0 / `jdk21` |
| JDK 25 flexible-constructor source with `--release 21` | 1, expected diagnostic: feature unsupported in source 21 |
| Same source with JDK 25 `--release 25` | 0 |

This proves the Java 21 language/API/classfile fence and prevents silent acceptance of a Java 25 language feature. See `cross-runtime.txt`.

Primary metadata: https://api.adoptium.net/v3/assets/feature_releases/21/ga and fixed artifact under https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1 .

## IntelliJ IDEA Ultimate and bundled JBR

Pinned IDEA Ultimate `2026.1.4`, product build `IU-261.26222.65`. JetBrains release metadata reports the fixed Linux artifact:

`https://download.jetbrains.com/idea/idea-2026.1.4.tar.gz`

Size is `1,577,586,467` bytes. JetBrains checksum and the downloaded artifact both equal:

`3104d85d9507ff882065e3f8eb9506402b4a8129092d2682662bb6e9c4f063fc`

The extracted `product-info.json` confirms version/build/product code and that the launcher uses `jbr/bin/java`. The bundled runtime was actually executed:

`JBR-25.0.3+9-329.124-jcef`, exit 0.

This corrects the earlier documentation-derived assumption that branch 261 necessarily bundled Java 21. Plugin source bytecode/toolchain must be tested against the actual pinned platform APIs; compiler/server runtime remains separately modeled.

The archive contains and hashes the concrete LSP implementation artifacts:

| Artifact | SHA-256 |
|---|---|
| `intellij.platform.lsp.jar` | `31f9997c16f42c5491d96dc482da9507851844cc906bd79662440df6af2d8705` |
| `intellij.platform.lsp.impl.jar` | `13db5f9fd6e5568fb026c6a0e292730d325f176bef3d419127343e92142eda3d` |
| bundled `eclipse.lsp4j.jar` | `a36c160d23f6beb0b67edd5a45ea7a81c9fac3c1ba11808db74f5edcf831910a` |
| bundled `eclipse.lsp4j.jsonrpc.jar` | `22d43ebeba54c19420f8deae04eb4ac4a6946e8cdc68ba3ff56ecf8fd6d92b78` |

`jar tf` confirms `LspServerManager`, `LspServerDescriptor`, `LspServerSupportProvider`, and related API classes. IU is required: JetBrains documents that this LSP integration is unavailable in IntelliJ IDEA open-source builds and Android Studio. Fixed support matrix for the first implementation is deliberately narrow: minimum=current=`IU 2026.1.4`, `since-build=261`, `until-build=261.*`. Broader IDE claims require separate artifacts and tests.

The extracted JBR `java.base/LICENSE` begins with GPLv2; its checksum is `4b9abebc4338048a7c2dc184e9f800deb349366bdf28eb23c2677a77b4c87726`. Full IDE/JBR third-party notice review remains a release-license task.

Primary metadata and constraints:

- https://data.services.jetbrains.com/products/releases?code=IIU&release.type=release&latest=false
- https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html
- https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html

## Remaining verifier boundary

The fixed platform and LSP API are now artifact-probed, not documentation-only. JetBrains Plugin Verifier cannot meaningfully verify Teyru yet because no IDEA plugin ZIP or plugin descriptor exists in this stage. Running it against a fabricated empty plugin would not prove API compatibility. P11 must run the pinned verifier against the first real plugin ZIP on IU 2026.1.4; until then, plugin binary compatibility is `NOT_VERIFIED`, not a successful P01-04 claim.

No configuration, product source, ledger, or release state was changed.
