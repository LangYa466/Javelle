# P01-R3 — LSP4J 1.0.0 endpoint and DTO gap inventory

- Agent `/root/p00_spec`; base `NO_GIT_REPOSITORY`
- Official input: LSP 3.18.2 meta-model SHA-256 `35ebcc0b607eeda105dd092c990182eb338852d82130053d3a27499ff34c89a2`
- Binding input: LSP4J 1.0.0 binary `ccd78893facc6bfcc359d56cba05d3d5b85eb41e4c40d4b4215ca45db5f416d9` plus Maven Central source jar
- Generator: `.agent/logs/P01-R3/generate_lsp_inventory.py`; generated machine files are tracked, raw/source evidence remains ignored

## Result

`compatibility/lsp-methods.json` now contains exactly 95 unique methods. Every item was derived from actual `@JsonSegment`, `@JsonRequest`, or `@JsonNotification` source annotations; `$/cancelRequest` is tied to `RemoteEndpoint#cancel`. All 95 have a concrete symbol, registration kind, source file and official source marker. There are 0 unbound/custom-registration items after correctly handling positional annotation values. All remain `supportStatus=NOT_IMPLEMENTED` and `advertised=false`.

`compatibility/lsp-dto-gaps.json` records all 450 official meta-model structures, enumerations and aliases with field-count/matching evidence:

- 309 `SUPPORTED_LIBRARY`: same-named LSP4J source type and no unmatched directly declared official field.
- 16 `PROTOCOL_DELTA_WITH_REASON`: matching type exists but one or more official fields are not directly declared; inheritance/mixin/rename requires focused serialization tests, or an alias maps to Java primitives/collections/Either.
- 125 `CUSTOM_DTO_REQUIRED`: no same-named source type was found. This is deliberately conservative; a later reviewer may replace it only with a verified alternate Java symbol and serialization test.
- 0 `NOT_REVIEWED`.

This field/type inventory removes the former blanket “LSP4J supports 3.18” inference. A library binding still does not mean Teyru implemented a handler or may advertise it.

## Endpoint evidence model

- Text methods bind to `TextDocumentService`; workspace methods to `WorkspaceService`; client/window/telemetry methods to `LanguageClient`; notebook lifecycle to `NotebookDocumentService`; lifecycle to `LanguageServer`.
- Explicit annotations such as `completionItem/resolve`, hierarchy and semantic-token paths preserve `useSegment=false` through the parsed exact method value.
- The machine entry contains the exact `Class#javaMethod`, annotation kind and source filename, so each method is auditable without relying on version marketing.

## Application N/A boundary

The pre-existing application-level rationales remain separate from DTO support: notebook lifecycle (no notebook host), document colors (no color-provider semantics), and inline values (requires debugger frame context) are `N/A_WITH_REASON`. DTO/endpoint support is still inventoried, and no N/A capability may be registered. Other 88 entries remain applicable but unimplemented.

## Reproduce and validate

```bash
python3 .agent/logs/P01-R3/generate_lsp_inventory.py \
  .agent/tmp/P01-R3/src \
  .agent/logs/P01-LOMBOK-LSP/lsp-3.18.2-metaModel.json \
  compatibility/lsp-methods.json compatibility/lsp-dto-gaps.json

jq -e '(.methods|length)==95
 and ([.methods[].method]|unique|length)==95
 and ([.methods[]|select(.libraryBinding.status=="CUSTOM_REGISTRATION_REQUIRED")]|length)==0
 and ([.methods[]|select(.dtoGapStatus=="NOT_REVIEWED")]|length)==0
 and ([.methods[]|select(.supportStatus!="NOT_IMPLEMENTED" or .advertised!=false)]|length)==0
 and ([.methods[]|select((.libraryBinding.symbol|length)==0 or (.source|length)==0)]|length)==0' compatibility/lsp-methods.json

jq -e '.typeCount==450
 and (.types|length)==450
 and ([.types[].name]|unique|length)==450
 and ([.types[]|select(.status=="NOT_REVIEWED" or (.reason==null and .status!="SUPPORTED_LIBRARY"))]|length)==0
 and ([.summary[]]|add)==450' compatibility/lsp-dto-gaps.json
```

## Remaining review risks

1. Same-name/source-text matching is conservative structural evidence, not a Gson wire round-trip proof. The 141 non-supported entries require alternate-symbol mapping or custom DTO tests before implementation.
2. LSP4J 1.0.0 described 3.18.0 while the fixed official model is release/protocol/3.18.2. Serialization tests must verify optional fields, unions, inheritance/mixins, enums and aliases.
3. No support/advertisement status should change until P10/P30–P36 black-box tests pass capability negotiation.

Primary sources: https://raw.githubusercontent.com/microsoft/vscode-languageserver-node/release/protocol/3.18.2/protocol/metaModel.json ; https://github.com/eclipse-lsp4j/lsp4j/releases/tag/v1.0.0 ; https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/1.0.0/
