# P03 independent specification/contract review

TASK / AGENT_ID / BASE_REVISION: `P03-REVIEW` / `/root/p00_review` / full post-P02 `dev` candidate

STATUS: **FAILED — REJECT P03**

Scope is contract quality only. No parser, compiler, source-map, debugger, Lombok compatibility, or mixed-compilation implementation is claimed or required to pass this specification stage.

## Requirement decisions

| ID | Decision | Independent result |
|---|---|---|
| P03-01 | PASS | `spec/language-spec.md` is versioned in Git, explicitly normative, separates informative examples/rationale, covers B1–B5 boundaries, and marks implementation `NOT_VERIFIED`; README does not define semantics. |
| P03-02 | **FAIL** | Unicode translation, original spans, contextual names, logical line terminators and recovery are normatively described, but `teyru.ebnf` is not a complete verifiable grammar delta: it imports only `JAVA_*` yet also references 18 undefined non-`JAVA_*` productions (including `block`, `enumConstant`, `classBodyDeclaration`, and all control statements). Its `variableDeclarator` requires `=`, contradicting legal uninitialized explicit Java locals/fields and the normative uninitialized/computed property forms. |
| P03-03 | PASS | Prose and ADR-0003/0004 freeze basic/enhanced for, newline TWR, enum colon, empty-block migration, no-tail-semicolon do/while, and newline endings for abstract/interface methods, annotation elements, and module directives. |
| P03-04 | PASS | Normative rules freeze accessor/storage visibility, default/custom accessors, contextual `field`, initialization/definite assignment, final/computed properties, side-effect order, inheritance/interfaces/records, and annotation targets. |
| P03-05 | PASS | JavaBeans primitive-boolean/boxed-Boolean/acronym rules, deterministic `$teyru$` collision policy, descriptors, property IDs, metadata schema v1 and additive/major evolution behavior are frozen. |
| P03-06 | PASS | Local/enhanced-for/resource contexts, initializer requirement, val final binding, Java var lambda parameters, bare-null/target typing and anonymous/intersection/capture emission constraints are explicit. |
| P03-07 | **FAIL** | The inventory has 33 unique entries, 15 categories, JLS links, Java 25/21 dispositions and positive/negative obligations, including the four principal 25→21 deltas. It is nevertheless not the required chapter-by-chapter inventory: its JLS references omit Chapters 16 (definite assignment) and 19 (syntax), despite both being material to this stage; Chapters 1/2 are also absent. Coarse prose references to definite assignment do not satisfy a complete chapter mapping. |
| P03-08 | PASS | Resolved-FQN recognition, collisions, native/strict-metadata profiles, full baseline matrix obligations, config hierarchy/import/cycle/flagUsage rules, isolated oracle and unsupported-feature diagnostics are normative and remain honestly unimplemented. |
| P03-09 | PASS | Projection/attribution/lowering/javac order, opt-in trusted processors, eight-round bound, source fingerprinting, conflict/regression handling, fixed-point failure code, and editor `-proc:none` are frozen. |
| P03-10 | PASS | Diagnostic namespaces/ranges/related recovery, UTF-16 conversion, source-map many-to-one/synthetic/priority/evolution/path rules are specified; diagnostic mapping is explicitly separated from JVM SMAP/debugger evidence. |
| P03-11 | PASS | Seven accepted ADRs exist and resolve autonomous choices. Each records alternatives, consequences, at least two counterexamples/test obligations, and `NOT_IMPLEMENTED` status. |
| P03-12 | **FAIL** | Reviewer counterexamples exposed formal grammar contradictions and undefined productions; the draft therefore has unresolved parser ambiguity/completeness defects and cannot pass the required adversarial grammar review. |

## Review-only counterexamples

| Case | Expected contract | Review result |
|---|---|---|
| `for (int i=0 : ok ? a : b : i++) {}` | ternary colon remains inside condition | Covered by manifest/prose, but depends on opaque `JAVA_expression`; no closed formal disambiguation interface is defined. |
| `return\nnext()` | value-less return, then next statement | Covered and unambiguous in normative prose/fixture. |
| multiline TWR initializer | newline inside incomplete expression continues; only completed resource newline separates | Prose covers it; EBNF delegates completion without defining how NEWLINE interacts with imported Java expression grammar. |
| `enum E { A { ... }, B : ... }` | anonymous constant body remains within enum constant | Prose/inventory covers it; `enumConstant` and `classBodyDeclaration` are undefined in the claimed delta grammar. |
| `factory().count++` | receiver/getter/setter exactly once; postfix returns old value | Normative prose and behavior fixture cover it. |
| `byteProperty += 130` | Java compound narrowing/overflow and converted setter argument | Normative prose and inventory cover it. |
| `class C { String x { get { return "x" } } }` | legal computed property without initializer | Normative section says legal; EBNF requires `variableDeclarator = identifier "=" initializer`, so it rejects the form. |
| `class C { int x }` / local `int x` | legal explicit Java uninitialized declaration where Java permits | Imported-Java preservation requires it; the same mandatory initializer production rejects it. |

## Executed checks

- `python3 spec/diagnostics/validate.py`: exit 0, `SPEC_MANIFEST_OK diagnostics=20 fixtures=24`.
- `python3 -m json.tool` over grammar contract, diagnostics catalog/fixtures, Java inventory, ABI/ADR indexes and Lombok baseline: all exit 0.
- ABI/ADR relative-link resolver: exit 0; all nine declared document links exist.
- Inventory shape: 33 entries, 15 categories, unique IDs; referenced chapters are 3–15, 17, 18, leaving 1, 2, 16, 19 unmapped.
- Formal-production reference audit: 18 undefined non-`JAVA_*` productions; imported `JAVA_*` symbols were excluded from this failure count.

## Stage decision

**REJECT.** Exact failed requirements: `P03-02`, `P03-07`, `P03-12`. The semantic/ABI/Lombok/source-map contracts are otherwise suitable as frozen, explicitly unimplemented obligations. Repair requires a closed and validator-checked Java-base grammar import boundary, declaration productions consistent with uninitialized/computed forms, and a genuinely chapter-complete JLS 25/21 mapping. Then rerun these counterexamples before P03 acceptance.

REVIEW: independent `/root/p00_review`; implementers did not approve their own packages.

---

## Focused R1/R2 re-review — authoritative current decision

TASK / AGENT_ID / BASE_REVISION: `P03-REVIEW-R2` / `/root/p00_review` / repaired full `dev` candidate

STATUS: **VERIFIED — ACCEPT P03**

### Repaired failures

- `P03-02` — **PASS**. The formal audit now reports zero undefined non-`JAVA_*` references. All 34 referenced `JAVA_*` productions appear exactly once in the machine-readable Java import boundary and are mapped to the Java SE 25 JLS grammar source. `variableDeclarator` now makes initialization syntactically optional, while four explicit semantic constraints require it for `var`/`val`, permit omission for explicitly typed Java declarations subject to definite assignment, and distinguish stored versus computed properties. Both computed-property and explicit uninitialized-field forms are derivable.
- `P03-07` — **PASS**. `chapterCoverage` is ordered and exactly covers JLS Chapters 1 through 19. Chapters 16 and 19 have material, separately identified requirements (`TY-J25-DA-001`, `TY-J25-SYNTAX-001`), positive/negative obligations, and valid feature cross-references. All chapter requirement references resolve to inventory entries.
- `P03-12` — **PASS**. The diagnostics manifest now contains 29 cases and the focused adversarial set covers ternary/basic-for, return newline, multiline TWR initializer, enum anonymous constant body, `factory().count++`, compound narrowing, computed property without initializer, and explicit uninitialized field. The repaired grammar/semantic constraints resolve the contradictions found in the first review. These remain normative test obligations, honestly `NOT_IMPLEMENTED/NOT_VERIFIED`, rather than compiler-pass claims.

### Regression and executed evidence

- `python3 spec/diagnostics/validate.py`: exit 0, `SPEC_MANIFEST_OK diagnostics=20 fixtures=29 undefinedRefs=0 javaImports=34`.
- Independent production/import audit: undefined non-Java references 0; referenced Java imports 34; contract imports 34 unique.
- JSON parsing for grammar, fixtures, catalog, Java inventory, ABI/ADR indexes and Lombok baseline: all exit 0.
- ABI/ADR link resolver: exit 0 with no missing document; seven ADRs and prior frozen contracts remain present.
- JLS audit: exact chapters `[1..19]`; zero missing inventory cross-references.
- Status audit: grammar and fixtures remain `NOT_IMPLEMENTED/NOT_VERIFIED`; spec, ABI, source-map, Lombok and ADR documents retain explicit non-implementation status. No unresolved TODO/TBD marker was found.
- `.idea/vcs.xml` remained excluded and untouched.

Final decisions: **PASS `P03-01/02/03/04/05/06/07/08/09/10/11/12`**. No prior PASS regressed. The stage freezes specification and contract obligations only; it does not accept compiler, Lombok, source-map, mixed-build, or debugger functionality.

REVIEW: independent `/root/p00_review`; **ACCEPT P03**.
