# P03-W01 — Java SE 25 feature inventory

- Agent: `/root/p00_recon`
- Base revision: `82c264d007fd7114e468f8ef2c855a6d4f6718e2`
- Requirement: P03-07; supports P03-01/02/03/06/09/10/12
- Status: `IMPLEMENTED` after P03-R02 chapter-completeness repair; pending independent semantic/JLS review
- Artifact: `spec/java-se-25-inventory.json`
- Evidence: `.agent/logs/P03-JAVA-INVENTORY/`

The inventory establishes 37 stable requirement IDs across 19 categories and links each to normative JLS 25 sections. A separate `chapterCoverage` manifest now enumerates chapters 1 through 19 in strict order. Every chapter has an official source anchor, resolvable requirement cross-references, and positive/negative test obligations. Every feature records syntax/semantic preservation, Java 25 disposition, Java 21 profile disposition, and mandatory fixtures.

P03-R02 adds material coverage previously missing from the coarse feature list: Chapter 1 fixes the final/non-preview scope and precedence of normative clauses; Chapter 2 freezes JLS grammar notation and requires closure of every imported `JAVA_*` category; Chapter 16 explicitly preserves definite-assignment/unassignment across locals, blank finals, constructors, loops, switch and patterns, with `val` following final-local rules; Chapter 19 requires production-to-fixture coverage for the complete non-preview syntax and rejects dangling/undefined/preview-only production imports.

Three feature families are explicitly rejected by the Java 21 profile: module-import declarations, unnamed variables/patterns, and compact source files/instance main methods. Flexible constructor bodies carry a separate Java 21 legacy-first-statement obligation. Preview primitive patterns are explicitly excluded from the Java 25 non-preview baseline. Javelle-only changes—newline statement termination, forbidden syntax semicolons, basic-for colons, enum boundary colon, resource newlines, and `val`—are identified as deltas rather than attributed to Java.

Official Oracle JLS 25 and JLS 21 index pages were captured on 2026-09-11. SHA-256:

- JLS 25 index: `607a803eac612407f9133ca1b17211c0c9af66ff2da64bd632763e1c0a85c74b`
- JLS 21 index: `b1f9fee02fd33408018f203b28b4fe2d4100bf7aa9b2eff92ab215f8ac3a8761`

Sources:

- https://docs.oracle.com/javase/specs/jls/se25/html/index.html
- https://docs.oracle.com/javase/specs/jls/se21/html/index.html
- https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/module-summary.html

Validation executed with `jq`: JSON exit 0; exact ordered chapters `[1..19]` exit 0; unique IDs exit 0; chapter anchor/obligation/cross-reference integrity exit 0; all feature JLS/positive/negative/profile obligations exit 0. Result: 37 features, 19 chapters, 19 categories. See `chapter-validation.txt`. The historical initial summary-only jq error remains documented in the earlier log and did not affect the artifact.

This package defines obligations only. No compiler feature, compatibility status, or passed fixture is claimed. P03 spec owners must reference these IDs from normative grammar/semantics; later compiler and QA packages must replace each fixture obligation with executable evidence before marking it verified.
