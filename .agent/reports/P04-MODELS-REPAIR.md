# P04-R01 model/source/syntax/type repair

Agent `/root/p00_repair`. Actual owned implementation paths: `compiler-core/src/main/java/dev/teyru/compiler/core/source/**`, `syntax/**`, `symbol/**`; mirrored targeted tests under `compiler-core/src/test/**`; model entries in `compiler-core/src/test/resources/public-api-v1.txt`. No diagnostic, lowering/generation, sourcemap, classfile, budget, build, ledger or IDE paths are owned.

## Result

Status: IMPLEMENTED, pending independent review. Review gaps P04-01/02/03/05 are the acceptance scope.

- P04-01/09: `SourceFile.convertBoundary` provides explicit, strict conversion among raw/translated UTF-16, Unicode code points and UTF-8 bytes. It rejects offsets within surrogate pairs and multibyte UTF-8 sequences; BOM, Chinese, emoji and EOF round-trips are exercised.
- P04-02: `UnicodeMap` now validates raw and translated UTF-16 boundaries, including an escape-produced surrogate pair. Tests cover eligible/ineligible backslash parity and an escape-created supplementary character.
- P04-03: `NodeIndex` validates every declared parent against actual CST structure; new iterative `AstIndex` does the same for AST. Reconstruction-stable IDs, trivia preservation, error nodes, missing-token invariants and inconsistent-parent negatives are exercised.
- P04-05: `AnnotatedType`, `AnnotationRef`, `CapturedType` and `AnonymousType` preserve resolved type-use annotations, capture bounds/lower bound and stable anonymous identity instead of widening to Object. Existing ABI sorting/property invariants remain intact.

Actual changed implementation paths: `source/SourceFile.java`; `syntax/NodeIndex.java`, `syntax/AstIndex.java`; `symbol/TypeRef.java`, `AnnotationRef.java`, `AnnotatedType.java`, `CapturedType.java`, `AnonymousType.java`; `ModelRepairTest.java`. No shared golden edit was necessary because the P04-11 complete-surface gate is separately owned.

Verification: `./gradlew --dependency-verification=strict :compiler-core:test verifyQuick`, exit 0. Shared suite: 32 tests, 0 failures/errors/skipped (model repair 6); aggregate BUILD SUCCESSFUL, 77 tasks. Evidence `.agent/logs/P04-MODELS-REPAIR-final.txt` and JUnit XML.
