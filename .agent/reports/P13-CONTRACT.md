# P13 complete declaration and type grammar

TASK / AGENT_ID / BASE_REVISION: `P13-W01` / `/root` / `dev` at `2f6bb43`

STATUS: **CONTRACT_FROZEN — P13 implementation IN_PROGRESS (round 1 of N)**

Source: `prompts/JAVELLE_IMPLEMENTATION_PLAN.md` section E, `## P13 — 完整宣告與型別 grammar` (lines 798-817). This contract restates that section's 12 requirements (P13-01..P13-12) as a concrete acceptance matrix; it does not change the plan's scope. Depends on P12 (ACCEPTED pending its own independent review, in progress concurrently with this round).

## 1. Starting point

`RecursiveJavelleParser` (compiler-core, P05-era, ~620 lines before this round) only parses: top-level `class` declarations (via a lookahead scan for the `class` keyword), `package`/`import` as raw scanned lines, and inside a class body: fields, computed/stored properties, and methods requiring a body. `enum`, `record`, `interface`, `@interface`, `module`/`open` are all top-level `unsupported(...)` placeholders. There is no constructor support (a member with no type prefix, same name as the enclosing class), no nested/local/anonymous classes, no generics, no modifiers validation beyond a flat allowlist, no sealed/permits, no package-info/module-info handling.

## 2. Acceptance matrix (P13-01..P13-12)

| ID | Required evidence |
|---|---|
| P13-01 | `package`, all import forms (single-type, on-demand, static single, static on-demand) parse as real declarations, not raw scanned lines swallowing arbitrary trailing content. |
| P13-02 | class/interface/enum/record/annotation declarations, `sealed`/`non-sealed`/`permits`, and full modifier validation (reject invalid combinations with a real diagnostic, not silent acceptance). |
| P13-03 | Generic declarations, bounds, wildcards, array annotations, varargs, receiver parameters, type-use annotations. |
| P13-04 | Fields, methods, constructors, annotation elements, initializer blocks, nested/local/anonymous classes. |
| P13-05 | Java 25 constructor-body rules (this()/super() placement), compact/instance `main`, and other current non-preview declaration features per JLS inventory. |
| P13-06 | Enum colon member delimiter, constant arguments, constant-specific class bodies, empty enums, trailing commas. |
| P13-07 | `package-info`/`module-info` compilation units — not every source file is assumed to declare a public class. |
| P13-08 | Property initializer bodies that are array/anonymous-class/lambda expressions followed by an accessor block are disambiguated correctly — seeing `{` doesn't always mean "this is an accessor block". |
| P13-09 | Interface/abstract method bodiless termination and adjacent-declaration ambiguity recovery. |
| P13-10 | Precise syntax diagnostics for invalid modifiers, duplicate constructors, unclosed generics, class/record context confusion. |
| P13-11 | Real Java fixtures migrated through grammar-aware translation, compared for declaration AST/ABI equivalence, preserving annotations and Javadoc. |
| P13-12 | Independent reviewer cross-checks against the Java declaration inventory; gaps cannot be reclassified as "future work" without explicit scope sign-off. |

## 3. Round 1 progress (this commit, self-reported, not yet independently reviewed)

Implemented: real `interface` declarations (P13-02 partial, P13-09) — modifiers, abstract method signatures with no body (bodiless, terminated by newline/`}`/EOF per P13-09), `default`/`static` methods requiring a real body (reusing the class body-parsing infrastructure via a new `parseMethod(type, name, bodyRequired)` overload), and constant fields requiring an initializer (diagnosed if missing). Five new tests in `P13InterfaceDeclarationTest.java` cover: abstract-method-no-body, default/static-method-with-body, missing-body-is-an-error, constant-field-initializer-required, and unterminated-interface recovery.

Explicitly NOT done yet (next rounds): `enum`, `record`, `@interface`, `module`/`open`/package-info/module-info (P13-06, P13-07), sealed/permits/non-sealed modifiers (P13-02), generics/bounds/wildcards/varargs/receiver parameters/type-use annotations (P13-03), constructors and nested/local/anonymous classes (P13-04, P13-05), the property-initializer-vs-accessor-block disambiguation for array/anonymous-class/lambda initializers (P13-08 — the current class-body parser already handles SOME of this per P05/P18 groundwork; needs a dedicated audit), full modifier-combination validation (P13-02/P13-10), and the Java-fixture ABI-equivalence migration suite (P13-11).

## 4. Test requirements

Same discipline as every prior stage: fixture-backed JUnit tests per requirement, `verifyQuick` green, no silent regression of already-accepted P05/P12 behavior.

## 5. Acceptance

Independent review only after all 12 rows have real evidence — this round is a checkpoint, not a stage-exit claim.
