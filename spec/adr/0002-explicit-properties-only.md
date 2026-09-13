# ADR-0002: Explicit properties only
Status: Accepted design; P03-04/P03-05; implementation NOT_IMPLEMENTED.

Decision: only an accessor-block declaration is a native property. Plain fields and Java `getX()` methods remain ordinary Java members.

Alternatives: global bean projection changes Java name resolution; field auto-property changes encapsulation and ABI. Consequences: cross-JAR property syntax requires versioned Teyru metadata; absent metadata falls back to Java members.

Counterexamples/tests: `public String name` does not permit property lowering; Java library `getName()` alone does not create `name`. Test explicit property success and malformed metadata rejection.
