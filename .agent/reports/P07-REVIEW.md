# P07 independent CLI review

TASK / AGENT_ID / BASE_REVISION: `P07-REVIEW` / `/root/p00_review` / current shared `dev` candidate

STATUS: **FAILED — REJECT P07**

| ID | Decision | Independent black-box result |
|---|---|---|
| P07-01 | **FAIL** | `installDist` succeeds, but produces only `bin/compiler-cli` and `bin/compiler-cli.bat`; the required installed executable `teyru` does not exist. Version/help content is stable only when invoking the wrongly named launcher. |
| P07-02 | **FAIL** | Check/compile use `CliCompilerFacade` in compiler-driver and P08 project JSON is read without build/network execution. `emit-java` publishes only `.java`: it does not publish the contracted source maps or ownership manifest. |
| P07-03 | PASS | Packaged-process tests exercise frozen 0/2/3/4/5/6/124/130 codes; manual usage, compilation, internal and unavailable-command results matched their assigned codes. This does not imply lifecycle correctness for P07-09. |
| P07-04 | **FAIL** | Semicolon JSON has structured RAW_UTF16 source/range/fix/data and no ANSI. On successful `emit-java --diagnostics json`, stdout contains an absolute emitted path followed by JSON, so it is not one JSON document. Test validation is a lightweight field subset, not validation against the normative schema or duplicate/type/future/canonical negatives. |
| P07-05 | **FAIL** | Space/Traditional-Chinese/emoji arguments work. Machine stdout separation fails for JSON emit and leaks the absolute checkout path; quiet/no-color/locale/timezone/redirected-stream and Windows quoting matrices are not independently covered. |
| P07-06 | PASS | Independent packaged-process loop explained all 20 pinned catalog codes with exit 0; unknown code exited 2 and produced no fabricated stdout explanation. |
| P07-07 | PASS | Healthy doctor, missing selected JDK, unsupported release, invalid input and source compilation errors are separated into success/toolchain-or-I/O/compilation outcomes. |
| P07-08 | **FAIL** | Driver compile preserves accepted output on compilation failure. `emit-java` refuses any existing output rather than atomically replacing an accepted owned output, and it has no ownership manifest; unwritable/write-fault/collision/symlink failure preservation is not proven. |
| P07-09 | **FAIL** | Real SIGINT fixture and zero-timeout injection cover 130/124 cleanup. A manual `--timeout-ms 1` operation exited 0: every positive timeout value is parsed but never enforced, so real deadline cancellation is absent. |
| P07-10 | PASS | Help lists only P07 commands; format/migrate/inspect/teyru-lsp each exit 6 when directly invoked. |
| P07-11 | **FAIL** | Tests do launch the packaged process and inspect streams/files, but they target `compiler-cli`, do not independently schema-validate real output, and miss the invalid JSON emit path; the required executable integration is therefore incomplete. |
| P07-12 | **FAIL** | Reviewer successfully ran the distribution from a non-repository directory with spaces/中文/emoji, but only via `compiler-cli`; the declared `teyru` packaged launcher is absent, so the required consumer invocation cannot be reproduced. |

## Executed evidence

- JSON syntax checks for `exit-codes.json` and `diagnostics-v1.schema.json`: exit 0.
- Java 25 strict `:compiler-cli:installDist :compiler-cli:test :compiler-cli:p07BlackBox --rerun-tasks`: exit 0; seven tests, zero failures/errors/skips; 20 Gradle tasks executed. Log `.agent/logs/P07-REVIEW/gradle-tests.log`.
- Java 25 strict `verifyQuick`: exit 0; 91 tasks and `VERIFY_QUICK_PASS`. Log `.agent/logs/P07-REVIEW/verifyQuick.log`.
- Non-repository packaged-process commands: version/help/doctor/check/compile/emit/explain ran from `.agent/logs/P07-REVIEW/nonrepo space 中文😀`. Check exit 0; semicolon check exit 3 with `TY-SYN-0001`; compile/emit exit 0; unknown explain exit 2.
- `emit-java --diagnostics json` stdout begins with `/home/langya/IdeaProjects/Teyru/.../User.java` and only then emits the envelope. `--timeout-ms 1` exit 0. These are direct counterexamples to P07-04/05/09.
- All 20 diagnostic catalog entries were independently passed to the installed process; 20/20 exited 0.
- Fresh product tree contains `compiler-cli`/`.bat`, five jars, and no `teyru` launcher.

Final result: PASS `P07-03/06/07/10`; **FAIL `P07-01/02/04/05/08/09/11/12`**. Fix the application name, emit source-map/manifest atomically, keep JSON stdout pure, enforce positive deadlines, and expand packaged negative/schema/failure tests before acceptance.

REVIEW: independent `/root/p00_review`; implementer did not self-approve. `.idea/vcs.xml` remained excluded and untouched.

---

## Final focused review R02 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P07-REVIEW-R02` / `/root/p00_review` / final repaired shared `dev` candidate

STATUS: **VERIFIED — ACCEPT P07**

| ID | Decision | Final independent result |
|---|---|---|
| P07-01 | PASS | Clean distribution contains only the correctly named POSIX `teyru` and Windows `teyru.bat` launchers; version and root/subcommand help are stable. Windows artifact/classpath/`%*` quoting is inspected at P07; native Windows execution remains explicitly deferred to P52. |
| P07-02 | PASS | Check/compile/emit share compiler-driver; emit publishes Java, source map and ownership manifest without classfiles, and P08 project consumption performs no build/network evaluation. |
| P07-03 | PASS | Packaged processes cover exact exits 0/2/3/4/5/6/124/130 and preserve output on failure. |
| P07-04 | PASS | Machine stdout is one canonical schema-valid uncolored JSON document with structured RAW_UTF16 source/range/related/fixes/data; recursive major/type/duplicate negatives pass. |
| P07-05 | PASS | stdout/stderr, quiet/no-color, locale/timezone and spaces/中文/emoji arguments pass. Generated Windows script retains quoted classpath and `%*` forwarding without delayed expansion; this is artifact-level P07 evidence, not a native-Windows claim. |
| P07-06 | PASS | Every pinned catalog code explains and unknown code fails without fabricated text. |
| P07-07 | PASS | Doctor executes selected `javac -version`, bounds it to five seconds and distinguishes valid 21–25, fake 99, garbage, hang and missing toolchain; source compilation remains exit 3 rather than toolchain exit 4. |
| P07-08 | PASS | Emit manifest now includes exact 64-hex `inputFingerprint`, Java/source-map hashes and kinds. Candidate files and directories are forced; candidate/write/manifest/fsync/move faults preserve accepted Java/manifest byte-identically and leave no staging/backup residue. Foreign/changed/symlink/unwritable negatives pass. |
| P07-09 | PASS | A real 1 ms deadline exits 124 before publication; real SIGINT exits 130 with no scratch residue. |
| P07-10 | PASS | Help advertises only P07 commands; recognized later commands exit 6. |
| P07-11 | PASS | Nine tests execute the clean installed launcher as external processes and inspect JSON, streams, filesystem, signal, doctor subprocess and fault paths. |
| P07-12 | PASS | Reviewer independently ran the packaged launcher from a non-repository Unicode/space directory using declared JDK and fixtures. |

### Reproduced evidence

- Java 25 strict clean `:compiler-cli:clean :compiler-cli:installDist :compiler-cli:test :compiler-cli:p07BlackBox --rerun-tasks`: exit 0; nine tests, zero failures/errors/skips; 21 tasks executed. `.agent/logs/P07-REVIEW/r3-tests.log`.
- Java 25 strict `verifyQuick`: exit 0; 91 tasks and `VERIFY_QUICK_PASS`. `.agent/logs/P07-REVIEW/r3-verifyQuick.log`.
- Strict `verifyProductArchives`: exit 0; `PRODUCT_ARCHIVES_REPRODUCIBLE count=16`. `.agent/logs/P07-REVIEW/r3-archives.log`.
- Independent prior fake `javac` exiting 99 now yields one JSON `TOOLCHAIN_OR_IO_ERROR`, exit 4. `.agent/logs/P07-REVIEW/r3-fake99.json`.
- Independent nonrepo JSON emit: exit 0, one SUCCESS envelope; manifest contains input fingerprint plus Java/source-map entries and exact hashes. `.agent/logs/P07-REVIEW/r3-emit.json`.
- Executed five-phase loop verifies candidate/write/manifest/fsync/move rollback; source inspection confirms `FileChannel.force(true)` on every file/directory and parent-directory forcing around atomic replacement.

Final result: PASS `P07-01..12`; **ACCEPT P07**. Windows native process execution is not claimed by this acceptance and remains a P52 cross-platform release gate.

REVIEW: independent `/root/p00_review`; implementer did not self-approve. `.idea/vcs.xml` remained excluded and untouched.

---

## Second independent full review R01 — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P07-REVIEW-R01` / `/root/p00_review` / repaired shared `dev` candidate

STATUS: **FAILED — REJECT P07**

| ID | Decision | Current independent result |
|---|---|---|
| P07-01 | **FAIL** | Clean distribution now correctly contains only `bin/teyru` and `bin/teyru.bat`; POSIX version/root/subcommand help pass from a non-repository path. The required Windows launcher argument/quoting fixture is still absent, so both launchers are not independently verified. |
| P07-02 | PASS | Check/compile/emit use the compiler-driver facade. Emit now publishes Java, source map and ownership metadata with no class file; P08 project input is consumed without evaluating Gradle/network/user code. Manifest validity is separately blocking P07-08. |
| P07-03 | PASS | External packaged processes cover exits 0/2/3/4/5/6/124/130 with stable precedence and sanitized internal failure. |
| P07-04 | PASS | JSON emit is now exactly one LF-terminated document with no path/banner/ANSI; real semicolon diagnostics retain RAW_UTF16 source/range/related/fixes/data. Recursive schema validation plus major/type/duplicate negatives pass. |
| P07-05 | **FAIL** | stdout/stderr, quiet/no-color, locale/timezone and spaces/中文/emoji pass. No Windows `.bat` quoting/argument fixture is executed or statically asserted, so the explicit cross-platform quoting requirement remains unverified. |
| P07-06 | PASS | All 20 catalog codes explain successfully; unknown codes remain usage errors without fabricated output. |
| P07-07 | **FAIL** | Missing path and unsupported release are distinct, but `doctor --jdk` only checks whether `<jdk>/bin/javac` is executable. A reviewer-created fake executable that exits 99 was reported `SUCCESS`, exit 0; selected javac version/health/release support is not actually probed. |
| P07-08 | **FAIL** | Compile and emit replacement preserve foreign/changed-owned files and reject symlink/unwritable targets. Emit's ownership JSON is not the valid P06 manifest required by contract: it omits `inputFingerprint`. The separate publisher performs atomic moves but no file/manifest/directory fsync and has no write-phase fault seam/test proving rollback after the first rename. |
| P07-09 | PASS | `--timeout-ms 1` now exits 124 through a real deadline budget; real SIGINT exits 130 within five seconds, and tests verify no CLI scratch residue. |
| P07-10 | PASS | Help advertises only P07 commands; all recognized later commands exit 6. |
| P07-11 | PASS | Seven tests run the clean installed launcher as external processes, recursively validate JSON, inspect both streams and filesystem outputs, and cover signal/timeout/failure paths. |
| P07-12 | PASS | Reviewer independently ran `bin/teyru` from a non-repository directory containing spaces, Traditional Chinese and emoji, with only the distribution/JDK and fixture arguments. |

### Reproduced evidence

- Java 25 strict clean `:compiler-cli:clean :compiler-cli:installDist :compiler-cli:test :compiler-cli:p07BlackBox --rerun-tasks`: exit 0; seven tests, zero failures/errors/skips; 21 tasks executed. `.agent/logs/P07-REVIEW/r2-tests.log`.
- Java 25 strict `verifyQuick`: exit 0; 91 tasks, `VERIFY_QUICK_PASS`. `.agent/logs/P07-REVIEW/r2-verifyQuick.log`.
- Strict `verifyProductArchives`: exit 0; 16 reproducible product archives. `.agent/logs/P07-REVIEW/r2-archives.log`.
- Non-repository manual `emit-java --diagnostics json`: exit 0, one JSON line, recursive schema validator exit 0, no absolute-path match; output has Java, `.map.json`, and manifest. `check --timeout-ms 1`: exit 124.
- Fake selected JDK negative: executable `fake-jdk/bin/javac` that exits 99; `teyru doctor --jdk <fake> --format json` returned `status=SUCCESS`, exit 0.
- Static manifest inspection: CLI manifest writes `schemaVersion`, `generatorVersion`, and files, but no P06-required `inputFingerprint`; publish implementation contains no `FileChannel.force`/fsync and no publish fault injection.

Final result: PASS `P07-02/03/04/06/09/10/11/12`; **FAIL `P07-01/05/07/08`**. Add Windows launcher quoting evidence, execute/inspect the selected javac, and make emit publication use a complete P06 manifest plus durable fault-tested replacement before acceptance.

REVIEW: independent `/root/p00_review`; implementer did not self-approve. `.idea/vcs.xml` remained excluded and untouched.
