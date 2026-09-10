# Resume Javelle

1. Run `sudo su` before every operation, then verify `git status --short --branch` and read `.agent/STATE.md`.
2. Read the final focused section of `.agent/reports/P01-REVIEW.md`; P01 is independently accepted and P02 is READY.
3. Preserve the P01 boundaries: all Lombok/LSP application support remains NOT_IMPLEMENTED, non-library DTO work remains classified for later implementation, and Plugin Verifier remains deferred to P11's real plugin ZIP.
4. Preserve unowned `.idea/vcs.xml`. Assign a single build owner for root build/version/schema files before starting P02 and respect the two-writer/one-heavy-build limits.
5. Implement and independently review P02 before unlocking P03.
