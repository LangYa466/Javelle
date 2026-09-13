# ADR-0007: Minimal disclosed runtime and compatibility annotations
Status: Accepted design; P03-11/P25-06; implementation NOT_IMPLEMENTED.

Decision: ordinary output has no Teyru runtime. A feature needing a helper uses a separately versioned, disclosed tiny runtime. Strict metadata compatibility may use isolated compile-only Lombok-compatible annotation definitions only after license/namespace review; it never enables the processor.

Alternatives: an absolute zero-runtime promise would force semantic changes; silently bundling Lombok duplicates classes and hides MIT obligations. Consequences: artifact manifests expose profile, dependency, ABI and license; unknown need is a release blocker, not an invented exception.

Counterexamples/tests: default programs run without runtime JAR; a helper-dependent feature fails clearly when its declared artifact is absent. Test duplicate `lombok.*` classes, processor-disabled strict profile, notices/SBOM and throwable identity for SneakyThrows.
