# P09 minimal Gradle plugin contract

TASK / AGENT_ID / BASE_REVISION: `P09-W01` / `/root/p00_spec` / shared P08 candidate

STATUS: **CONTRACT_FROZEN — P09 implementation NOT_VERIFIED**

## 1. Public identity and compatibility boundary

The binary plugin ID is `org.javelle`, implementation class `org.javelle.gradle.JavellePlugin`, and extension name `javelle`. Contract schema is v1; the local development implementation version is `0.1.0-SNAPSHOT`. This freezes local consumer identity but does not claim ownership or availability on Plugin Portal: publication remains `NOT_AUTHORIZED`. `spec/gradle/plugin-contract-v1.json` is the machine contract.

P09 supports Gradle 9.6 with Java toolchains 21 and 25, the standard Java plugin, `main`/`test`, jar and application consumers. Custom source sets, multi-project production semantics, build cache/relocation, processors, module path, source/Javadoc JAR, test fixtures and parallel source sets remain P37 acceptance scope unless specifically exercised below; unavailable combinations fail with an actionable Gradle error rather than silently dropping `.javelle`.

## 2. Extension

```kotlin
javelle {
    languageVersion.set("0.1")
    targetRelease.set(21)
    compilerExecutable.set(layout.projectDirectory.file("tools/javelle"))
    compilerOptions.addAll("--no-color")
    diagnosticsFormat.set("json")
    trustProcessors.set(false)
}
```

All values are lazy Gradle `Property`/`ListProperty`/`RegularFileProperty`; conventions use provider APIs and are finalized on read at task execution. P09 accepts target release 21 or 25 and rejects a target above the selected launcher/toolchain. Options are normalized ordered inputs; duplicate/conflicting output, release, project-model or diagnostics flags are forbidden because the plugin owns them. No environment `CLASSPATH`, global Javelle config or working-directory discovery changes task identity.

The configured compiler is an explicit executable/distribution input with content/version fingerprint. It runs via process isolation or classloader-isolated Worker API; compiler classes never enter the Gradle daemon/plugin classloader. No tool download occurs. Missing/untrusted/non-executable compiler fails before outputs are changed.

## 3. Source sets and task graph (P09-01…06)

Applying `org.javelle` applies or waits lazily for `java`; it creates `src/main/javelle` and `src/test/javelle` logical source directories without creating them on disk. For source set `main`:

```text
exportJavelleWorkspaceModel ─┐
                            ├→ generateJavelleJava → compileJava → classes → jar/run
src/main/javelle ────────────┤
src/main/java (analysis) ────┘
```

For `test`, `exportTestJavelleWorkspaceModel → generateTestJavelleJava → compileTestJava → test`; test generation also consumes main outputs/model through normal source-set providers. `compileJava` receives `generateJavelleJava.outputDirectory` through `SourceDirectorySet.srcDir(TaskProvider)`, which establishes the dependency without `dependsOn` string wiring. Likewise for test.

Generation consumes Java source files for declaration analysis but never `compileJava` output; therefore there is no `compileJava ↔ generateJavelleJava` cycle. The finite P09 mixed-source strategy is P06 header/source analysis plus one joint javac compilation: generated Javelle Java and consumer Java are both compileJava sources. A cycle assertion walks the realized task graph in TestKit.

Workspace export uses accepted P08 schema/codecs only; the plugin maps Gradle providers into that model and does not fork a second DTO. Outputs are `build/javelle/workspace/main.json` and `test.json`. Source maps are under `build/javelle/source-maps/<sourceSet>/`; generated Java is exactly `build/generated/sources/javelle/<sourceSet>/`. Nothing writes `src/`.

Neither plugin application nor task configuration opens sources, resolves the compiler executable, starts a worker/process, executes Gradle scripts, probes the JDK, or accesses the network. Those occur in task actions from declared providers. No `afterEvaluate`, eager task realization, `Project` capture in task actions or mutable static service is permitted.

## 4. Task types, inputs and outputs

`GenerateJavelleJava` is cacheable only after P37 proves relocation; at P09 it must at least be incremental/up-to-date correct. Annotated properties:

- `@InputFiles @PathSensitive(RELATIVE)`: sorted Javelle sources; sorted Java analysis sources; accepted P08 model inputs; compiler distribution files; referenced configuration files.
- `@Classpath`: compile classpath and resolver inputs, including dependency JAR content.
- `@Input`: plugin/compiler/language/schema versions, source-set logical identity, target release, encoding, normalized compiler options, processor trust flag and toolchain language/vendor identity.
- `@OutputDirectory`: generated Java root and source-map root; `@OutputFile`: ownership manifest. Workspace export owns only its P08 JSON output.

The literal `@Classpath` semantics, not absolute path strings, fingerprint dependency content. Java source analysis is a declared input but generated/classes directories are excluded to avoid feedback. The isolated invocation receives a deterministic argument/model file rather than command-line concatenation; spaces and non-ASCII paths are preserved.

The task stages all generated Java/maps/manifest, verifies hashes and P06 path rules, then atomically publishes. Its ownership manifest lists exact relative path, type, input source fingerprint and output SHA-256. On removed/renamed `.javelle`, only previously manifest-owned stale Java/map outputs are deleted; foreign files, unlisted files, symlinks and modified owned files are retained and cause an actionable collision/ownership failure. A failed compiler run leaves the last accepted output intact and the Gradle task fails.

## 5. Toolchain, release and trust

The plugin obtains `JavaToolchainService` providers and configures the isolated compiler/javac with an explicit launcher. `targetRelease` defaults from `JavaCompile.options.release`, otherwise the selected toolchain language version; disagreement is a configuration error. Main/test use the corresponding compile task providers and UTF-8 unless explicitly and consistently configured.

P09 always disables annotation processors in Javelle analysis/isolated compilation and never executes user code. Consumer `compileJava` may use its explicitly configured processors under Gradle's normal trust boundary, but P09 does not add processors or processor paths. Workspace model `trustPolicy` is honored and never widened. Opening/configuring a project performs no external process or network action.

## 6. Minimal real TestKit consumers

Every test uses a temporary directory outside this repository, its own `settings.gradle(.kts)` with `pluginManagement { includeBuild(<validated local repo>) }`, and the trusted Gradle wrapper. It invokes GradleRunner/external wrapper tasks, then inspects task outcomes and real artifacts; it never directly calls compiler internals.

1. `java-calls-javelle`: `src/main/javelle/p/Greeting.javelle`, Java `Main` calls it, `application` run prints expected output; jar contains both classes.
2. `javelle-calls-java`: Java helper used by a Javelle method; `classes` and external `java` process succeed, proving same-module mixed analysis without a cycle.
3. `test-source-set`: Java or Javelle test consumes main Javelle class; `test` executes a real assertion and both generate tasks have correct graph edges.
4. `failure-location`: syntax/type error fails build and log/JSON contains normalized `.javelle` path/range, not only generated Java.
5. `ownership`: build, unchanged rebuild (`UP_TO_DATE`), edit one source (generation executes and output changes), delete source (owned Java/class disappears), preserve injected foreign file, reject modified-owned collision.

Run clean and non-clean sequences. First successful build must not rely on a prior repository build or developer IDE classpath. Test the local plugin version through `includeBuild`; never resolve `org.javelle` from public Plugin Portal.

## 7. Configuration-cache negative and minimum correctness

Run `help`, `tasks`, and consumer `classes --configuration-cache` twice. Both configuration-time commands must show no compiler process marker/network/source read. P09 does not claim full configuration-cache compatibility unless the second `classes` run reports reuse and output is correct. If current implementation captures unsupported Gradle objects, the test must fail and P09 remains unaccepted; it cannot suppress the warning or use `--no-configuration-cache` as acceptance.

For task up-to-date behavior, the second identical `classes` must report both export/generate tasks `UP_TO_DATE`; source content, Java analysis signature, classpath JAR content, compiler binary/version, release or relevant option changes must rerun. P09 verifies these basic invalidations. Cross-directory build-cache relocatability, remote cache and fine-grained per-file compilation belong to P37 and are not claimed here.

## 8. Requirement acceptance matrix

| ID | Required evidence |
|---|---|
| P09-01 | Binary plugin descriptor resolves ID/version; typed lazy extension/task via real consumer, no hand-written JavaExec. |
| P09-02 | Main/test Javelle roots and named generate tasks exist without creating source dirs; test source-set fixture runs. |
| P09-03 | Generated directory is task-provider-backed Java source; `compileJava` dependency and jar/application artifacts verified. |
| P09-04 | Java sources declared as analysis inputs; graph walk proves no reverse compile cycle; both mixed directions run. |
| P09-05 | Worker/process uses selected toolchain; daemon classpath scan lacks compiler-core; missing compiler fails closed. |
| P09-06 | Main/test accepted P08 JSON and source maps below `build/javelle`; no `src` mutation or absolute-path leakage. |
| P09-07 | Fresh includeBuild consumer resolves locally while offline; no Plugin Portal publication assumption. |
| P09-08 | TestKit runs build/test/jar/application and external Java process for Java↔Javelle fixtures. |
| P09-09 | Identical second build `UP_TO_DATE`; source/Java signature/classpath/compiler/release/options invalidate. P37 owns full cache matrix. |
| P09-10 | Syntax and type negatives fail task/build with exact `.javelle` source location and no new partial output. |
| P09-11 | Delete/rename removes owned stale Java/class; foreign/modified/symlink/path-collision counterexamples are preserved/rejected. |
| P09-12 | Independent reviewer follows docs in a new consumer directory, using only local distribution/includeBuild and declared JDK. |

## 9. Commands and evidence

```bash
python3 -m json.tool spec/gradle/plugin-contract-v1.json >/dev/null
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :gradle-plugin:test --rerun-tasks
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :gradle-plugin:p09TestKit --rerun-tasks
```

`p09TestKit` is a required implementation deliverable, not a current capability claim. Evidence records Gradle/TestKit versions, exact task outcomes for each invocation, process exits/output, output hashes/manifests, graph assertions and configuration-cache status. Independent reviewer repeats from a clean external consumer and mutates/deletes one source.

## 10. Current-state risks and dependencies

- Existing plugin registers only extension plus compiler-executable validation; P08 workspace export pieces exist, but P09 generate task graph and consumer TestKit remain to be implemented and reviewed.
- P06 driver and accepted P08 model are dependencies. P09 must adapt their public contracts; it cannot copy compiler code into the plugin or fork the model schema.
- `org.javelle` is frozen for local use only. Public namespace verification and publication credentials remain a later authorization gate, not a reason to stop local implementation.
