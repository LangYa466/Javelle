# Toolchain matrix

Checked 2026-09-11 (Asia/Taipei). These axes are independent; one version does not imply another.

| Axis | Locked value | Verification state |
|---|---|---|
| Compiler runtime JDK | Red Hat OpenJDK 25.0.3+9, Fedora RPM build `25.0.3.0.9-2.fc44` | Locally executed; RPM header SHA-256 `be0eb1a95dc82a47a990af3dc0255dfda1e009ad0197b13a5fa27387515ef360` |
| Gradle daemon JDK | JDK 25 for current local build; Gradle 9.6.0 | Gradle 9.6.0 official distribution and regenerated wrapper executed locally |
| Javelle target releases | Java 25 non-preview and Java 21 via `--release 21` | JDK 25 `javac` available; native JDK 21 distribution/checksum and cross-runtime probe remain **UNVERIFIED** |
| IDEA client bytecode | Java 21 | Proposed IU 2026.1.4 branch 261/JBR pairing remains **UNVERIFIED** until the fixed IDE artifact, JBR manifest, LSP module, and Plugin Verifier are exercised |

## Fixed tool pins

- Gradle wrapper 9.6.0; distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`; wrapper JAR SHA-256 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.
- Lombok oracle 1.18.48, test/migration use only; JAR SHA-256 `85477a4655ebb2c074a9099cfb749be454449fee564d4282610df1b85f7c508b`.
- Eclipse LSP4J and JSON-RPC 1.0.0 for LSP 3.18 inventory work.
- IntelliJ Platform Gradle Plugin 2.18.1 is pinned, but IDE compatibility is not claimed before artifact and runtime verification.
- Node 22.22.2 and npm 10.9.7; Astro 7.3.2 and Starlight 0.42.0 are website pins for the later website package. No Node project is created in P01.

Unsupported/unverified combinations remain failures in future matrices; they must not be silently skipped.
