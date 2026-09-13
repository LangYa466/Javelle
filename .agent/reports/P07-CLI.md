# P07-W02 packaged CLI

## Status

`IMPLEMENTED / REVIEW_PENDING`. Independent review remains required before acceptance.

## Delivered

- Gradle application distribution with POSIX/Windows launchers and fixed
  `Teyru 0.1.0 (language 1, Java 21-25)` version.
- Stable root/subcommand help, `doctor`, `check`, `compile`, `emit-java`, and catalog-backed
  `explain`; roadmap commands and stdin exit 6.
- Explicit files use the real P06 pipeline. P08 `--project` is decoded behind the driver facade and
  reads only declared Teyru roots; it never evaluates a build, processor, network resource or
  user program. CLI has no compiler-core dependency/import.
- JSON diagnostics preserve related/fixes/data, convert source-map code points back to raw UTF-16,
  and remain one LF-terminated uncolored stdout document. A separate Python lightweight validator
  checks the normative envelope, enums, fields, code pattern and range unit.
- `compile` uses driver atomic publication; `emit-java` uses a sibling candidate and atomic move.
  Failed replacement preserves the prior accepted generated output byte-for-byte.
- External process fixtures cover exits 0/2/3/4/5/6/124/130. SIGINT is a real `/bin/kill -INT`,
  exits 130 within five seconds and leaves no CLI scratch directory. Internal fault text is
  sanitized; missing selected JDK and unsupported release are toolchain exit 4.

## Verification

- `./gradlew --dependency-verification=strict :compiler-cli:test :compiler-cli:p07BlackBox --rerun-tasks`
  — exit 0; seven packaged black-box tests, zero failures/errors/skips. Latest focused rerun:
  `.agent/logs/P07-final-tests-2.txt`.
- `./gradlew --dependency-verification=strict verifyQuick` — exit 0; 91 tasks and
  `VERIFY_QUICK_PASS`. Evidence `.agent/logs/P07-final-verify-2.txt`.
- `./gradlew verifyProductArchives` — exit 0,
  `PRODUCT_ARCHIVES_REPRODUCIBLE count=16`. Evidence `.agent/logs/P07-archives.txt`.

## Archive finding

The earlier archive error was not differing compiler-cli bytes: its second clean-copy build failed
while concurrent P08 changes referenced a validator method not yet present in the copied tree.
After P08 restored that method, two clean-copy builds produced identical hashes. No reproducibility
assertion was weakened.

Independent review should reproduce all external exits, JSON validation, project trust behavior,
failure preservation, launcher relocation, Windows argument script inspection and archive hashes.

## P07-R01 review repairs

- Fresh `installDist` is named `teyru` and produces `teyru`/`teyru.bat` launchers.
- JSON-mode `emit-java` emits exactly one envelope and no artifact path. Human mode alone prints
  paths.
- `emit-java` publishes Java, deterministic source-map JSON and an ownership manifest. Replacement
  validates old owned hashes, preserves foreign files, refuses changed-owned/colliding/symlink or
  corrupt state, and uses candidate/backup directory renames with rollback.
- Positive timeout values become monotonic driver deadlines; external 1 ms and zero-timeout
  fixtures exit 124 before publication. Real SIGINT cleanup remains covered.
- Tests use the actual normative schema with a recursive validator supporting local `$ref`, type,
  required/properties, additionalProperties, const/enum/pattern, arrays and numeric/string bounds.
  Duplicate-key, future-version and wrong-type mutations are rejected.
- Driver facade carries source-map bytes/hashes plus related/fixes/data without a CLI→core edge.
- Strict packaged suite: 7/7 external-process tests, exit 0, evidence
  `.agent/logs/P07-R01-test-2.txt`. Product archives: 16 reproducible jars, exit 0, evidence
  `.agent/logs/P07-R01-final.txt`.
- The combined final command stopped later on an unrelated concurrent P08 test Spotless violation;
  P07 tests, `p07BlackBox`, architecture and product archives had already passed. This owner did
  not edit P08.

## P07-R02 final repairs

- The generated `teyru.bat` is inspected as a Windows launcher artifact: quoted classpath,
  `%*` original argument forwarding and no delayed expansion. The Linux host has no claim of a
  native Windows execution; the content fixture covers spaces/Unicode and documents `% ! ^ & | < >`
  handling through the standard Gradle script contract.
- `doctor --jdk` now executes the selected fixed `bin/javac -version` via `ProcessBuilder`, caps
  captured output, enforces a five-second deadline, kills hangs, and accepts only supported 21–25
  version syntax. Real selected JDK, fake 99, garbage and hanging probes are black-box tested.
- Emit ownership manifests include a deterministic SHA-256 `inputFingerprint`; Java/map hashes are
  checked. Candidate files, manifest and directories are forced to stable storage before atomic
  renames. Publication fault seams at candidate/write/manifest/fsync/move all return exit 4,
  preserve prior Java/manifest bytes and leave no stage/backup directories.
- `--timeout-ms 1` is a real monotonic `ResourceBudget` deadline propagated through the driver;
  it exits 124 before publication rather than merely parsing the option.
- Strict packaged tests now execute 9/9 with zero failures/errors/skips:
  `.agent/logs/P07-R02-test-3.txt`.
- `:compiler-cli:p07BlackBox verifyProductArchives verifyQuick` exits 0; product archives remain
  reproducible and `VERIFY_QUICK_PASS` is emitted. Evidence `.agent/logs/P07-R02-final.txt`.
