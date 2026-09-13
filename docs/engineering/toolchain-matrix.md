# Toolchain matrix

Checked 2026-09-11 (Asia/Taipei). These axes are independent; one version does not imply another.

| Axis | Locked value | Verification state |
|---|---|---|
| Compiler runtime JDK | Red Hat OpenJDK 25.0.3+9, Fedora RPM build `25.0.3.0.9-2.fc44` | Locally executed; RPM header SHA-256 `be0eb1a95dc82a47a990af3dc0255dfda1e009ad0197b13a5fa27387515ef360` |
| Gradle daemon JDK | JDK 25 for current local build; Gradle 9.6.0 | Gradle 9.6.0 official distribution and regenerated wrapper executed locally |
| Teyru target releases | Java 25 non-preview and Java 21 via `--release 21` | Temurin 21.0.12.1+1 and system JDK 25 compiled and cross-ran Java 21 classes; Java 25-only syntax was rejected under release 21 |
| IDEA client platform | IU 2026.1.4, build `IU-261.26222.65`, branch 261 only | Fixed archive SHA-256 `3104d85d9507ff882065e3f8eb9506402b4a8129092d2682662bb6e9c4f063fc`; product metadata, bundled JBR, and LSP classes were artifact-probed |
| IDEA bundled runtime | `JBR-25.0.3+9-329.124-jcef` | Executed from the fixed IU archive; this corrects the earlier documentation-derived Java 21 assumption |
| IDEA plugin binary compatibility | branch 261, exact first target IU 2026.1.4 | **NOT_VERIFIED** until P11 produces a real plugin ZIP and runs Plugin Verifier; no empty/fabricated plugin is accepted as evidence |

## Fixed tool pins

- Gradle wrapper 9.6.0; distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`; wrapper JAR SHA-256 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.
- Eclipse Temurin 21.0.12.1+1 Linux x64 archive SHA-256 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`.
- Lombok oracle 1.18.48, test/migration use only; JAR SHA-256 `85477a4655ebb2c074a9099cfb749be454449fee564d4282610df1b85f7c508b`.
- Eclipse LSP4J and JSON-RPC 1.0.0 for LSP 3.18 inventory work.
- IntelliJ Platform Gradle Plugin 2.18.1 is pinned. IU/JBR/LSP artifact presence is verified; plugin compatibility remains deferred to P11's first real plugin ZIP.
- Node 22.22.2 and npm 10.9.7; Astro 7.3.2 and Starlight 0.42.0 are website pins for the later website package. No Node project is created in P01.

Supported first implementation matrix is intentionally narrow: IU 2026.1.4 only (`since-build=261`, `until-build=261.*`). IntelliJ open-source builds and Android Studio do not expose JetBrains' LSP integration and are unsupported. Other OS Node artifacts are checksum-pinned but not locally executed. Unsupported/unverified combinations must not be silently skipped.
