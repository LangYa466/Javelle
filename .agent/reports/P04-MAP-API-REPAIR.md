# P04-R03 — Source-map/API repair status

- Agent: `/root/p00_recon`
- Base revision: `82c264d007fd7114e468f8ef2c855a6d4f6718e2`
- Requirements: P04-08/09/11/12
- Status: `IMPLEMENTED`; pending independent review
- Evidence: `.agent/logs/P04-R03-targeted.txt`

Implemented in the source-map ownership area:

- immutable reverse per-source interval indexes replace `originalAt`'s linear scan;
- reverse lookup covers overlaps and exact zero-width anchors with deterministic de-duplication/order;
- versioned schema constructor rejects unsupported schema versions;
- generated artifact fingerprint map validates URI and SHA-256 through `SourceId` and composition rejects an intermediate fingerprint mismatch;
- source-map segments reject mixed/non-code-point units;
- expanded and synthetic segments require a nonempty `GeneratedMemberOrigin`; synthetic mappings continue to require a reason;
- tests add reverse zero-width, schema mismatch, fingerprint mismatch, unit mismatch and synthetic-helper/member-origin cases.
- deterministic versioned `SourceMapCodec` round-trips schema, fingerprints, all original locations, nodes, and generated-member origins; it rejects schema, count-budget, duplicate URI, enum/range, and trailing-data violations;
- correct-fingerprint composition and mismatch paths, overlapping priority/kind/size ordering, and exact property field/getter/setter intervals are exercised;
- coordinate goldens cover BOM, text-block CRLF, Chinese, emoji, every UTF-8-byte/code-point boundary, invalid byte interiors, and escaped surrogate interiors;
- the API golden is the SHA-256 of all public types plus declared public/protected constructors, methods, and fields. Both additions and removals/signature changes alter the digest; all compiler-core classfiles are constant-pool scanned before the public-type filter.

The first final attempt was blocked during test compilation by an unrelated concurrently added invalid compound `var` declaration. Its owner repaired it without this package crossing ownership. The strict rerun `:compiler-core:test :compiler-core:spotlessCheck` then completed successfully (exit 0); see `P04-R03-targeted-final.txt`.

Strict `:compiler-core:spotlessApply :compiler-core:test verifyQuick` completed successfully (exit 0, 78 actionable tasks). A shared `NodeIndex` regression exposed by the full suite was repaired by preserving the `LinkedHashMap` traversal order instead of using order-unspecified `Map.copyOf`.

No asymptotic benchmark is claimed. Complexity evidence is structural: both directions use immutable interval trees and the functional suite retains the 10,000-segment case. Independent acceptance remains required.
