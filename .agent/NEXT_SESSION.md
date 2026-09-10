# Resume Javelle

1. Run `sudo su` before every operation, then verify `git status --short --branch` and read `.agent/STATE.md`.
2. Read the authoritative repair decision at the end of `.agent/reports/P00-REVIEW.md`; P00 is rejected, not accepted.
3. Treat the final `P00-04 zero-write follow-up` as PASS; the attributable `/root/p00_recon` return was independently verified with an unchanged worktree.
4. For `P00-10`, verify a genuine platform close operation. The current collaboration tools expose interrupt but no close/delete operation. If the specification is unchanged, this is the sole P00 failure and P00/P01 remain blocked until that platform capability exists.
5. Rerun the independent P00 stage-exit decision after the close requirement is satisfied. Only an accepted review may unlock the first READY P01 work package.
