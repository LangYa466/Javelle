# P03-W03 ABI / ADR contract report

- Agent: `/root/p00_repair`
- Status: CONTRACT_IMPLEMENTED; product functionality NOT_IMPLEMENTED
- Requirements: P03-03/04/05/08/09/10/11 plus downstream P19/P20/P25 obligations.

Delivered: seven A3 decisions as accepted ADRs with alternatives, consequences, at least two counterexamples and test obligations; deterministic generated-Java/property metadata ABI; finite joint compilation/processor rounds; Lombok baseline/FQN/profile/config/oracle/full-matrix boundary; minimal-runtime and compatibility-annotation licensing decision; separate diagnostic source-map and JVM debug contracts.

Machine checks: `spec/adr/index.json` links all seven ADR files; `spec/abi/index.json` links ABI documents, ADR index, source-map and Lombok contracts. Both declare schema version and distinguish frozen contracts from unimplemented features.

Verification performed: JSON indexes parsed and every declared relative link resolved by a local Python read-only check; exit 0. Evidence: `.agent/logs/P03-ABI-ADR-check.txt`.

No claims: no compiler, serializer, processor, Lombok feature, source-map or debugger implementation is asserted. Independent specification review remains required before ACCEPT.
