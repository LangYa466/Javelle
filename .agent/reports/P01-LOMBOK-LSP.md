# P01-W06 — Lombok 1.18.48 / LSP 3.18 inventories

- Agent: `/root/p00_spec`; base: `NO_GIT_REPOSITORY`; checked 2026-09-11
- Scope: inventories only. No Javelle feature is claimed implemented.
- Evidence: `.agent/logs/P01-LOMBOK-LSP/`

## Immutable inputs and hashes

| Input | Fixed source | SHA-256 |
|---|---|---|
| Lombok 1.18.48 | `https://projectlombok.org/downloads/lombok-1.18.48.jar`; source tag `v1.18.48` | `85477a4655ebb2c074a9099cfb749be454449fee564d4282610df1b85f7c508b` |
| LSP 3.18 meta-model | `https://raw.githubusercontent.com/microsoft/vscode-languageserver-node/release/protocol/3.18.2/protocol/metaModel.json` | `35ebcc0b607eeda105dd092c990182eb338852d82130053d3a27499ff34c89a2` |
| LSP4J API 1.0.0 | Maven Central `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` | `ccd78893facc6bfcc359d56cba05d3d5b85eb41e4c40d4b4215ca45db5f416d9` |
| LSP4J JSON-RPC 1.0.0 | Maven Central `org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0` | `9647feb0524bf763c878e12ab878a684102b81cccb3f77feecbec709d54f9bbb` |

正式 dependency verification 仍應向 Maven Central `.sha256`/signatures 二次核對；本包只保存下載內容 hash。

## Lombok inventory

實際 `jar tf` 匯出 151 個 `lombok/`、`lombok/experimental/` 頂層 public-surface 與 `lombok/extern/**` class entries（含 nested classes），完整排序表為 `lombok-public-surface.txt`；完整 jar entries 為 `lombok-jar-entries.txt`。這個數字是 class-entry coverage seed，不等於 151 個獨立 feature 或已支援數。

實際執行 `java -jar lombok-1.18.48.jar config -g --verbose` 得 988 行與 82 個唯一 config keys，完整原始輸出與 `lombok-config-keys.txt` 已保存。P20 registry 必須逐一記錄：FQN、annotation/utility/enum/nested API、合法 target、參數/default、config keys、generated ABI、negative cases、組合、oracle evidence。

Inventory families（須以完整 class/key 表驅動，不得只靠此摘要）：

- Core: val/var, Getter/Setter, NonNull, constructors, Data/Value, ToString, EqualsAndHashCode, Builder/Singular, With, Cleanup, SneakyThrows, Synchronized, logging families.
- Experimental/current: Accessors, SuperBuilder（1.18.48 promotion/legacy path需雙查）, WithBy, Delegate, ExtensionMethod, UtilityClass, FieldDefaults, Helper, Locked, Jacksonized, FieldNameConstants, StandardException, Tolerate.
- Metadata/support: AccessLevel、Generated、onX spellings、nested Include/Exclude/Default/ObtainVia、extern log packages、`lombok.Lombok` utility、config hierarchy/list/import/stopBubbling/flagUsage。

Production 禁止把此 jar/processor 當實作；僅 oracle/migration controlled path。未知 feature 必須明確 diagnostic，不能靜默忽略。

## LSP 3.18 inventory and LSP4J mapping

官方 3.18.2 meta-model 匯出 95 個唯一 request/notification method strings，見 `lsp-3.18-methods.txt`。LSP4J 1.0.0 jar 有 492 entries；`javap` 對 `LanguageServer`, `TextDocumentService`, `WorkspaceService`, `LanguageClient` 得 106 行 API 證據。LSP4J 官方 release 說 1.0.0 實作 LSP 3.18.0，但當時規格仍未 finalized；因此不能用 library 版本代替逐 method/capability 驗收。

映射策略：

| 類別 | 處理 |
|---|---|
| lifecycle/transport | LSP4J launcher + service interfaces；Javelle 仍須測 Content-Length、stdout purity、shutdown/exit、cancel、error codes |
| text/workspace language methods | 使用 LSP4J DTO/service default methods；每一 advertised method 必須有 handler、capability gate 與 black-box test |
| client-bound requests/notifications | `LanguageClient` API；只在 client capability 支援時發送 refresh/register/configuration/progress |
| meta-model 有、typed interface 缺少/變動 | 先查 1.0.0 tag source；必要時用版本化自有 DTO/JSON-RPC method registration，記 gap test；不可回空集合冒充支援 |
| protocol proposals/vendor extensions | 不混入 3.18 conformance；自有 extension 放 `language-protocol` 並有 schemaVersion/capability namespace |

Capability negotiation 必須至少覆蓋 positionEncoding/UTF-16 fallback、incremental sync、pull vs push diagnostics、dynamic registration、resolve support、workspace folders/file operations、semantic tokens full/delta/range、inlay/codeLens resolve、hierarchy、work-done/partial progress。Server 不做虛構 version handshake。

可合理 N/A 的候選只有經逐 method review 證明與語言/host 無關者，例如 notebook（無 notebook host）、color（無 color syntax）、inline value（屬 debugger context，除非另有 DAP integration）。即使 N/A，也要記 method、理由、reviewer；rename/Java completion/type hierarchy 等產品需求不得 N/A。

重要 gap/risk：

1. 官方 meta-model版本為 release branch 3.18.2，而 LSP4J release note說實作 3.18.0 draft；新增/變更 DTO、optional fields、proposals需逐项 diff。
2. LSP4J DTO availability不代表 Javelle semantic handler已实现；`compatibility/lsp-methods.json` 初始必须 NOT_IMPLEMENTED/NOT_VERIFIED。
3. LSP4J同时包含 DAP surface；debug 不得因 jar 有 DAP DTO 就宣称完成，P42仍需独立调试验收。
4. JSON-RPC、Gson 等 transitives需由 build owner pin/hash/license；本包没有修改 dependencies。

## Machine-checkable counts

```text
Lombok public-surface class entries: 151
Lombok verbose config output lines: 988
Lombok unique config keys: 82
LSP 3.18 unique request/notification methods: 95
LSP4J jar entries: 492
LSP4J selected service javap lines: 106
```

## Reproduce

```bash
java -jar lombok-1.18.48.jar config -g --verbose > lombok-config.txt
jar tf lombok-1.18.48.jar | sort > lombok-jar-entries.txt
jar tf lombok-1.18.48.jar | awk '/^lombok\/(experimental\/)?[A-Z][A-Za-z0-9_$]*\.class$/ || /^lombok\/extern\/.+\.class$/' | sort > lombok-public-surface.txt
awk '/^## Key : /{print $4}' lombok-config.txt | sort -u > lombok-config-keys.txt
jq -r '.requests[].method, .notifications[].method' lsp-3.18.2-metaModel.json | sort -u > lsp-3.18-methods.txt
javap -classpath org.eclipse.lsp4j-1.0.0.jar -public org.eclipse.lsp4j.services.LanguageServer org.eclipse.lsp4j.services.TextDocumentService org.eclipse.lsp4j.services.WorkspaceService org.eclipse.lsp4j.services.LanguageClient
sha256sum <all inputs and generated inventories>
```

## Status

- P01-05/P01-06 research repair: IMPLEMENTED, awaiting independent reviewer.
- P20 full semantic feature registry and production implementation: NOT_IMPLEMENTED.
- LSP per-method Javelle support/capability matrix and black-box handlers: NOT_IMPLEMENTED.

Primary sources: [Lombok download](https://projectlombok.org/download), [Lombok v1.18.48 tag](https://github.com/projectlombok/lombok/tree/v1.18.48), [LSP 3.18 spec](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/), [LSP4J v1.0.0 release](https://github.com/eclipse-lsp4j/lsp4j/releases/tag/v1.0.0), [Maven Central LSP4J 1.0.0](https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/1.0.0/).
