# P00 environment and instruction recon

TASK / AGENT_ID / BASE_REVISION: `P00-RECON-REPAIR` / `/root/p00_repair` / `f4e67788ea0bff07c2ccbfcc9164372974324bc6`

STATUS: **IMPLEMENTED — independent review pending**

CHANGED_PATHS: `.agent/reports/P00-RECON.md`, `.agent/reports/P00-CAPABILITY.md`, `.agent/templates/report-template.md`; raw evidence under ignored `.agent/logs/P00-REPAIR/`.

CONTRACT_CHANGES: none

## Read scope

Read completely: root `AGENTS.md`; plan P00, D1-D3, and G1-G5; independent `.agent/reports/P00-REVIEW.md`. Official OpenAI AGENTS and subagent pages were fetched and checked. Product source and unrelated detailed specification sections were not explored.

## P00-06 — effective instructions

Official loading behavior is global `AGENTS.override.md` else `AGENTS.md`, followed root-to-CWD by one override/AGENTS/fallback file per directory; later files override earlier ones. The combined project-document default cap is 32 KiB.

Observed chain relevant to this app session:

- `/home/langya/.codex/AGENTS.md`: 1,064 bytes; no global override.
- `/home/langya/IdeaProjects/Javelle/AGENTS.md`: 20,098 bytes; no root override and no nested instruction file below the repository root.
- Combined file bytes: 21,162, below 32 KiB by 11,606 bytes. `/home/langya/.codex/config.toml` has no `project_doc_max_bytes` or fallback override, so the documented default applies.
- Runtime evidence: this session received the root file as project instructions through its final directive and follows both distinctive rules (Traditional Chinese communication and `sudo su` shell entry). Therefore no truncation is observed. The 160,543-byte implementation plan is a contract read as a normal repository file, not an automatically loaded AGENTS document.

No global Codex setting was changed.

## P00-07 — environment matrix

| Resource | Observed value | Constraint / disposition |
|---|---|---|
| OS/kernel | Nobara Linux 44 KDE; Linux 7.0.1 x86_64 | Local Linux builds available |
| CPU | Ryzen 7 5700X; 8 cores / 16 threads | Project concurrency remains capped at 4 agents |
| RAM/swap | 31 GiB RAM, 39 GiB swap; 17 GiB available at probe | One heavy build at a time |
| Disk | 303 GiB free on workspace filesystem | Sufficient for local toolchains; recheck before large IDE matrices |
| JDK | OpenJDK/javac 25.0.3 | Java 21 profile/toolchain still P01 work |
| Gradle | 9.6.1, launcher JDK 25.0.3 | Project wrapper/versions not yet accepted |
| Node/npm | Node 22.22.2; npm 10.9.7 | Present |
| pnpm/yarn | Not on root `PATH` | Missing; do not claim unavailable bundled runtime equals system install |
| Git/GitHub CLI | Git 2.55.0; gh 2.96.0 | Present; repository publication separately verified |
| GUI | X11 `DISPLAY=:0`; `xvfb-run` present | GUI and headless harness possible; actual IDEA test remains future work |
| Network | DNS and HTTPS HEAD to official OpenAI docs succeeded | Network enabled at probe; future dependency access must be rechecked |
| Privilege | shell entered through `sudo su`; effective uid 0; `sudo -n true` exit 0 | Required by repository instruction; not generalized to future CI |
| Filesystem sandbox | session permission profile reports unrestricted; direct workspace write succeeded | Current local session only |
| Approval policy | platform reports `never` | No interactive escalation path; avoid actions needing new approval |
| Shell limits | open files 1024; stack 8192 KiB; locked memory 8192 KiB | Record before stress/IDE tests |

Raw commands and outputs: `.agent/logs/P00-REPAIR/environment.txt`, `.agent/logs/P00-REPAIR/instructions.txt`.

## P00-09 — report discipline

`.agent/templates/report-template.md` now encodes the required concise return fields. Recon and capability results are split into non-overlapping short artifacts; raw process/tool output stays in ignored logs. This implementation does not update the coordinator-owned ledgers.

## P00-11 — publication and external conditions

Local development is **feasible** independently of publication. GitHub repository and `main`/`dev` publication were already authorized and independently verified; this does not authorize other external release actions.

| External condition | Current state | Local impact |
|---|---|---|
| GitHub repository/branches | Public repo and branch push verified in P00-GIT/P00-REVIEW | None |
| Maven Central namespace/token | Not provided or verified | Blocks Maven publication only |
| Gradle Plugin Portal key | Not provided or verified | Blocks plugin publication only |
| JetBrains Marketplace token/vendor ownership | Not provided or verified | Blocks IDEA Marketplace publication only |
| Package/release signing keys | Not provided or verified | Blocks signed public release only |
| Website domain/DNS/hosting target | Undecided and no deployment authorization | Blocks public website deployment only |
| Release authority | User authorized Git commit/push and public GitHub repo; no Maven, Portal, Marketplace, website, domain, or GitHub Release authorization observed | Local builds, dry-runs, and distributable artifacts may proceed |

No credentials or secret values were persisted.

TESTS: environment/tool probes completed; direct HTTPS probe succeeded; file-size/config/override checks completed. Commands and full outputs are in `.agent/logs/P00-REPAIR/`.

REVIEW: pending independent reviewer; implementer does not accept its own work.

RISKS_OR_BLOCKERS: actual Java 21 toolchain, IDEA installations, publication credentials, and signing infrastructure remain future checks. They do not block P01 local research.

NEXT_DEPENDENCIES: independent P00 review; coordinator updates ledgers only after acceptance.

REPORT_PATH: `.agent/reports/P00-RECON.md`
