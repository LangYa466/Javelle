# P01 source register

Checked 2026-09-11. URLs below are immutable where copied bytes enter the repository.

| Subject | Source | Recorded verification |
|---|---|---|
| GPL-2.0-only text | SPDX license-list-data commit `16f3aa6c3bdd62e50f8b1cf618f32d2a510250ee`, `text/GPL-2.0-only.txt` | SHA-256 `aaf135472f81c5b4a0dca9367e5bb5e9750032b5bebe5442b36e4c0a47430df3` |
| Classpath Exception 2.0 | same SPDX commit, `text/Classpath-exception-2.0.txt` | SHA-256 `f36ecd7d28dc3c49854d3908a3824696972aed34f5ffe426174f35fb03c5f194` |
| OpenJDK 25.0.3 license cross-check | openjdk/jdk25u commit `2fce64f0ecc22355298b9ab9c1ba9477a2f1ec86` | SHA-256 `4b9abebc4338048a7c2dc184e9f800deb349366bdf28eb23c2677a77b4c87726` |
| Gradle 9.6.0 | `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip` and official release checksums | distribution and wrapper hashes in toolchain matrix |
| Lombok 1.18.48 | Maven Central fixed coordinate; source tag commit `017d15c7222c88480c1f65f96a113f4cc0091462` | JAR hash in toolchain matrix; complete feature inventory deferred to P20 |
| Temurin JDK 21 | Adoptium release `jdk-21.0.12.1+1`, fixed Linux x64 archive | SHA-256 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`; cross-runtime probe recorded in P01-JDK-IDE |
| IntelliJ IDEA Ultimate 2026.1.4 | JetBrains release metadata and fixed Linux archive | SHA-256 `3104d85d9507ff882065e3f8eb9506402b4a8129092d2682662bb6e9c4f063fc`; bundled JBR/LSP artifact hashes in P01-JDK-IDE |
| LSP 3.18.2 meta-model | vscode-languageserver-node `release/protocol/3.18.2` | SHA-256 `35ebcc0b607eeda105dd092c990182eb338852d82130053d3a27499ff34c89a2`; 95-method inventory |
| Node 22.22.2 | fixed Node release files and `SHASUMS256.txt` | Linux x64 executed hash `88fd1ce767091fd8d4a99fdb2356e98c819f93f3b1f8663853a2dee9b438068a`; other supported artifact hashes in machine-readable toolchain lock |

Normative Java, Gradle, JetBrains, LSP, Node, Astro, and Starlight documentation URLs and the evidence date are retained in P01 reports. Mutable documentation pages are citations, not vendored release inputs. Full semantic Lombok implementation remains P20; Plugin Verifier remains P11 because no real plugin ZIP exists in P01.
