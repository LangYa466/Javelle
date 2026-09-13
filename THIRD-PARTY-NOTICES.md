# Third-party notices

The Teyru compiler itself has no third-party runtime dependencies: it is written
in Go using only the standard library, and the generated programs link only
against the C standard library and the Teyru runtime in `internal/runtime/src`.

- **Go standard library** — Copyright the Go Authors, BSD-3-Clause. Used to build
  the compiler.
- **libm / libc / libpthread** — the C runtime that the generated natives link
  against, provided by the host system under its own license.
- **clang / LLVM** (optional) — the default C backend. Clang is licensed under
  Apache-2.0 with the LLVM exception; GCC may be used instead under GPLv3.

`go.mod` lists no external modules, so no module notices are required.
