# P10-W01 — independent LSP minimal contract

TASK / AGENT_ID / BASE_REVISION: `P10-W01` / `/root/p00_recon` / shared `dev` worktree

STATUS: CONTRACT_FROZEN — implementation and independent black-box verification pending

Canonical machine contract: `spec/lsp/p10-minimal-contract.json`.

## Boundary and immutable decisions

`teyru-lsp --stdio` is a standalone process. `language-server` owns JSON-RPC transport/lifecycle and calls `language-tooling`; parsing and coordinate truth remain in `compiler-core`, workspace/JDK/classpath truth comes from the validated `workspace-model`, and no IntelliJ class may enter the server. stdout contains framed protocol bytes only. Human logs, warnings and bounded stack traces use stderr or a configured local log.

The fixed baseline is LSP 3.18 and the pinned LSP4J 1.0.0 binding. This is not a version handshake. The authoritative 95-method inventory remains `compatibility/lsp-methods.json`; P10 may change support/advertisement status only for behavior proven by its black-box tests. Library DTO presence never constitutes handler support.

P10 advertises exactly UTF-16 positions, incremental open/change/close sync, push diagnostics, hover and non-resolving basic completion. UTF-16 is also the fallback when the client omits `general.positionEncodings`. Definition and every advanced P30–P36 family remain unadvertised. Pull diagnostics (`diagnosticProvider`) are specifically not advertised because P10 implements push diagnostics.

## Transport and lifecycle

Frames use ASCII headers, required case-insensitive `Content-Length`, CRLF-CRLF delimiter and strict UTF-8 bodies; length counts UTF-8 bytes, not characters. The implementation must buffer fragmented frames, drain coalesced frames and bound header/body sizes before allocation. Missing, duplicate, negative, non-decimal, excessive or truncated length and invalid UTF-8 fail deterministically without writing logs to stdout.

State is `PRE_INITIALIZE → INITIALIZING → RUNNING → SHUTDOWN → EXITED`. Duplicate initialize is `-32600`; ordinary pre-initialize requests are `-32002`; unknown requests are `-32601`; unknown notifications are ignored and logged; requests after shutdown are `-32600`. `shutdown` responds with JSON null. `exit` after shutdown returns process code 0, otherwise 1. Parse/params/internal/cancel/content-modified errors use the standard numeric codes recorded in the machine contract. Every request ID receives at most one terminal response.

## Documents, snapshots and diagnostics

Each open/change produces an immutable snapshot keyed by normalized URI, strictly increasing document version, workspace model fingerprint and overlay analysis fingerprint. Incremental edits are applied in array order to the prior version. LSP ranges are zero-based UTF-16 code units; the compiler boundary explicitly converts to/from Unicode-code-point coordinates and preserves CRLF, CJK and surrogate-pair emoji boundaries. Invalid ranges reject the whole notification without partial mutation.

Stale versions are ignored and cannot publish. `$/cancelRequest` is cooperative at parse/query boundaries; a cancellation race may finish normally or return `-32800`, never both, and an old task cannot overwrite a newer snapshot. Syntax diagnostics publish against the original `.teyru` URI with document version. A successful fix and `didClose` each publish an empty diagnostic array to clear stale UI state.

Unsaved text is the parser input. Hover covers real declarations/locals/String/native property data; completion covers contextual Teyru keywords plus String/current-type property symbols obtained from the shared semantic/workspace model. Empty or fixed fake results do not satisfy the contract.

## Workspace and trust

The only P10 discovery input is `initializationOptions.teyru.workspaceModelUri`, a local `file:` URI to the P08 schema. Decode, limit, path, fingerprint and trust validation occurs without evaluating Gradle, running processors/user code, executing downloaded tools or accessing network. Invalid/stale models return bounded structured errors and cannot seed symbol state. Reload changes the workspace fingerprint and invalidates dependent snapshots.

## Requirement coverage and executable acceptance

| P10 ID | Contract evidence | Required external evidence |
|---|---|---|
| 01 | standalone command and module boundary | launch distribution with no IntelliJ jars/classes |
| 02 | framing, states, IDs and error table | vectors BB-01/02/03/08/11 |
| 03 | immutable unsaved incremental snapshots | BB-04/05/06 |
| 04 | exact capability allowlist/denylist | inspect initialize response and invoke advertised handlers |
| 05 | original URI/range and clear rules | BB-04/05 plus mapped parser fixture |
| 06 | validated P08 workspace symbols | String and native-property hover/completion fixtures; untrusted negative |
| 07 | stdout purity | parse every stdout byte as frames while forcing warnings/errors |
| 08 | version/cancellation publication guard | BB-06/07 with deterministic analysis delay hook |
| 09 | malformed/EOF/unknown/post-shutdown | BB-03/08/09 |
| 10 | external process client only | execute black-box script against installed launcher |
| 11 | repeat lifecycle/resource cleanup | BB-10 with PID descendants, handles and post-exit CPU check |
| 12 | environment independence | reviewer runs BB-12 on JVM host without IDEA installation |

The black-box client must write raw framed bytes to process stdin and parse raw stdout independently; directly invoking a handler or sharing the server framing parser is insufficient. It must support byte fragmentation/coalescing, request deadlines, stderr capture, explicit process cleanup and transcript redaction. No test may accept unframed stdout, skip on malformed input or advertise a handler merely because it returns an empty collection.

## Later inventory boundary

P10 is a minimal closed loop, not “full LSP.” Dynamic registration, workspace folders/config/watch, pull/workspace diagnostics, definition and all navigation/refactor/format/token/hierarchy/progress families stay `NOT_IMPLEMENTED` and `advertised=false` until their later phase tests pass. Color, notebook and inline-value remain the three reviewed N/A candidates in the 95-method inventory; P10 does not broaden N/A classifications.

## Contract validation

- `jq -e` validates the canonical JSON.
- The contract contains 12 unique black-box vector IDs and maps all P10-01 through P10-12.
- No compiler/server/tooling/build file was changed by this contract package.
