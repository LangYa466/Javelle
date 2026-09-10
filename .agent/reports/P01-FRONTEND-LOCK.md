# P01-R2B tracked frontend dependency lock

TASK / AGENT_ID / BASE_REVISION: `P01-R2B` / `/root/p00_repair` / working tree based on `eb75e78a6772b62271b2b6affd127cc16ee54d42`

STATUS: **IMPLEMENTED — independent review pending**

CHANGED_PATHS: `config/frontend/package.json`, `config/frontend/package-lock.json`, `config/frontend/generate-components.jq`, `config/frontend/README.md`, `third-party/frontend-components.json`, `.agent/reports/P01-FRONTEND-LOCK.md`; ignored evidence under `.agent/logs/P01-FRONTEND-LOCK/` and `.agent/tmp/P01-FRONTEND-LOCK/`.

CONTRACT_CHANGES: none. `config/frontend` is a dependency fixture, not a website implementation.

REQUIREMENTS: `P01-11` repair and reproducible subset of `P01-12`.

## Durable lock

- `package.json` fixes Node `22.22.2`, npm `10.9.7`, `astro` `7.3.2`, and `@astrojs/starlight` `0.42.0`; no ranges or lifecycle scripts.
- Tracked npm lockfileVersion 3 contains 373 platform-inclusive package entries with exact versions, registry sources, integrity hashes, and license metadata.
- `third-party/frontend-components.json` contains the same 373 entries as `{path,name,version,integrity,license,source}` sorted by package-lock path.
- `generate-components.jq` is the single deterministic transformation. Regeneration followed by byte comparison verifies the tracked inventory; it does not query the network.
- No `node_modules` directory is tracked or present below `config/frontend`.

The component inventory records upstream package metadata; it is not a P51 conclusion about the license obligations of the eventual static output.

## Executed acceptance

| Check | Exit | Result |
|---|---:|---|
| JSON parse of manifest, lock, component inventory | 0 | valid |
| Exact manifest values and lockfileVersion | 0 | Node/npm/Astro/Starlight pins and v3 lock match |
| Component completeness | 0 | 373 lock entries = 373 manifest entries; required fields are strings |
| Deterministic regeneration + `cmp` | 0 | byte-identical |
| Isolated `npm ci --ignore-scripts --audit=false --fund=false` | 0 | added 281 Linux x64 packages |
| Alter Astro integrity to fake SHA-512, fresh cache, rerun `npm ci` | 1 | failed with `EINTEGRITY` and reported actual pinned Astro digest |
| `node_modules` tracked/present under baseline | 1/no matches | clean |

Bounded logs: `.agent/logs/P01-FRONTEND-LOCK/npm-ci.txt`, `npm-tampered.txt`, `summary.txt`, and `acceptance.txt`.

REVIEW: pending independent reviewer; implementer does not approve its own package.

RISKS_OR_BLOCKERS: macOS, Windows, and Linux arm64 execution remain P52/P54 gates as already documented. P51 must review LGPL/MPL/compound-license components against distributed website bytes. These do not weaken the exact tracked lock.

NEXT_DEPENDENCIES: independent reviewer regenerates the inventory, performs isolated install and tamper negative, then redecides P01-11.

REPORT_PATH: `.agent/reports/P01-FRONTEND-LOCK.md`
