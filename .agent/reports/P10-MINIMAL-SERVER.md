# P10-W02 minimal standalone language server

TASK / AGENT_ID / BASE_REVISION: `P10-W02` / `/root/p00_recon` / shared `dev` worktree

STATUS: IMPLEMENTED — independent reviewer must run the complete P10-BB matrix

## Implementation

- `language-protocol` contains a bounded, duplicate-key-rejecting JSON codec with no compiler/platform dependency.
- `language-tooling` owns immutable URI/version/text/fingerprint snapshots, atomic full/ranged edits, strict UTF-16 boundaries including surrogate interiors and CRLF, stale-version rejection, compiler-driver-backed diagnostics, snapshot publication guards, hover and minimal completion.
- `language-server` builds the standalone `javelle-lsp --stdio` distribution. It implements bounded Content-Length framing, fragmented/coalesced input, lifecycle/error states, exact minimal initialize capabilities, open/change/close, asynchronous version-guarded push diagnostics/clear, hover/completion and file-only validated workspace-model initialization. Transport/log failures remain off stdout.
- Workspace initialization rejects non-file URIs before access and decodes through the P08 bounded validator; it never evaluates Gradle, processors, user code or network.

## Executed evidence

- `./gradlew --no-daemon --dependency-verification=strict :language-protocol:spotlessApply :language-tooling:spotlessApply :language-server:spotlessApply :language-tooling:test :language-server:test --rerun-tasks` — exit 0; tooling 1/1, external server 3/3, zero skips.
- External process cases include byte-at-a-time fragmentation, coalesced frames, initialize capability denylist, unsaved CJK/emoji/CRLF input, hover/completion, stale edit suppression, diagnostic clearing, malformed JSON framed error, malformed/duplicate/oversize/truncated headers, partial EOF, ten restart cycles, untrusted network workspace rejection, normal and early exit codes.
- `verifyQuick` reached architecture, compiled boundaries, governance and reproducible product archives, then exit 1 at unrelated active `gradle-plugin` Spotless violations (`ExportJavelleWorkspaceModel`, `GenerateJavelleJava`, `GenerateJavelleParameters`, `JavellePlugin`). No owned LSP file failed.

## Reviewer focus

Run each of the 12 contract vectors as separately reported assertions, especially deterministic slow-analysis cancellation (`$/cancelRequest`), delayed old-publication suppression, valid local workspace symbols, unknown/post-shutdown requests, process descendants/handles/CPU, and a host without IDEA. Basic completion intentionally advertises no resolve support; all advanced capabilities remain absent.
