# Resume Javelle

1. Run `sudo su` before every operation, then verify `git status --short --branch` and read `.agent/STATE.md`.
2. Read the final focused R05 section of `.agent/reports/P06-REVIEW.md`; it independently accepts P06.
3. Preserve the P06 boundary: the executable property-to-readable-Java-to-javac slice is implemented; full language, joint compilation, hard process-isolated cancellation and debugger support remain downstream.
4. Preserve unowned `.idea/vcs.xml`. P07 CLI and P08 early IDEA/LSP client packages are READY and may run concurrently with no overlapping write ownership.
5. Keep the 938-entry compiler-core API golden and strict `verifyQuick` green; independently review each stage before unlocking dependants.
