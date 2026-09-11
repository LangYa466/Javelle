# P06 emitter and first real compilation contract

TASK / AGENT_ID / BASE_REVISION: `P06-W01` / `/root/p00_spec` / shared P05 candidate

STATUS: **CONTRACT_FROZEN — implementation remains NOT_IMPLEMENTED / NOT_VERIFIED**

## 1. Stage boundary and executable pipeline

P06 implements one honest vertical slice only:

```text
P05 ParseResult + exact SourceId fingerprint
  → declaration/property binding (no name-text rewriting)
  → typed early IR with explicit read/write/temp/conversion order
  → deterministic GeneratedFile(Java text + SourceMap)
  → atomic generated-source staging
  → javax.tools.JavaCompiler task (--release 21 or 25)
  → remapped Javelle diagnostics + class output
  → atomic publish of complete successful generation/compile result
```

The normal path invokes the JDK public compiler API, never `com.sun.tools.javac.*`, Lombok annotation processing, shell `javac`, or direct JVM bytecode generation. `compiler-core` owns IR/emission; `compiler-driver` owns I/O, javac lifecycle, staging and publication; `java-resolver` provides symbol bindings/projections without owning emission.

## 2. Frozen API

```java
// compiler-core
record EmitOptions(int languageMajor, int targetRelease, String generatorVersion,
                   String indent, String lineSeparator) {}
record EmitResult(List<GeneratedFile> files, List<Diagnostic> diagnostics) {}
interface JavaEmitter {
  EmitResult emit(BoundCompilationUnit unit, EmitOptions options,
                  ResourceTracker resources, CancellationToken cancellation);
}

// compiler-driver; all Paths are resolved/validated at this boundary
record CompileRequest(List<SourceInput> javelleSources, List<Path> javaSources,
  List<Path> classpath, List<Path> modulePath, Path generatedRoot, Path classOutput,
  int release, boolean enableProcessors, List<Path> processorPath,
  ResourceBudget budget) {}
record JavacMessage(DiagnosticCode code, Severity severity, String generatedUri,
  TextRange generatedRange, List<SourceLocation> originalLocations, String message) {}
record CompileResult(boolean success, int javacExitCode,
  List<GeneratedFile> generatedFiles, List<JavacMessage> diagnostics,
  Optional<PublishedOutputs> outputs) {}
interface CompilerDriver {
  CompileResult compile(CompileRequest request, CancellationToken cancellation);
}
```

All records defensively copy. `release` is exactly 21 or 25 in P06 and must not exceed the selected toolchain feature version. `enableProcessors=false` is the P06 default and yields `-proc:none`; enabling processors before the later controlled-round contract fails `JV-DEV-0001(annotation-processing)` rather than silently running them. A failed/cancelled/resource-exhausted request returns no `PublishedOutputs` and cannot replace the last successful output.

## 3. P06 supported lowering subset

- P05 package/import/non-generic class, explicit fields, methods, blocks, locals, literals, member/call/new/cast/operators, assignment, return and if.
- Stored property with explicit type, optional initializer, default/custom getter/setter, per-accessor visibility, contextual `field`/`value`, and simple reads/writes bound to its `PropertyDescriptor`.
- Plain fields always emit/access as fields. A method named `getName`, identifier named `field`, or JavaBeans-looking library method is not a property without a bound Javelle property symbol.
- Backing storage is private and retains the property name, initializer and allowed `static/final/volatile/transient`; custom/default accessor visibility follows B3. A computed custom getter with no initializer/storage reference emits no field.
- P06 expression lowering supports simple property read and simple property assignment. Compound assignment, `++/--`, inheritance, generic/static/interface/record properties, boolean `isX`, Lombok combinations and annotation propagation fail closed with `JV-DEV-0001` at the unsupported construct. They are not approximated.

## 4. Deterministic readable Java (P06-01/02/08/09)

- UTF-8 output, LF, four spaces, one declaration per readable line, braces in Java style, required Java semicolons, and no trailing whitespace.
- Preserve source declaration order. Imports preserve semantic groups but sort lexicographically within static/non-static groups and deduplicate by bound import identity. No hash-map iteration order affects output.
- Header is exactly two stable lines carrying Javelle generator version and normalized workspace-relative source URI/fingerprint; no timestamp, host, user, absolute path or random ID.
- Generated identifiers use deterministic collision-aware allocation from bound symbols. P06 does not need a helper for simple reads/writes; it must not invent one.
- Emission twice from immutable input is byte-identical. Re-emitting into the same staging root starts from a fresh candidate manifest and cannot accumulate imports/methods.

## 5. Property ABI and evaluation semantics (P06-02/03/06/11)

For the canonical `User.name` fixture, Java reflection must observe a private `String name`, public `String getName()`, public `void setName(String)`, and no duplicate/synthetic public member. The custom setter executes `this.name = value.trim();`; initialization writes storage once and does not invoke the setter. A Java consumer constructs the class, calls `setName(" Alice ")`, asserts `getName().equals("Alice")`, and fails to compile on direct `user.name` access.

Within emitted Javelle bodies, bound `user.name` reads become `user.getName()`, writes become `user.setName(rhs)`, and `this.name` follows the same rule. Contextual accessor `field` becomes `this.name`; ordinary field access and an unrelated local named `field` remain unchanged. Simple assignment evaluates receiver then RHS once, calls setter once, and preserves the Java assignment expression value when used as an expression; if this cannot be represented by the current early IR, diagnose unsupported rather than duplicate evaluation.

## 6. Source maps and javac diagnostic remapping (P06-04/07)

Each emitted identifier, declaration, initializer, expression and statement has a `SourceMapSegment`. Property field/getter/setter have separate generated intervals sharing the property declaration origin and distinct `GeneratedMemberOrigin`. Boilerplate tokens are `SYNTHETIC` with the nearest declaration and a nonempty reason. Map JSON follows `spec/source-map.md`: Unicode-code-point half-open primary offsets plus explicit line/UTF-16 coordinates and normalized URIs.

The driver obtains javac diagnostic source URI, start/end/position through `Diagnostic` public APIs, converts generated UTF-16 positions explicitly, queries the map, and reports the highest-priority exact/expanded original plus ordered related origins. Generated-only boilerplate maps to the nearest owner and is marked synthetic. If no valid segment exists, emit an internal mapping diagnostic carrying only the normalized generated URI; never guess an original range.

Negative fixtures:

1. Custom getter returns `String` from an `int` property: javac error must anchor the Javelle returned expression, not `generated/`.
2. Body calls `missingSymbol()`: original invocation range and generated location as related information.
3. Generated synthetic signature conflict: maps to owning property with synthetic marker, not a fabricated exact token.
4. CRLF + emoji before an error verifies code-point/UTF-16 conversion; Unicode escape verifies raw/translated mapping.

## 7. javac/toolchain and lifecycle (P06-05)

- Obtain `ToolProvider.getSystemJavaCompiler()`; absence is a toolchain error distinct from source diagnostics. Create and close a fresh `StandardJavaFileManager` per request.
- Pass explicit `--release`, UTF-8 encoding, destination, classpath/module-path, generated Java plus caller Java sources, and `-proc:none`. Never inherit ambient `CLASSPATH`, working-directory output or arbitrary JVM options.
- P06 matrix runs actual JDK 21 `--release 21`, JDK 25 `--release 21`, and JDK 25 `--release 25`. Java 25 source rejected under release 21 is a real negative. Compiler invocation/result/diagnostics are captured structurally; no success is inferred from files existing.
- Check cancellation and cumulative byte/token/node/diagnostic/deadline budgets before staging, between units/emission, before javac, and before publication. On cancellation close file manager, delete only the unique staging directory, and publish nothing.

## 8. Atomic output ownership and stale cleanup (P06-10)

The successful output root contains `META-INF/javelle/generated-files-v1.json`:

```json
{"schemaVersion":1,"generatorVersion":"<pinned>","inputFingerprint":"<sha256>",
 "files":[{"relativePath":"p/User.java","sha256":"<sha256>","kind":"JAVA"},
          {"relativePath":"p/User.class","sha256":"<sha256>","kind":"CLASS"}]}
```

Paths are normalized relative paths and hashes match exact bytes. Generation occurs below a unique sibling staging directory; validate all hashes/collisions, fsync files and manifest, then atomic rename where supported. Replacement cleanup compares the prior valid manifest and removes only listed unchanged-owned paths under the designated output root. Missing/corrupt/foreign manifest means refuse cleanup with a diagnostic. Symlinks, traversal, case-fold collision, duplicate normalized path and output-root aliasing source roots reject before writes. User files never appear in the manifest.

## 9. Independent end-to-end fixtures

The fixture oracle is handwritten Java ABI/behavior, not Java text produced or normalized by the emitter.

| Fixture | Javelle/Java action | Independent oracle |
|---|---|---|
| `stored-custom-user` | Javelle `User.name` initializer/getter/trim setter; Java `Consumer.main` | process exit 0 and stdout `Alice`; reflection modifiers/descriptors; direct field Java compile fails |
| `plain-field-vs-property` | same class has `raw` field and `name` property | reflection: no `getRaw/setRaw`; property methods exist; AST symbol IDs distinguish accesses |
| `computed-property` | custom getter returning explicit field/method expression | no backing property field; consumer result equals expected |
| `mixed-body` | method uses local `val`, if, property write/read | generated Java compiles and process output matches handwritten expected result |
| `javac-type-error` | wrong getter return type | compile false, no class publication, exact original range |
| `missing-symbol` | missing invocation | compile false, mapped original/related generated coordinates |

For success cases the harness launches `java` as a separate process with only the fresh class output, asserts exit/stdout/stderr and timeout, then loads a separate reflection verifier. Golden Java asserts stable formatting/readability and key ABI text but is not the sole semantic oracle. Run fixture once from a path containing spaces and non-ASCII and scan output/manifest/map for the checkout absolute path.

## 10. Requirement acceptance matrix

| ID | Required evidence |
|---|---|
| P06-01 | Golden readable Java plus normal `javac` compile and formatting assertions. |
| P06-02 | Stored/default/custom/computed property ABI and visibility reflection. |
| P06-03 | Symbol-ID based field/property access test; same-name negative and receiver/RHS once counters. |
| P06-04 | Per-node maps, distinct field/getter/setter segments, stable header/no timestamp. |
| P06-05 | Public JavaCompiler API, real exits/diagnostics, release 21/25 and missing-compiler test seam. |
| P06-06 | Separate Java consumer process proves trim/getter and private direct-access rejection. |
| P06-07 | Type/missing-symbol/emoji-CRLF/Unicode-escape errors remap to exact Javelle ranges. |
| P06-08 | Two independent emissions byte/hash equal; repeated staging has no accumulation. |
| P06-09 | Inspect generated `.java`; compile it without Lombok processor/runtime. |
| P06-10 | Valid ownership manifest, stale-owned deletion, corrupt/foreign/traversal/symlink counterexamples. |
| P06-11 | Separate reflection verifier checks fields/modifiers/method return/parameter descriptors. |
| P06-12 | Reviewer mutates setter to skip trim/direct assign and proves consumer/reflection tests fail. |

## 11. Acceptance commands

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-core:test :compiler-driver:test --rerun-tasks
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-driver:p06EndToEnd --rerun-tasks
JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-driver:p06Release21 --rerun-tasks
```

The latter task names are required deliverables, not claims that they exist today. Acceptance records exact exits, JUnit counts, consumer process output, javac structured diagnostics, artifact hashes and reviewer mutation evidence.

## 12. Current gaps and dependency decisions

- `compiler-driver` and `java-resolver` currently contain boundary markers only; no pipeline/API exists.
- The P05 early frontend subset is ACCEPTED at commit `0b16c5c` and is the executable input boundary for P06. Unsupported or recovered/error AST cannot reach emission; the driver returns frontend diagnostics and publishes nothing. Full Java-first grammar remains scheduled for P12+ and is not implied by this early subset.
- P05 diagnostic namespace drift (`JVL-*` implementation versus normative `JV-*`) must be resolved before remapping contracts can pass.
- Full joint Java↔Javelle attribution, annotation-processing rounds, compound property operations and classfile debug mappings are explicitly later stages. Diagnostic source mapping is not debugger support.
