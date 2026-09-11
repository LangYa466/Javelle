# P02-R02 — Architecture boundary enforcement repair

- Requirement: P02-06
- Agent: `/root/p00_recon`
- Base revision: `82c264d007fd7114e468f8ef2c855a6d4f6718e2`
- Status: `IMPLEMENTED`, pending build-owner wiring and independent review
- Evidence: `.agent/logs/P02-ARCHITECTURE/manual-tests.txt`

## Implementation

`ArchitectureBoundaryChecker` independently scans each governed module's production Java sources and compiled main classes. Source inspection removes comments, character literals, and string literals before detecting imports, fully-qualified references, field/method signatures, and other FQNs. It therefore catches references that the root task's line-prefix import test misses without flagging textual examples.

The bytecode scanner parses the class-file constant pool without loading inspected code. It checks `CONSTANT_Class` entries and every UTF-8 descriptor/signature, which catches field, method, generic-signature, annotation, and transitive package references even when source is unavailable. Unknown or malformed constant-pool entries fail closed with a located `IOException`.

Enforced boundaries cover:

- `compiler-core` → Gradle, IntelliJ, LSP, workspace-model packages;
- `workspace-model` → Gradle, IntelliJ, LSP;
- `language-protocol` → compiler/semantic implementations;
- `language-server` → IntelliJ;
- IntelliJ and Gradle plugins → compiler/resolver internals.

The existing root `verifyArchitecture` retains exact direct project-edge equality and cycle detection. Together, the graph check and this scanner cover direct/transitive configuration changes, source/package leakage, and compiled references. Test dependencies do not enter the production scan.

## Executed tests

```text
javac --release 21 -Xlint:all -Werror ...ArchitectureBoundaryChecker.java ...ArchitectureBoundaryCheckerTest.java
compile=0
java ... ArchitectureBoundaryCheckerTest
ARCHITECTURE_NEGATIVES_OK; exit=0
java ... ArchitectureBoundaryChecker .
ARCHITECTURE_BOUNDARIES_OK; exit=0
```

Negative fixtures prove failures for core→IDE/Gradle/LSP/workspace, server→IDEA, workspace-model→Gradle/IDE/LSP, language-protocol→compiler, IntelliJ-plugin→compiler, and Gradle-plugin→resolver. A compiled descriptor-only `compiler-core`→`com.intellij.psi.PsiFile` fixture proves bytecode detection. A comments/string fixture proves textual false positives are ignored.

## Integration boundary

The assignment forbids modifying the root build, while `build-logic` currently has no test dependency or test-task wiring. The build owner must invoke `ArchitectureBoundaryChecker.inspect(rootDir)` from `verifyArchitecture` after compilation and wire `ArchitectureBoundaryCheckerTest` into an executable test task. Until that happens, `verifyQuick` does not exercise this repair and P02-06 must not be accepted solely from the manual evidence.

No root build, module, ledger, or IDE metadata was modified.
