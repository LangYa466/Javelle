# P08-W01 WorkspaceModel contract

TASK / AGENT_ID / BASE_REVISION: `P08-W01` / `/root/p00_recon` / `0b16c5ca2c26be3fd0b9d3079fea4c80a3c0310b`

STATUS: CONTRACT_FROZEN — Java implementation and verification remain pending

Canonical schema: `spec/workspace/workspace-model-v1.schema.json` (JSON Schema 2020-12, major 1).

## 1. Single shared boundary

`workspace-model` owns the immutable model, canonical JSON codec, validator and fingerprint implementation. Gradle exports it; CLI and LSP consume it. Those adapters must not create private variants, evaluate build scripts while consuming a model, or introduce Gradle/IntelliJ/LSP runtime types into the DTO/API.

Frozen public surface for P08 implementation:

```java
record WorkspaceModel(SchemaVersion schemaVersion, SerializationKind serializationKind,
  WorkspaceDescriptor workspace, List<WorkspaceModule> modules,
  List<DocumentOverlay> overlays, ModelFingerprints fingerprints, ModelLimits limits,
  Map<String, JsonValue> extensions) {}
interface WorkspaceModelCodec {
  byte[] writePortable(WorkspaceModel model);
  byte[] writeResolved(WorkspaceModel model);
  WorkspaceReadResult read(byte[] utf8, WorkspaceReadOptions options);
}
interface WorkspaceModelValidator {
  List<WorkspaceDiagnostic> validate(WorkspaceModel model, ValidationEnvironment environment);
}
```

Records and returned collections are deeply immutable. Byte arrays are copied. Serialization is UTF-8, LF, no BOM, deterministic key ordering, deterministic module/source-set/dependency ordering, and no timestamp. Path/classpath order is preserved where it affects javac lookup; set-like roots and metadata are canonicalized by logical identity.

## 2. Portable versus resolved paths

- `logicalProjectPath` is Gradle-neutral identity such as `:app`, not a filesystem path.
- `logicalPath` is slash-separated, workspace-relative, normalized Unicode text. It rejects empty segments, `.`/`..` after percent-decoding, backslash, NUL, URI query/fragment/archive separators, drive/UNC/absolute forms and over-limit length.
- `fileUri` and `resolvedAbsoluteUri` occur only in `RESOLVED` documents. They are normalized absolute `file:` URIs; raw platform paths are never serialized.
- `relocatableKey` is SHA-256 over kind + normalized logical path + declared content/artifact identity, never the absolute checkout/JDK path.
- A `PORTABLE` writer recursively rejects `fileUri`, `resolvedAbsoluteUri`, user-home prefixes and absolute toolchain paths. Checked-in examples use only portable form.
- Resolution is an explicit trusted adapter operation. It canonicalizes real paths without following a descendant symlink outside the declared workspace unless policy is `ALLOW_DECLARED_EXTERNAL`; the target must be explicitly listed and fingerprinted.

Missing roots are represented with `exists=false`; they are not silently removed. Empty source sets and empty path lists are valid. Duplicate roots are rejected after URI normalization, real-path resolution where available, and case folding when `caseSensitivity=INSENSITIVE`; `UNKNOWN` reports ambiguity instead of guessing.

## 3. Toolchain, compilation and trust

Every module has an explicit toolchain executable path reference, Java version/vendor/runtime fingerprint. Every source set separately records Java/Javelle/generated roots, compile/runtime/processor classpaths, module path, source JARs, source/target/release, exact ordered compiler options, UTF-8 encoding, on-disk snapshot fingerprint and trust.

`release` is 21 or 25 and must be consistent with source/target and not exceed the resolved toolchain. Processor path presence does not authorize execution. `UNTRUSTED` requires all execution/network flags false. Unknown trust levels, permission enums or security-critical enum values fail closed even when a newer minor version is otherwise readable. No reader runs Gradle, processors, downloaded tools, user programs or network operations as a side effect of decoding/validation.

## 4. Dependencies and property metadata

Module IDs and `(moduleId, sourceSetName)` pairs are unique. Dependency targets must exist. `BUILD_ORDER` edges must form a DAG and a cycle reports the complete stable cycle path. `SOURCE_VISIBILITY` strongly connected components are reported separately and may be supplied to a later joint-compilation planner; they are never mislabeled as a build-order cycle. Runtime-only edges do not affect compile ordering.

Property metadata is explicit and versioned: owner binary name, property name, JVM descriptor, getter/setter names, readable/writable flags, metadata version and source/artifact fingerprint. Consumers never infer Javelle properties from JavaBeans naming alone. Duplicate `(owner, property)` entries with differing ABI are errors.

## 5. Overlay and snapshot separation

Dirty buffers are `DocumentOverlay` values keyed by normalized document URI with monotonic version, content hash, base on-disk fingerprint and `DIRTY/SAVED/DELETED` state. Optional inline UTF-8 content is size-limited. Overlays never mutate source roots, artifact fingerprints or the on-disk model; an analysis snapshot combines a model fingerprint with a separate overlay-set fingerprint. Saving requires refresh if `baseOnDiskFingerprint` is stale.

## 6. Fingerprints and compatibility

Canonical model fingerprint includes schema major and semantic fields: module graph, logical paths and ordered compiler paths, content hashes, JDK/runtime identity, releases, compiler options, processor path/trust, dependency scopes, encoding and property metadata. Changing any of these invalidates analysis cache. It excludes resolved absolute URIs, producer timestamp (none exists), inline overlay contents and unknown non-semantic presentation extensions. Separate configuration/classpath/toolchain/options hashes make invalidation auditable.

Readers reject an unknown major. They accept a newer minor only when all required known fields and security invariants validate; unknown additive object fields are retained for lossless round-trip and ignored semantically. Unknown enum values, removed required fields, duplicate JSON keys, invalid UTF-8, excessive nesting/count/bytes, non-finite numbers and ambiguous duplicate identities reject. Older-minor input receives explicit defaults only where the version migration table defines them; stale producer/model/configuration fingerprints produce `REFRESH_REQUIRED`, never silent reuse.

## 7. Security/resource validation order

1. Bound bytes, nesting, string length, collection counts and dependency edges before allocation.
2. Decode strict UTF-8 and reject duplicate JSON keys.
3. Validate schema major/required fields/enums before path resolution.
4. Normalize and validate logical paths/URIs, then duplicates/case/symlink containment.
5. Validate trust and toolchain/release consistency.
6. Validate dependency targets/cycles and property ABI uniqueness.
7. Recompute all fingerprints and return success only on exact match.

Diagnostics contain stable code, JSON Pointer, module/source-set identity and a bounded message. Required families: `JV-WS-SCHEMA-*`, `JV-WS-LIMIT-*`, `JV-WS-PATH-*`, `JV-WS-DUPLICATE-*`, `JV-WS-CYCLE-*`, `JV-WS-TRUST-*`, `JV-WS-FINGERPRINT-*`, `JV-WS-STALE-*`.

## 8. P08 acceptance and ownership

| ID | Required executable evidence |
|---|---|
| P08-01 | Round-trip every schema field; immutable/copy tests; no Gradle/IDE/LSP bytecode references. |
| P08-02 | Real single-module main/test export including empty and missing roots. |
| P08-03 | Portable/resolved golden pair; relocation yields same relocatable/model keys; traversal negatives. |
| P08-04 | Compile/runtime/processor/module/source-JAR/toolchain/release/trust round-trip. |
| P08-05 | Duplicate, case-fold, symlink escape, missing path and empty source-set diagnostics with exact pointers. |
| P08-06 | Mutate classpath/JDK/options/config/trust/property metadata one at a time; every semantic fingerprint changes. Absolute relocation alone does not. |
| P08-07 | CLI process consumes exported JSON and compiles P06 fixture without reading/evaluating build files. |
| P08-08 | Main→test valid graph, missing target, build-order cycle exact path, source-visibility SCC distinguished. |
| P08-09 | Dirty overlay changes analysis fingerprint but not on-disk model; stale base and version regression reject. |
| P08-10 | Scan every tracked portable example/golden for absolute POSIX/drive/UNC/user-home/JDK paths. |
| P08-11 | v1.0 golden, unknown additive v1 minor round-trip, unknown major/enum/removal/corrupt fingerprint rejection and refresh result. |
| P08-12 | Independent plain-Java client with only the published workspace-model JAR reads the model and checks values/fingerprint. |

Future implementation ownership: `workspace-model/src/main/**` plus its unit/golden tests belongs to one model owner. Gradle exporter, CLI consumer and independent client are separate owners after API freeze. Review must be performed by an agent that did not implement the model.

## 9. Contract validation performed

- `jq -e . spec/workspace/workspace-model-v1.schema.json` — exit 0.
- Schema has bounded arrays/content, major version pin, portable/resolved discriminator, explicit trust/toolchain/releases/paths/dependencies/overlays/property metadata/fingerprint/limits, and an extension namespace.

No P08 checkbox is accepted by this contract-only package.
