# P04-W01 — compiler-core model/API contract

- Agent `/root/p00_spec`; base: current P03 candidate
- Scope: P04-01…12 model freeze only; implementation remains NOT_IMPLEMENTED/NOT_VERIFIED.
- Ownership: Java implementation belongs exclusively to `compiler-core`; workspace configuration DTOs remain `workspace-model`. Neither package may expose Gradle, IntelliJ, LSP4J or filesystem-provider implementation types.

## Package and version boundary

Public packages are `dev.teyru.compiler.core.source`, `.syntax`, `.diagnostic`, `.symbol`, `.lowering`, `.sourcemap`, `.analysis`, `.budget`. Public types are final classes, records, sealed interfaces or enums. Collections are immutable defensive copies in stable order; no nullable collections/elements. Schema/API major is `1`; JSON has explicit `schemaVersion: 1` and canonical UTF-8 encoding.

Equality is structural for value records. `SourceId`, `NodeId`, `SymbolId` equality uses documented stable content, never object identity, process counters, absolute machine paths or `hashCode()`. Builder/internal caches are excluded from equality/serialization. Offset arithmetic uses checked `long` during decoding/composition and rejects values beyond the public non-negative `int` coordinate range before narrowing.

## Source and coordinate API (P04-01/02/09)

```java
record SourceId(String workspaceRelativeUri, String contentSha256) {}
enum OffsetUnit { RAW_UTF16, TRANSLATED_UTF16, UNICODE_CODE_POINT, UTF8_BYTE }
record TextRange(OffsetUnit unit, int startOffset, int endOffset) {}
record LinePosition(int line, int utf16Character) {}
enum LineEnding { LF, CRLF, CR }

final class SourceFile {
  static SourceFile decode(SourceId id, byte[] utf8, ResourceBudget budget);
  SourceId id(); byte[] utf8(); String rawText(); String translatedText();
  LineMap rawLineMap(); UnicodeMap unicodeMap(); InputFingerprint fingerprint();
}
interface LineMap {
  LinePosition positionOf(int rawUtf16Offset);
  int offsetOf(LinePosition position);
  TextRange lineRange(int zeroBasedLine);
}
interface UnicodeMap {
  TextRange rawRangeForTranslated(TextRange translated);
  TextRange translatedRangeForRaw(TextRange raw);
  int rawBoundary(int translatedBoundary, Bias bias);
  int translatedBoundary(int rawBoundary, Bias bias);
}
enum Bias { START, END }
```

`TextRange` is half-open, always carries its coordinate unit, and enforces `0 <= start <= end`; containment/intersection requires equal units and never overflows. Lexer/parser ranges use `RAW_UTF16` or `TRANSLATED_UTF16` to match Java `String`. The normative serialized source-map primary offsets use `UNICODE_CODE_POINT`, while its explicit line/character projection uses UTF-16 as required by `spec/source-map.md`; UTF-8 byte offsets are only `UTF8_BYTE`. Cross-unit conversion is explicit through `SourceFile`/`UnicodeMap`, never implicit. A boundary inside a surrogate pair or CRLF pair is rejected rather than rounded. `LineMap` accepts EOF, preserves zero-length last lines, treats CRLF as one terminator and round-trips every valid boundary.

Unicode translation follows P03/JLS eligibility/parity before tokenization. The map retains every raw↔translated boundary; escape-created newline and surrogate pairs are tested. Many raw characters may map to one translated code unit, so range conversion requires explicit start/end bias and monotonicity. Invalid UTF-8 reports a decoding diagnostic with original byte location; no platform default charset.

`SourceId.workspaceRelativeUri` is normalized forward-slash URI text: no absolute filesystem path, drive prefix, backslash, NUL, encoded or decoded `..`, archive escape, query/fragment ambiguity. Symlinks are resolved/enforced by the caller workspace boundary; core never opens the URI.

## Trivia, tokens, CST and AST (P04-03)

```java
enum TriviaKind { WHITESPACE, NEWLINE, LINE_COMMENT, BLOCK_COMMENT, JAVADOC, BOM }
record Trivia(TriviaKind kind, TextRange rawRange, TextRange translatedRange, String rawText) {}
record Token(TokenKind kind, TextRange rawRange, TextRange translatedRange,
             String rawText, String value, List<Trivia> leadingTrivia,
             List<Trivia> trailingTrivia, boolean missing) {}
record NodeId(SourceId source, String grammarKind, TextRange rawRange, int ordinal) {}
sealed interface CstNode permits CstBranch, CstToken, CstError { NodeId id(); TextRange range(); }
sealed interface AstNode permits AstDeclaration, AstStatement, AstExpression, AstError { NodeId id(); TextRange range(); }
record AstError(NodeId id, TextRange range, String recoveryReason, List<TokenKind> expected) implements AstNode {}
```

Traversal is ordered, read-only and iterative-safe: `children()`, optional `parentId()` (not object parent), and lookup through an immutable `NodeIndex`. Node IDs are deterministic for identical source and parser output; inserted/missing nodes use the synchronization boundary plus deterministic sibling ordinal. Trivia is never silently dropped. Error nodes cover consumed range; missing tokens have zero-width range. Recovery must consume or stop and is bounded by `ResourceBudget`.

## Diagnostics (P04-04/10)

```java
enum Severity { ERROR, WARNING, INFORMATION, HINT }
record DiagnosticCode(String value) {}
record RelatedInformation(SourceId source, TextRange range, String message) {}
record TextEdit(SourceId source, TextRange range, String replacement) {}
record Fix(String id, String title, FixKind kind, List<TextEdit> edits) {}
record Diagnostic(int schemaVersion, DiagnosticCode code, Severity severity,
  SourceId source, TextRange range, String message, List<RelatedInformation> related,
  List<Fix> fixes, Map<String, JsonValue> data) {}
```

Codes match the versioned P03 catalog. Primary and related ranges are original-source half-open ranges. Fix edits are sorted by source/start, non-overlapping, in-bounds, version/fingerprint-bound and applied from end to start; cross-file fixes are explicit. JSON property order is schema-defined; arrays retain semantic order; map keys sort lexicographically. No ANSI, throwable text, absolute path, locale/timezone-dependent formatting or unordered map output. Duplicate diagnostics use `(code, source, range, normalized data identity)`, not message text.

Schema v1 readers ignore unknown additive fields but reject wrong type/missing required field. A higher major rejects with `TY-SCHEMA-UNSUPPORTED`; it never guesses. Writers always emit current fields. Serialization twice and across working directories must be byte-identical.

## Symbols, properties and ABI projection (P04-05)

```java
record SymbolId(String moduleId, String ownerBinaryName, SymbolKind kind,
                String simpleName, String erasedDescriptor, int declarationOrdinal) {}
sealed interface TypeRef permits PrimitiveType, DeclaredType, ArrayType,
  TypeVariable, WildcardType, IntersectionType, NullType, ErrorType {}
record GeneratedMemberOrigin(SourceId source, NodeId declaration,
  OriginKind kind, String featureId) {}
record AccessorDescriptor(SymbolId method, Visibility visibility,
  String jvmName, String descriptor, List<AnnotationUse> annotations) {}
record PropertyDescriptor(SymbolId property, TypeRef type, PropertyKind kind,
  Optional<SymbolId> storage, Optional<AccessorDescriptor> getter,
  Optional<AccessorDescriptor> setter, Set<PropertyModifier> modifiers,
  GeneratedMemberOrigin origin, int metadataVersion) {}
record AbiProjection(List<AbiType> types, List<AbiMember> members,
                     List<PropertyDescriptor> properties) {}
```

Symbol IDs are stable across JVM processes/workspace relocation and distinguish overloads/constructors/generated members. `TypeRef` preserves generics/annotations/null type/error state; it never widens capture/intersection/anonymous inference to `Object`. Property kind is `STORED` or `COMPUTED`; storage/accessor optionality must satisfy P03 invariants. Generated origins distinguish `SOURCE`, `PROPERTY_EXPANSION`, `LOMBOK_INTRINSIC`, `COMPILER_SYNTHETIC`; Lombok feature IDs are resolved FQNs.

ABI projection includes public/protected/package members needed by Java joint attribution plus necessary private structure marked non-API. Ordering is owner, declaration order, kind, JVM signature. Metadata v1 is additive; incompatible major produces a diagnostic rather than guessing JavaBeans properties.

## Lowering/generation boundary (P04-06)

```java
sealed interface IrNode { NodeId id(); TypeRef type(); SourceOrigin origin(); }
record SourceOrigin(MappingKind kind, List<SourceLocation> locations, String reason) {}
interface JavaGenerationSink { GeneratedRange emit(JavaFragment fragment, SourceOrigin origin); }
record GeneratedFile(SourceId source, String relativeUri, String javaText,
                     SourceMap sourceMap, String contentSha256) {}
```

Typed IR represents evaluation order explicitly (temporary, read, convert, write, branch), never as untyped Java snippets. Every emitted fragment has an original origin or a nonempty synthetic reason. Generated relative URI passes the same traversal rules; no timestamp/absolute path/random identifier. P04 freezes interfaces only, not property lowering implementation.

## Cancellation, budgets, fingerprints and snapshots (P04-07)

```java
interface CancellationToken { boolean isCancelled(); void throwIfCancelled(); }
record ResourceBudget(long maxBytes, int maxTokens, int maxNodes,
  int maxDiagnostics, int maxNesting, long deadlineNanos) {}
record InputFingerprint(String algorithm, String hex) {}
record AnalysisSnapshot(long schemaVersion, InputFingerprint input,
  Map<SourceId, SourceFile> files, NodeIndex nodes, SymbolIndex symbols,
  List<Diagnostic> diagnostics) {}
```

Budgets are positive, checked before allocation/recursion and fail with a stable resource diagnostic, not OOM/stack overflow. Deadline uses injected monotonic clock and is not serialized. Cancellation is checked at bounded lexer/parser/index/lowering intervals and never yields a publishable partial snapshot. Snapshots defensively copy, expose no mutable collections, are safe for concurrent readers, and have no lazy mutation visible in equality/hash/JSON. Fingerprints hash canonical content plus relevant options/schema, never file mtime alone.

## Source-map API (P04-08/12)

```java
enum MappingKind { DIRECT, EXPANDED, SYNTHETIC, RELATED }
record SourceLocation(SourceId source, TextRange range) {}
record GeneratedRange(String generatedRelativeUri, TextRange range) {}
record SourceMapSegment(GeneratedRange generated, List<SourceLocation> originals,
  MappingKind kind, NodeId node, Optional<GeneratedMemberOrigin> memberOrigin,
  int priority, String reason) {}
interface SourceMap {
  List<SourceMapSegment> generatedAt(String uri, int offset);
  List<SourceMapSegment> generatedOverlapping(GeneratedRange range);
  List<SourceMapSegment> originalAt(SourceLocation location);
  SourceMap compose(SourceMap next);
}
```

Indexes are interval trees/equivalent `O(log n + k)`, not line-only maps. Segments use half-open ranges; zero-width synthetic anchors are queryable only at the exact boundary. Overlaps are legal and returned in deterministic order: priority, DIRECT→EXPANDED→RELATED→SYNTHETIC, smallest generated range, stable node ID. Synthetic segments require nonempty reason and nearest owning declaration; they never fabricate exact source text.

Composition maps generated→intermediate→original, retains all related origins, combines kinds conservatively (`SYNTHETIC` dominates, then `EXPANDED/RELATED`, else `DIRECT`), checks overflow and rejects mismatched intermediate URI/fingerprint/schema. One property declaration expanding to field/getter/setter yields distinct generated intervals sharing an original declaration plus accessor-specific origins; reviewer must locate each method body/name, not only the whole file.

Diagnostic source maps remain separate from LSP position conversion and JVM SMAP/debug strata.

## P04 acceptance matrix

| ID | Required tests |
|---|---|
| P04-01 | empty/EOF, every valid offset roundtrip, invalid/surrogate/CRLF mid-boundary rejection, UTF-8 failure |
| P04-02 | literal/escaped Unicode, eligibility parity, escape newline, many-to-one start/end bias, monotonic mapping |
| P04-03 | trivia conservation, stable IDs, parent/child index, missing/error node, incomplete-buffer bounded recovery |
| P04-04 | full diagnostic/fix roundtrip, overlap/out-of-bounds edit negatives, original ranges, no ANSI/path leak |
| P04-05 | overload/generic/capture/property structural equality, relocation/process-stable IDs, ABI ordering |
| P04-06 | typed IR origin required, synthetic reason required, deterministic generated URI/text |
| P04-07 | cancel/no partial publication, byte/token/node/nesting/deadline bounds, concurrent immutable readers |
| P04-08 | point/range/reverse/overlap/composition queries, mapping precedence, many-to-one and one-to-many |
| P04-09 | Chinese, emoji, CRLF/CR/LF, BOM, text block, Unicode-created newline golden roundtrips |
| P04-10 | byte-identical JSON, shuffled map input, unknown additive field, missing/type/future-major negatives |
| P04-11 | japicmp/API snapshot and forbidden Gradle/PSI/LSP references in public signatures/bytecode |
| P04-12 | independent property→field/getter/setter exact interval lookup and synthetic helper counterexample |

## Required negative/security cases

- `TextRange(unit,-1,0)`, reversed/overflow endpoints, integer addition overflow, and intersections across unequal units.
- UTF-8 overlong/invalid continuation; unpaired surrogate policy; offset inside emoji surrogate or CRLF.
- `../`, `%2e%2e`, backslash, absolute/drive/UNC, NUL, archive `!/../`, symlink escape URIs.
- Unicode escape producing `;`/newline; CRLF split by translated mapping; BOM outside offset zero.
- Cyclic/malformed JSON, duplicate required keys, unknown major, excessive nesting/size, nondeterministic map order.
- Source-map overlap without deterministic priority, empty synthetic reason, composition fingerprint mismatch.
- Concurrent reader observes mutation; cancelled/over-budget operation publishes caches/snapshot.

## Implementation ownership and commands

Implementation paths: `compiler-core/src/main/java/dev/teyru/compiler/core/{source,syntax,diagnostic,symbol,lowering,sourcemap,analysis,budget}/`. Tests mirror packages under `src/test`. `workspace-model` may reference stable core-neutral identifiers only through an accepted dependency/API decision; core must not depend on workspace-model.

Minimum owner commands after implementation:

```bash
./gradlew --no-daemon --dependency-verification=strict :compiler-core:test
./gradlew --no-daemon --dependency-verification=strict verifyArchitecture verifyCompiledArchitecture
./gradlew --no-daemon --dependency-verification=strict verifyQuick
```

Reviewer additionally runs tests in a clean copy, mutation checks one coordinate/source-map assertion, API drift comparison, and a URI traversal corpus. Passing this report review alone does not satisfy P04.
