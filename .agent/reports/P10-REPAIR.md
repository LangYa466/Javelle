# P10-R01 LSP repair

- Agent: `/root/p00_recon`; base revision: `307fba6`.
- Scope: `language-protocol`, `language-tooling`, `language-server`, and the P10 rows of `compatibility/lsp-methods.json`.
- Result: IMPLEMENTED; independent review remains required before ACCEPTED.

## Closed review findings

- FAIL02/03: strict UTF-8 decoding, distinct pre-initialize/initializing/running/shutdown states, JSON-RPC parse/invalid-request/invalid-params/internal error separation, and bounded framing.
- FAIL04/06: immutable normalized-URI snapshots carry document, workspace, and overlay fingerprints; a validated workspace model is retained. Hover/completion are derived from the compiler-produced Java AST/JDK symbols and return empty for unknown symbols.
- FAIL05/08/09: 95-method inventory now records only 12 verified handlers and six advertised capabilities; exact diagnostic publish-to-clear, malformed transport, UTF-16 emoji/CRLF, stale versions, cancellation, and shutdown are covered.
- FAIL11: ten separate-process restart cycles assert bounded exit, dead process/descendants, and released Linux `/proc/<pid>/fd` handles.

## Evidence

- `./gradlew --no-daemon --dependency-verification=strict :language-protocol:spotlessApply :language-tooling:spotlessApply :language-server:spotlessApply :language-tooling:test :language-server:test --rerun-tasks`: exit 0; tooling 2/2, server black-box 12/12; `.agent/logs/P10-R01-strict.txt`.
- Restart/resource rerun `:language-server:test --rerun-tasks`: exit 0, 12/12; `.agent/logs/P10-R01-restart-evidence.txt`.
- Inventory jq gate: exit 0; 95 rows, 12 VERIFIED, six advertised, zero advertised NOT_IMPLEMENTED; `.agent/logs/P10-R01-inventory.txt`.
- `./gradlew --no-daemon --dependency-verification=strict verifyQuick`: exit 0; `.agent/logs/P10-R01-verifyQuick.txt`.
- `:language-server:distZip`: exit 0; `language-server/build/distributions/javelle-lsp-0.1.0-SNAPSHOT.zip`; `.agent/logs/P10-R01-archive.txt`.

## Limits / next dependency

- This is the P10 minimal contract only; semantic refactors, references, rename, and IDEA behavior remain later packages and are not advertised.
- Required next action: independent reviewer reruns the black-box vectors and attempts cancellation/lifecycle counterexamples before P10 acceptance.
