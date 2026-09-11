# ADR-0004: Try-resource and enum separators
Status: Accepted design; P03-03; implementation NOT_IMPLEMENTED.

Decision: multiple try resources are grammar-delimited by significant line boundaries; one colon separates enum constants from members.

Alternatives: comma resources diverge further from Java expressions; semicolons violate the language; implicit enum transition is ambiguous. Consequences: formatter must preserve required line breaks and migration must emit them.

Counterexamples/tests: two resources on distinct valid lines succeed while two adjacent resources on one line fail; `enum E { A: int code() { return 1 } }` succeeds while a second colon/member-less malformed boundary fails.
