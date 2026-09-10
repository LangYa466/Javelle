# Property metadata ABI

Status: normative contract frozen; serializer/consumer NOT_IMPLEMENTED. Requirements: P03-05, P03-10, P19-05, P19-07.

Each output JAR may contain `META-INF/javelle/properties-v1.json`. The UTF-8 JSON root is `{schemaVersion, producerVersion, classes}`; `schemaVersion` is integer `1`. Classes are keyed by JVM binary name. Each property records stable source-independent `propertyId`, source name, JVM field name when backed, getter/setter JVM names and descriptors, property/accessor visibility, mutability, static/final/computed flags, and declaration annotations by resolved FQN. Entries and object keys are emitted deterministically.

Unknown additive fields MUST be ignored within major schema 1. A higher schema major is rejected with `JVL-META-UNSUPPORTED-VERSION`; missing metadata means Java-only ABI (never inferred from arbitrary getters). A malformed or duplicate property/member mapping is `JVL-META-INVALID`. Metadata cannot widen JVM accessibility and is not runtime reflection authority.

Test obligations: cross-JAR property use and plain-Java getter use; missing/corrupt/future schema; stable IDs after relocation; overloaded accessors and boolean/acronym naming; metadata/JVM signature mismatch; deterministic byte output.
