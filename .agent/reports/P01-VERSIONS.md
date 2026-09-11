# P01-W01 — Official versions and toolchain research

- Requirement IDs: P01-01, P01-02, P01-03, P01-04, P01-05, P01-06, P01-07, P01-10, P01-11
- Agent: `/root/p00_recon`
- Base revision: `82c264d007fd7114e468f8ef2c855a6d4f6718e2`
- Checked: 2026-09-11 (Asia/Taipei)
- Status: `IMPLEMENTED`, pending independent reproduction and license review
- Raw evidence: `.agent/logs/P01-VERSIONS/probes.txt`

## Proposed immutable pins

| Concern | Pin | Reason and compatibility boundary | License / verification |
|---|---:|---|---|
| Compiler/runtime JDK | Red Hat OpenJDK `25.0.3+9`, RPM `java-25-openjdk-headless-1:25.0.3.0.9-2.fc44.x86_64` | Installed and probed. Java language target remains Java SE 25 non-preview. Keep compiler runtime separate from emitted target. | GPL-2.0-only WITH Classpath-exception-2.0. RPM SHA-256 header `be0eb1a95dc82a47a990af3dc0255dfda1e009ad0197b13a5fa27387515ef360`; `sha256sum` executable evidence in log. |
| Java 21 profile | `--release 21` under JDK 25 initially | `javac --release` is the correct API/language/bytecode fence. A native JDK 21 runtime matrix entry remains required; no JDK 21 binaries are installed despite an empty `/usr/lib/jvm/java-21-openjdk` directory. | Do not mark P01-01/P01-12 verified until a fixed JDK 21 distribution and checksum are selected and probed. |
| Gradle Wrapper | `9.6.0` | Existing fixed wrapper; official matrix supports running/toolchains on Java 25 from Gradle 9.1 onward. Avoid unnecessary movement to the documentation's current 9.7.x line. | Apache-2.0. Official distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. Verify: `sha256sum gradle-9.6.0-bin.zip`. |
| IntelliJ Platform Gradle Plugin | `org.jetbrains.intellij.platform:2.18.1` | Official SDK example and current stable 2.x line; requires Gradle 9.0+ and Java 17+, satisfied by the proposed build runtime. | Apache-2.0. Marker POM SHA-256 `1cca2c5f27bffb992fa3b7a9f6e33e01993ed28a13b0a0ad67239f018afc819e`. |
| IntelliJ IDEA target | IntelliJ IDEA Ultimate `2026.1.4`, branch `261`, `sinceBuild=261`, `untilBuild=261.*` | Official LSP page uses 2026.1.4. LSP is a commercial-platform module, unavailable in IntelliJ IDEA open-source builds and Android Studio. 2026.1 adds range formatting, code lens, organize imports, and highlighting improvements. | JetBrains proprietary IDE distribution; only compile/test dependency. Plugin bytecode/JBR language level `21`, per official branch table. Installer/JBR checksum still requires artifact-resolution evidence. |
| JetBrains Runtime | JBR bundled with pinned IU `2026.1.4` | Do not independently float JBR; use the IDE distribution's bundled Java 21 runtime so client classes do not couple to compiler JDK 25. | JetBrains Runtime licensing/notices and installer checksum must be captured after the fixed IU artifact is resolved; release gate remains open. |
| LSP protocol | Microsoft LSP `3.18` | Required method inventory and capability negotiation baseline. There is no version handshake. | CC-BY-3.0 for specification repository content; do not copy the whole specification into the product. |
| Java LSP binding | Eclipse LSP4J `1.0.0` plus `org.eclipse.lsp4j.jsonrpc:1.0.0` | First stable LSP4J line documented as implementing LSP 3.18.0; 0.24.x only implements LSP 3.17. | EPL-2.0. JAR SHA-256: `ccd78893facc6bfcc359d56cba05d3d5b85eb41e4c40d4b4215ca45db5f416d9`; JSON-RPC `9647feb0524bf763c878e12ab878a684102b81cccb3f77feecbec709d54f9bbb`; tag `cee863f6b20f7f60995f7ab8fbebb9ac531d9d00`. |
| Lombok oracle | `org.projectlombok:lombok:1.18.48` | Fixed formal release dated 2026-09-01. Its public feature/config/API surface becomes `lombokBaseline`; use only as differential oracle and controlled migration dependency. | MIT. Maven JAR SHA-256 `85477a4655ebb2c074a9099cfb749be454449fee564d4282610df1b85f7c508b`; tag `017d15c7222c88480c1f65f96a113f4cc0091462`. Maven Central has no `.sha256` sidecar for this artifact, so verify the downloaded fixed URL directly. |
| Node.js | `22.22.2` | Installed Node 22 LTS; Astro 7.3.2 requires `>=22.12.0`. Do not use `node:lts` or another floating selector. | Node.js license is MIT with bundled third-party notices. Distribution checksum must be added when CI images are selected. |
| Package manager | npm `10.9.7` | Installed, fixed, and sufficient; avoids introducing absent pnpm solely for preference. Set `packageManager: "npm@10.9.7"` and commit `package-lock.json`. | npm CLI is Artistic-2.0; dependency tree needs generated license/SBOM review. |
| Astro / Starlight | `astro@7.3.2`, `@astrojs/starlight@0.42.0` | Fixed registry releases; compatible with Node pin. Never use `@latest` in checked-in commands or manifests. | MIT/MIT. Registry SRI: Astro `sha512-ysTcdpGP61XZpHoMRYC/CK19DQ94qkBvzXMtXEo0gEqPNkmOU/Tv++CtWmzaI4nF2Ck8RXFdiWvdlTWa2oOr9w==`; Starlight `sha512-EVsGnyGJ6rRNwGRZFYmGABo0qTQ55F7OSmfSL0VK+5lsqD+u6GWcB8tFqXETcUvP8kyn90W+QBLSL2aer9jNJQ==`. Tags `852793f4117d6db26f41bd3a5e9283abcf963d54` and `88ad3c2630487ba227a7b4ccbffc01a2bdf623a5`. |

## Primary sources

- Oracle Java SE 25 JLS: https://docs.oracle.com/javase/specs/jls/se25/html/index.html
- Oracle JDK 25 API overview: https://docs.oracle.com/en/java/javase/25/docs/api/overview-summary.html
- Gradle Java compatibility: https://docs.gradle.org/current/userguide/compatibility.html
- Gradle integrity guidance: https://gradle.org/release-checksums/
- JetBrains platform plugin 2.x: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- JetBrains LSP integration: https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html
- JetBrains build/JBR levels: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
- LSP 3.18: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/
- Eclipse LSP4J releases/support: https://github.com/eclipse-lsp4j/lsp4j/releases/tag/v1.0.0 and https://github.com/eclipse-lsp4j/lsp4j
- Lombok release/download: https://projectlombok.org/changelog and https://projectlombok.org/download
- Lombok license: https://github.com/projectlombok/lombok/blob/master/LICENSE
- Node release policy: https://nodejs.org/en/about/previous-releases
- Starlight setup/releases: https://starlight.astro.build/getting-started/ and https://github.com/withastro/starlight/releases
- Fixed artifact repositories: https://repo.maven.apache.org/maven2/ and https://registry.npmjs.org/

## Reproduction commands

```bash
curl -fsSL https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256
curl -fsSLO https://repo.maven.apache.org/maven2/org/projectlombok/lombok/1.18.48/lombok-1.18.48.jar
sha256sum lombok-1.18.48.jar
curl -fsSLO https://repo.maven.apache.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/1.0.0/org.eclipse.lsp4j-1.0.0.jar
curl -fsSLO https://repo.maven.apache.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/1.0.0/org.eclipse.lsp4j.jsonrpc-1.0.0.jar
sha256sum org.eclipse.lsp4j*.jar
curl -fsSL https://registry.npmjs.org/astro | jq -r '.versions["7.3.2"].dist.integrity'
curl -fsSL https://registry.npmjs.org/%40astrojs%2fstarlight | jq -r '.versions["0.42.0"].dist.integrity'
```

## Unresolved gates

1. Select and install a fixed native JDK 21 distribution, record its vendor archive checksum, and run the profile on both JDK 21 and JDK 25 `--release 21`.
2. Resolve the fixed IU 2026.1.4 installer in P11, capture installer checksum and bundled JBR build/license manifest, then run Plugin Verifier for branch 261.
3. Export Lombok 1.18.48 classes, annotations, features, configuration keys, and license notices in P20; this report pins the oracle but does not claim compatibility.
4. Generate and independently review Gradle/Maven/npm lockfiles, dependency verification metadata, complete licenses, and SBOM before P01/P51 acceptance.

No public namespace ownership, artifact publication, or legal conclusion is claimed here.
