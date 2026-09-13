# Teyru Language Specification 0.1-draft

Status: **normative contract, implementation NOT_VERIFIED**. “Must”, “must not”, “shall” and “error” are normative. Examples and rationale are informative. Java semantics mean Java SE 25 non-preview unless a `--release 21` profile is selected.

## 1. Identity and Java-first rule

The language name is Teyru, extension `.teyru`, language ID `teyru`. Except for the explicit grammar and semantic differences below, Teyru preserves Java syntax, static typing, overload resolution, generics/capture, target typing, reference `==`, numeric overflow/conversions, mutability, checked exceptions, initialization, evaluation order and null behavior. Teyru does not add Kotlin-style `name: Type`, nullable `Type?`, `fun`, implicit bean properties, deep immutability or a different equality operator.

## 2. Source, Unicode, trivia and locations

1. Input bytes decode as UTF-8. A leading BOM is recorded as trivia and is not part of the first token.
2. Unicode escapes are translated using the Java lexical eligibility/parity rules before token recognition. The implementation must retain a monotonic many-to-one mapping from every translated UTF-16 boundary to the original byte and UTF-16 boundaries. An escape producing CR/LF affects line structure; an escape producing `;` produces the same forbidden token as literal `;`.
3. Line terminators are LF, CRLF, or CR. Their raw spelling is trivia; logical newline is one token. Strings, chars, text blocks and comments consume their internal characters before semicolon/newline classification.
4. Tokens own no leading/trailing trivia. Ordered trivia (whitespace, newline, line/block/Javadoc comments, BOM) is attached to the containing token gap. CST nodes preserve tokens, trivia, raw and translated spans; AST nodes preserve a source span and stable node ID.
5. Ranges are half-open `[start,end)`. Diagnostics use original-source ranges. Editor conversion is negotiated LSP encoding, with UTF-16 mandatory fallback. Generated nodes record direct, expanded, synthetic or related source-map reasons.
6. Error recovery inserts explicit missing/error nodes. Synchronization points are newline after a complete construct, `}`, declaration starters, accessor starters, enum member separator, resource newline and the two basic-for separators. Recovery must consume input or stop; it may not loop or discard an entire following declaration for one incomplete line.

## 3. No syntax semicolons

`SEMICOLON` is never legal syntax and reports `TY-SYN-0001`. Semicolons within string/char/text-block/comment data are preserved. Generated and input `.java` use normal Java semicolons.

A logical newline terminates a statement/declaration only when the prefix is syntactically complete and continuation is not required. Continuation is required inside unclosed delimiters, after an operator/comma/dot/`::`/arrow requiring a right operand, or when the following line begins with a legal chain selector (`.`/`::`) or grammar-approved leading operator. Formatter output must make continuation unambiguous. Blank/comment-only lines do not change the decision.

- `return` followed immediately by logical newline is a value-less return. A value must start on the same line; `return (` may continue until the matching `)`.
- `throw` and value-requiring `yield` must begin their expression on the same line (or open it with `(`); otherwise `TY-SYN-0003`.
- `++`/`--` never attach across a terminating newline. A postfix operator must be on the operand's logical line; a prefix operator belongs to the following operand only where a new expression is expected.
- Multiple ordinary statements cannot share a line via `;`. A Java empty statement migrates to an explicit empty block where grammar permits. `do { } while (condition)` has no trailing semicolon.
- Abstract/interface methods, annotation elements and module directives end at logical newline or containing `}` according to their productions.

## 4. Structural replacements

### 4.1 Basic and enhanced for

Basic-for is `for ( init? : condition? : update? ) statement`. These two top-level colons are structural separators. Enhanced-for remains `for ( modifiers? type variable : expression ) statement`. Parser choice is syntactic and nesting-aware: colons inside parentheses/brackets/braces, conditional expressions and type constructs do not split the header. `for (int i=0 : test ? a : b : i++)` is valid. Omitted condition is `true`; Java scope, order, labels, break and continue are unchanged.

### 4.2 Try-with-resources

Within `try (` each complete resource is terminated by at least one logical newline. A resource initializer may continue while its expression is incomplete. Same-line adjacent resources and semicolon separators are errors. Existing effectively-final variables remain valid resources. Initialization order, reverse close order, partial failure and suppressed exceptions are Java semantics; Lombok `@Cleanup` is a separate contract.

### 4.3 Enum

Enum constants use the Java comma grammar. If declarations follow, exactly one top-level `:` separates the constant area and member area. No members means the colon is omitted; an empty constants area with members starts with `:`. Constant arguments, anonymous bodies and trailing comma remain legal. Blank lines never infer this boundary.

## 5. `var`, `val`, and null

`var` is Java local inference with reassignment allowed. `val` uses the same inference then creates a final local binding; it is not deep immutability. Both require an initializer and are allowed only for local variables and the corresponding enhanced-for/resource contexts defined by grammar. `val` is not a parameter syntax; Java's contextual `var` lambda-parameter syntax remains available.

Fields, parameters and return types require explicit Java types. `private var x`, `private val x`, `var` return types and `name: Type` report context errors. `var x = null`, `val x = null`, and untargeted lambda/method-reference initializers cannot infer a type. `(String)null` has type String. Explicit `String x = null` is valid and differs from `""`; Teyru adds no implicit null check.

Anonymous, intersection and captured inferred types must not be widened to `Object` merely for emission. Legal emitted Java may retain `var`/`final var`; no unnameable inferred type may leak into ABI.

## 6. Native properties

### 6.1 Declaration and storage

An explicitly typed field-like declaration becomes a property only when followed by an accessor block. Without the block it remains an ordinary Java field and generates no accessor API.

A property declaration's type is the accessor/storage type. Its outer visibility is the default accessor visibility; backing storage, when required, is always private. `static`, `final`, `volatile`, and `transient` apply to storage when semantically valid. Accessors may be `get`/`set`, may override visibility, may be default (no body), and may appear in either order exactly once. `set(value)` has the property type; another parameter name is permitted only if grammar/spec later explicitly extends this contract.

Storage is required by any initializer, default accessor, setter, or accessor reference to contextual `field`. A custom getter with no initializer and no `field` reference is computed and has no storage. Storage-less properties reject storage-only modifiers. A final storage property rejects a setter. Uninitialized final storage follows Java constructor definite-assignment; constructor assignment is raw initialization, not a setter call.

Initializers write storage exactly once in Java field-initializer order and never invoke the setter. Storage without an explicit initializer receives Java default initialization; strings are not changed to empty strings.

`field` binds to the current property's storage only inside that property's accessor body. It is not a keyword elsewhere. A local/member literally named `field` outside that context retains Java meaning. A computed property that refers to `field` thereby requires storage. The preferred backing name is the declared property name; collision is a diagnostic, while unavoidable helpers use deterministic collision-free names.

### 6.2 Access, ABI and names

Resolved property reads call the getter and writes call the setter, including unqualified and `this.name` accesses inside the declaring class. Only contextual `field` and explicit initialization lowering access storage. Missing getter makes normal reads invalid; missing setter makes normal writes invalid. Explicit generated `getX`/`setX` calls remain legal Java/Teyru calls.

Default ABI follows JavaBeans capitalization: compute a base by uppercasing the first code point unless the first two code points are already uppercase; getter is `get<Base>`, except primitive `boolean` may use `is<Base>`; boxed `Boolean` uses `get<Base>`. Setter is `set<Base>(T)`. Conflicts, acronyms and explicit Lombok `@Accessors` are resolved before emission and may not silently duplicate a signature.

Unannotated plain Java fields/getters are not properties. Cross-JAR Teyru property use requires versioned `META-INF/teyru/` metadata identifying owner symbol, property name/type, accessors, modifiers and source origin; absence/incompatible version must not trigger getter-name guessing. Java consumers use ordinary accessor ABI without metadata.

### 6.3 Evaluation order

Lowering must preserve Java expression value, conversion, exception timing and exactly-once receiver evaluation:

- `r.p = rhs`: evaluate `r`, then `rhs`, call setter once; do not call getter. Expression result is the assignment-converted RHS, not a value re-read after setter normalization.
- `r.p op= rhs`: evaluate/store `r` once, call getter once, evaluate `rhs`, apply Java binary operation plus implicit compound-assignment conversion (including narrowing/boxing/unboxing/overflow), call setter once; expression result is converted value passed to setter.
- `r.p++`/`++r.p`: evaluate `r` once, getter once, arithmetic/conversion once, setter once. Postfix result is old value; prefix result is converted new value.
- Chained assignment evaluates receivers/RHS in Java order and each target once. Conditional/short-circuit branches invoke no accessor from an unchosen branch. Lowering in lambdas/switch/for update/labelled control flow must not wrap code in a lambda that changes capture, checked exceptions or control transfer.
- Null receiver and accessor/RHS exceptions occur at the corresponding Java evaluation step. A throwing getter prevents RHS and setter in compound assignment; a throwing RHS prevents setter; simple assignment never triggers getter.

### 6.4 Inheritance, interfaces, records, annotations

Property accessors participate in Java override, accessibility, generic substitution, covariant return and static hiding rules. An interface may declare abstract accessors or a default computed getter, but cannot acquire instance backing storage. Records cannot gain illegal instance storage; computed properties are allowed and components retain Java semantics. Read-only, write-only, generic and static properties are permitted when their operations are type-correct.

Annotations are copied only to explicitly specified and legal targets. FIELD-only annotations stay on storage; accessor annotations stay on corresponding methods; setter-parameter annotations require PARAMETER target. Retention/repeatability remain Java rules. Explicit native accessors win over Lombok-generated accessors; duplicate/incompatible visibility or type is a diagnostic, never silent loss.

## 7. Java declarations and mixed compilation

Package/import/static import, Java types/generics/annotations, classes/interfaces/enums/records/sealed forms, nested/local/anonymous types, initializers/constructors, all Java statements/expressions/patterns/text blocks and Java 25 non-preview features remain in scope. Preview syntax is rejected unless a future explicit profile says otherwise. `--release 21` rejects later features precisely.

Resolution includes JDK, class/module path, source/source-JAR, generated sources, module outputs and unsaved overlays. Mixed compilation collects Teyru/generated member shapes, creates source-mapped Java-facing analysis projections, jointly attributes Java sources, completes lowering, then sends Java plus generated Java to one javac compilation. Stubs are never release classes. Processor rounds are explicit, finite and duplicate-safe; untrusted editor analysis uses `-proc:none`.

### 7.1 JLS chapter coverage contract

The Java SE 25 inventory is chapter-complete, not syntax-only. Chapters 1–2 define scope/notation; 3 lexical input; 4–5 types/conversions; 6–7 names/packages/modules; 8–10 declarations/arrays; 11 exceptions; 12 execution; 13 binary compatibility; 14–15 statements/expressions; 16 definite assignment; 17 concurrency; 18 inference; 19 syntax. Teyru differences are limited to this specification. In particular, Chapter 16 governs uninitialized explicit declarations and final property constructor assignment, while Chapter 19 is the formal base imported by `teyru.ebnf`. The machine grammar contract lists chapters 1 through 19; Java 21 profile differences must be attached per feature and may not remove a chapter from coverage.

## 8. Diagnostics and conformance

Diagnostic codes and fixture cases are normative machine files in `spec/diagnostics/`. A conforming frontend must emit the specified primary code and original-source range; additional recovery diagnostics are allowed only when non-duplicative. Fixture status in this draft is `NOT_IMPLEMENTED/NOT_VERIFIED`; conformance requires external parser/compiler execution, not schema validation alone.
