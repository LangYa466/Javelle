# Generated Java ABI contract

Status: normative contract frozen; implementation and tests remain NOT_IMPLEMENTED. Requirements: P03-05, P03-09, P19-07, P19-11.

Generated Java preserves the Java-visible package, binary name, nesting, accessibility, generic signature, annotations, declared exceptions, record/sealed metadata and source declaration order where Java semantics permit. Output is deterministic UTF-8 with LF, no timestamps or absolute paths. Synthetic implementation details use the reserved prefix `$teyru$`; a source declaration colliding with a required synthetic name is a diagnostic, never silently renamed.

Property accessors follow JavaBeans capitalization: `name` maps to `getName`/`setName`; primitive `boolean active` maps to `isActive`; boxed `Boolean` uses `getActive`; an initial two-uppercase sequence is retained (`URL` → `getURL`). Explicit `@Accessors` compatibility rules override only when that resolved annotation is enabled. Existing source methods with the required signature cause an explicit collision diagnostic. Plain fields and arbitrary Java getters do not become properties.

Mixed compilation is finite: declaration collection → versioned Java-facing projections → joint Java attribution → lowering/emission → one javac compilation. Projections are analysis artifacts and MUST NOT be packaged. Processor execution is opt-in trusted build behavior, has a configured maximum of eight rounds, fingerprints each generated source, rejects conflicting/regressing output, and emits `JVL-PROC-NONTERMINATING` when no fixed point is reached. Editor analysis always uses `-proc:none`.

Test obligations: javap/reflection ABI goldens; Java→Teyru and Teyru→Java same-module references; generics/annotations/exceptions/nesting; collision negatives; processor adds-type/fixed-point/duplicate/nontermination cases; deterministic output across clean roots.
