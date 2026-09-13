# P05-R04 varargs parser repair

Agent `/root/p00_repair`. Owned changes: `RecursiveTeyruParser`, exact `P05FrontendTest` regression and this report. Lexer/token files remain untouched.

Status: IMPLEMENTED; independent review pending.

Method parameter parsing now detects the lexer's single longest-match `...` token before typed-parameter validation. It emits exactly one `TY-DEV-0001` on the introducing ellipsis, creates exactly one balanced `UnsupportedSyntaxNode:varargs` covering the parameter construct, suppresses generic parameter syntax cascades, then continues through the method body/class boundary.

Verification: `./gradlew --dependency-verification=strict :compiler-core:spotlessApply :compiler-core:test verifyQuick`, exit 0. Compiler-core ran 56 tests (P05 frontend 11), 0 failures/errors/skipped; aggregate 78 tasks. Evidence `.agent/logs/P05-VARARGS-REPAIR-final.txt`. Public API golden remained unchanged at 708 entries because the repair adds no public API.
