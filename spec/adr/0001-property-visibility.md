# ADR-0001: Property accessor visibility
Status: Accepted design; P03-04; implementation NOT_IMPLEMENTED.

Decision: an omitted accessor visibility inherits the property visibility; an explicit accessor may widen or narrow it subject to Java accessibility legality.

Alternatives: always-public accessors leak private state; always-private accessors make public properties unusable. Consequences: ABI projection records property and accessor visibility separately; widening is deliberate API.

Counterexamples/tests: `private String token { get }` must not expose public `getToken`; `private String token { public get }` must expose it. Also test illegal modifier combinations and reflection visibility.
