# P06-R04 exact javac diagnostic mapping

- Agent: `/root/p00_recon`
- Base revision: `0b16c5ca2c26be3fd0b9d3079fea4c80a3c0310b`
- Status: IMPLEMENTED / independent review pending
- Requirement: P06-07, with regression coverage for repaired P06-06/P06-10

## Mapping matrix

- Two real javac errors produced from one CRLF Javelle source containing an astral emoji and a Unicode-escaped identifier.
- Exact generated Unicode-code-point ranges are `[180,198)` and `[259,278)`; exact original ranges are `[33,53)` and `[55,81)`.
- A returned `new Missing()` invocation is asserted against its exact original expression range, not merely a containing member.
- Missing-type and generated synthetic class diagnostics resolve to their mapped Javelle owners.
- A diagnostic from a caller-supplied Java source has normalized `Bad.java`, an empty origin list, and no guessed Javelle range.
- Diagnostic codes are stable `JV-JAVAC-ERROR`; the underlying javac code remains in the structured message.

The driver converts javac UTF-16 boundaries to Unicode code points before exact source-map overlap. It keeps generated coordinates for related/fallback inspection. If no valid generated map exists, it reports only the normalized generated filename and generated range.

## Verification

1. JDK 25 Gradle daemon: `:compiler-driver:test :compiler-driver:p06EndToEnd :compiler-driver:p06Release21 --rerun-tasks` — exit 0.
   - Driver: 12 tests, 0 failed/skipped.
   - End-to-end: 12 tests, 0 failed/skipped.
   - Fixed `/opt/jdk21/jdk-21.0.11+10/bin/java` profile: 1 test, runtime assertion 21, 0 failed/skipped.
2. `./gradlew --no-daemon --dependency-verification=strict verifyQuick` — exit 0; 79 tasks; `VERIFY_QUICK_PASS`.

## Review handoff

Independently reproduce exact ranges after changing emoji width, CRLF to LF, and the Unicode escape spelling. Confirm no-map Java diagnostics never acquire a guessed Javelle origin. Re-run transaction fault injection to ensure mapping failures cannot publish output.
