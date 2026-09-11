# P07 initial CLI and machine diagnostics contract

TASK / AGENT_ID / BASE_REVISION: `P07-W01` / `/root/p00_spec` / shared P06 candidate

STATUS: **CONTRACT_FROZEN — implementation remains NOT_IMPLEMENTED / NOT_VERIFIED**

## 1. Command surface frozen at P07

The installed application name is `javelle`. POSIX and Windows launchers invoke the same `compiler-cli` main class without requiring users to assemble a classpath. `javelle --version` prints exactly `Javelle <semver> (language <major>, Java <min>-<max>)` plus LF; `--help` and each `<command> --help` are locale-stable, list only options actually accepted, write to stdout and exit 0.

| Command | P07 behavior |
|---|---|
| `doctor [--format human|json] [--jdk <path>]` | Check launcher/runtime, selected `java`/`javac`, feature/release support and readable paths. It never compiles user code or downloads/runs a build. |
| `check --project <workspace-model.json> [--diagnostics human|json]` | Consume exactly the P08 workspace-model schema when available; before P08, also accept one or more explicit `.javelle` files. Parse/bind/compile-check through `compiler-driver`, publish no generated/class output. |
| `compile --project <workspace-model.json> [--output <dir>] [--diagnostics human|json]` | Use the same driver pipeline and atomically publish P06 generated/classes/manifest only on success. Explicit-file mode is allowed before P08. |
| `emit-java <file>... --output <dir> [--diagnostics human|json]` | Run through frontend/binding/emitter and atomically publish readable Java/source maps/ownership manifest, but no class files. |
| `explain <diagnostic-code> [--format human|json]` | Read the pinned diagnostic catalog. Unknown code is a usage error with `JV-CLI-UNKNOWN-DIAGNOSTIC`; never synthesize text. |

`format`, `migrate`, `inspect`, project-directory discovery, and `javelle-lsp` are recognized roadmap commands but unavailable at P07: they do not appear as usable in normal help, and direct invocation prints a stable “not available in this version” error to stderr and exits 6. P07 does not redefine a workspace model: `--project` is a reference to the versioned P08 schema only; unknown/missing/future schema is exit 4.

## 2. Arguments, input and trust

- GNU-style long flags; `--` ends options. Unknown, duplicate singleton, missing value and mutually exclusive arguments exit 2. Response files, environment option injection and plugin loading are not supported in P07.
- Every source argument is one OS argument; spaces/non-ASCII need no internal re-splitting. Normalize only after filesystem resolution. Reject NUL, traversal outside declared roots, source/output aliasing, symlink escape, archive marker, URI query/fragment and case-fold output collisions before writes.
- `-` source/stdin is intentionally unavailable at P07 and exits 6, avoiding an invented base URI/fingerprint contract. stdin otherwise remains unread.
- No command evaluates Gradle/Maven files, executes annotation processors, follows remote URIs, downloads a JDK/dependency, or sends source externally. `doctor` is read-only. P08 model trust policy is consumed, not overridden.
- Options are parsed with bounded count/length; sources and diagnostics use P04 cumulative budgets. Secret/environment values, absolute home paths and stack traces never enter stable JSON.

## 3. stdout, stderr, color and quiet

Machine output is stdout and logs/progress are stderr. For `--diagnostics json`/`--format json`, stdout contains exactly one UTF-8 JSON document plus LF, with no BOM, ANSI, banner or progress; stderr may contain concise non-diagnostic progress unless `--quiet`. `--quiet` suppresses progress only, never diagnostics or errors. `--no-color` is always accepted; color is used only for human stderr when attached to a terminal and `NO_COLOR` is absent. JSON is never colored.

Human `check`/`compile` diagnostics go to stderr; successful human commands may print artifact paths to stdout only when that is their documented result. `emit-java` human success prints normalized emitted paths, one per line, sorted. Broken pipe is an I/O error unless caused by the requested process cancellation. Messages use LF even on Windows for deterministic capture; launcher quoting preserves OS arguments.

## 4. Diagnostic JSON and deterministic ordering (P07-04)

`spec/cli/diagnostics-v1.schema.json` is the v1 envelope. It carries `schemaVersion`, command, status, structured P04 diagnostics and exact severity counts. Source URIs are normalized workspace-relative URIs with SHA-256; ranges are original raw UTF-16 half-open coordinates. Related information, typed fixes and data remain structured. Codes use the normative `JV-*` namespace; the known P04 implementation drift that only accepts `JVL-*` must be repaired, not reflected in this public schema.

Writers emit schema property order, diagnostics sorted by source URI/start/end/severity/code/data identity, related information in semantic order, canonical fixes, and lexicographic data keys. Readers ignore additive diagnostic fields in major 1 but reject missing/wrong required values and higher envelope major. Two runs, workspaces relocated under different absolute roots, locale/timezone changes and color environment produce byte-identical JSON.

## 5. Exit codes

`spec/cli/exit-codes.json` is normative: 0 success, 2 usage, 3 compilation, 4 toolchain/I/O/model, 5 internal, 6 recognized-but-not-available, 124 timeout and 130 interrupt. Multiple causes choose by precedence: interrupt, timeout, internal, toolchain/I/O, compilation, success. Warnings alone exit 0. `doctor` unhealthy exits 4; malformed doctor arguments exit 2. No exception class name or stack trace changes the code.

## 6. Atomic output and lifecycle

CLI delegates generation/compilation to the P06 driver; it does not duplicate frontend/emitter logic. Output uses the P06 sibling staging directory, hash/collision validation, fsync/atomic replace and ownership manifest. Parse, bind, javac, serialization, timeout, cancellation or write failure publishes nothing and preserves the prior accepted output. Cleanup only touches manifest-owned files.

A JVM shutdown handler converts first SIGINT/Ctrl-C to the shared cancellation token, closes JavaCompiler file managers/streams, removes the unique staging directory and exits 130. A second signal may terminate immediately. Deadline expiry follows the same cleanup and exits 124. No daemon/non-daemon worker, file handle or child process remains. Test seams inject cancellation/deadline; external process tests also send a real signal where supported.

## 7. Requirement acceptance matrix

| ID | Required black-box evidence |
|---|---|
| P07-01 | Distribution launcher from a directory with spaces; `--version`, root/subcommand help, Windows script argument fixture. |
| P07-02 | External `check`, `compile`, `emit-java` invoke the same driver spy/fingerprint and real P06 fixture; outputs differ only by requested publication. |
| P07-03 | One external process fixture for each of 0/2/3/4/5/6/124/130; assert stdout/stderr and no partial output. |
| P07-04 | Validate real stdout against diagnostics schema; duplicate/type/future-version/canonical-order negatives and no ANSI. |
| P07-05 | Spaces, Traditional Chinese and emoji paths/diagnostics; quiet/no-color/redirected streams and Windows quoting. |
| P07-06 | Every P03 catalog code explains from catalog; unknown/malformed code exits 2 without fabricated explanation. |
| P07-07 | Healthy JDK, missing javac, unsupported release, unreadable path, and source compile error are distinct JSON/status/exit results. |
| P07-08 | Pre-existing accepted output survives compiler/write failure; successful replace has valid P06 manifest and no staging residue. |
| P07-09 | Real SIGINT and injected timeout/cancel; bounded exit, closed handles, no daemon/process/staging leak. |
| P07-10 | Help/docs executable snippets enumerate only P07 commands; direct later-command invocation exits 6. |
| P07-11 | Tests launch packaged executable, parse stdout independently, inspect stderr and filesystem; no direct main-method unit substitute. |
| P07-12 | Independent reviewer runs packaged launcher in a newly created non-repository directory using only declared JDK and fixture inputs. |

## 8. Black-box fixture matrix

1. Success: P06 `stored-custom-user` through `check`, `emit-java`, then `compile`; run class and validate stdout/reflection separately.
2. Compilation negatives: syntax semicolon and javac missing-symbol/type mismatch; exact `JV-*` code/range, exit 3, empty/new output absent and old output retained.
3. Invocation negatives: no command, unknown/duplicate flags, missing file/project, directory supplied as file, malformed/future P08 JSON, source/output alias, symlink escape and paths with spaces/中文/emoji.
4. Toolchain/lifecycle: missing javac, release mismatch, unwritable target, disk-write fault seam, SIGINT, timeout and forced internal exception sanitization.
5. Determinism: run JSON/emission twice, from relocated working directory and different locale/timezone/`NO_COLOR`; compare bytes and scan for checkout/home path leakage.

Golden expected JSON is handwritten and schema-validated, not generated by the CLI serializer. Golden human output separately asserts stable code/location/message shape; it is not parsed as machine data.

## 9. Acceptance commands

```bash
python3 -m json.tool spec/cli/exit-codes.json >/dev/null
python3 -m json.tool spec/cli/diagnostics-v1.schema.json >/dev/null
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-cli:test --rerun-tasks
JAVA_HOME=/usr/lib/jvm/java-25-openjdk PATH=/usr/lib/jvm/java-25-openjdk/bin:$PATH \
  ./gradlew --no-daemon --dependency-verification=strict :compiler-cli:p07BlackBox --rerun-tasks
```

`p07BlackBox` is a required deliverable, not a current capability claim. Reviewer must run the packaged launcher, not Gradle `JavaExec` against development classes. Exact command, exit, JUnit/process counts, artifact paths and signal-platform limitations belong in evidence.

## 10. Dependencies and current state

- P06 implementation/acceptance is required before functional CLI acceptance. Current `compiler-cli` contains only `CompilerCliBoundary`; no command handler or launcher is claimed.
- P08 model does not yet exist. P07 explicit-file mode can ship first; `--project` must reject unavailable/invalid model with exit 4 until it consumes the accepted P08 schema.
- Public diagnostics require resolution of the `JV-*` versus current `JVL-*` implementation drift.
- Full CLI (`format`, migration, inspect), standalone LSP and build-tool project discovery remain later work and cannot be advertised as successful by P07.
