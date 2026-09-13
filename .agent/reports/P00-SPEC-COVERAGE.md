# P00 規格索引與需求覆蓋建議

- Task/agent: `P00-SPEC-COVERAGE` / `/root/p00_spec`
- Base revision: `NO_GIT_REPOSITORY`
- Fingerprints: `AGENTS.md=2a6e74a4…f867f01d`; plan=`1157bffd…f298389`
- Read scope: `AGENTS.md` 全文；實作規範 A–I、P00–P55、UAT-01–36 全部章節
- 性質：唯讀恢復判斷與覆蓋索引；未驗證產品功能

## 執行入口與不變決策

1. P00 實證 subagent、有效指示、環境、Git/dirty state 與恢復能力；P01 鎖定官方版本/checksum/授權；P02 建 monorepo/帳本/測試入口；P03 凍結規範/ABI/ADR。
2. 再走 P04–P11 early end-to-end；IDEA smoke 必須早於完整 Lombok。其後依 D2 推進完整語言、生態、IDE、文件，P49–P55 統合驗收。
3. 每個 checkbox 都是必要驗收點並疊加共同 DoD；實作者與 reviewer 分離，只有獨立證據齊全才可 ACCEPTED。

- 身分固定：Teyru、`.teyru`、ID `teyru`、CLI `teyru`、server `teyru-lsp`。
- Java-first：Java SE 25 non-preview，另驗證 Java 21 profile；保留 Java 型別優先、泛型、例外、語義及互通，不引入 Kotlin 型別系統。
- `.teyru` 禁止語法 `;`，資料中的分號保留；basic-for 用兩個 `:`，TWR 以完整 resource 換行，enum 常量/成員用 `:`；由 grammar 決定，不得 regex 轉換。
- `var` 是可重賦值局部推斷，`val` 是 final 局部推斷；不可作 field/parameter/return type；無 target 的 null/lambda/reference 不可推斷。
- 只有 accessor block 是 property；普通 field/bean getter 不自動轉 property。backing field private；accessor 預設繼承 property visibility；`field` 僅在 accessor contextual binding。
- 後端固定 `.teyru→AST/semantic/lowering→readable .java→javac→.class`；輸出決定性、可追蹤。必要 runtime helper 必須明示 ABI/依賴/授權。
- Lombok 鎖定 baseline，以原生 AST/symbol/lowering 實作全部公開 feature/option/config/組合；官方 Lombok 僅作 oracle，production path 禁用其 processor/AST injection。
- 單一 editor-neutral language tooling；LSP 獨立於 IDEA，IDEA 是 client/projection/run-debug adapter。source map、LSP positions、debug mapping 是不同契約。
- 自有工具授權固定 `GPL-2.0-only WITH Classpath-exception-2.0`；第三方逐項審核。發布、網域、namespace、signing 是額外授權 gate。

## 依賴圖

```text
P00→P01→P02→P03→P04→P05→P06→P07
                      └→P08→P09→P10→P11
P03/P04→P12→P13→P14→P15→P16→P17→P18→P19
P01/P06/P17→P20→P21→P22→P23→P24→P25→P26→P27→P28→P29
P08/P16/P17/P19→P30→P31→P32→P33→P34→P35→P36
P09/P19/P29→P37→P38→P43→P39
P11/P30/P34/P36/P38→P40→P41→P42
P03/P19/P29/P38→P43→P44
P03/P11→P45→P46→P47→P48
全部相關驗收→P49→P50→P51→P52→P53→P54→P55
```

精確 phase 依賴優先；P43 是 P39/P44 前置之一，P45 可提早搭建但完整內容依賴 P44。

## P00–P55 索引

| 範圍 | 階段 |
|---|---|
| P00–P11 基礎 | 環境/subagent；版本授權；monorepo/帳本；spec/ABI/ADR；核心模型/位置；真前端；emitter+javac；CLI 初版；workspace model；Gradle slice；LSP slice；IDEA smoke |
| P12–P19 語言 | lexer/recovery；declarations/types；expressions/control/lambda；semicolon/formatter；Java resolver；mixed compile/AP；property semantics；emitter/source-map/JAR metadata |
| P20–P29 Lombok | baseline/oracle/inventory；accessor/non-null；constructors/value；builder；super-builder/with；lazy/cleanup/exceptions/locks；logging/defaults/utility；delegate/extensions/helper/metadata；onX/Jacksonized；config/full acceptance |
| P30–P43 工具 | shared tooling/index；completion/nav/hover/signature；diagnostics/fixes/format；references/rename/files；advanced LSP；protocol resilience/security；non-IDEA clients；Gradle production；ecosystem consumers；CLI full；IDEA lifecycle；Java deep interop；debug；migrator |
| P44–P55 交付 | executable examples；docs/spec pipeline；website；AI docs/machine APIs；changelog/version；compatibility/fuzz；performance/resources；security/license/SBOM；CI/reproducibility；local RC；zero-start UAT；final state/commit |

## 逐章需求覆蓋表建議

| 章節 | 主工作包/角色 | 最低獨立驗收 |
|---|---|---|
| A1–A3 | P01/P03；recon/spec/build | pinned matrix、ADR、名稱/版本 schema、未授權 gate |
| B1 val/var/null | P03/P13/P16/P39；spec/compiler | 正反 type inference、Java21/25、CLI diagnostics |
| B2 無分號 | P03/P12–P15/P43；compiler/spec | Unicode/token/recovery、for/try/enum ambiguity、formatter+migrator roundtrip |
| B3 property | P03/P05–06/P16–19/P41–42；compiler/interop/ide | event order、reflection/ABI、cross-JAR metadata、Java consumer、debug |
| B4 Java/混編 | P13–17/P19/P37–38；compiler/interop/build | JLS inventory、clean cyclic mixed source、processor rounds、framework consumers |
| B5 Lombok | P20–29/P31/P38/P43；lombok/qa | hashes、全 API/config registry、oracle differential ABI/runtime/framework |
| C1–C2 modules/model | P02/P04/P08/P30；build/interop | architecture、schema compatibility/determinism、independent client |
| C3 Gradle/CLI | P07/P09/P37–39；build/compiler | external process/TestKit、cache/delete/relocate/toolchain、install dist |
| C4 LSP | P10/P30–36；lsp/qa | independent JSON-RPC、method/capability matrix、race/cancel/security |
| C5 IDEA/debug | P11/P40–42；ide/interop/qa | real IDE UI、Java projection/nav/refactor、breakpoint/step/locals |
| C6 docs/web/AI | P44–47；docs-web/spec/qa | executable examples、site browser/a11y/i18n、generated llms artifacts |
| C7 release/license | P01/P48/P51–55；build/integration/qa | version、SBOM/license、clean RC install、verifyAll/releaseCheck |
| D1–D3 | P00/P02/P49/P55；coordinator/integration/qa | task DAG/ownership、report schema、implemented/verified separation |
| E P00–P55 | 各 phase owner + 不同 reviewer | 每個 `Pxx-nn` 映射 implementation/test/evidence；不能只映射標題 |
| F UAT-01–36 | P54；全新 qa-review | 從 RC/文件獨立完成 build/run/CLI/LSP/IDE/debug/site/security |
| G | P00/P02/P55；coordinator/integration | 真 agent IDs、atomic state、NEXT_SESSION、stale lock audit |
| H | P55；coordinator+reviewer | COMPLETE_LOCAL 僅限全驗收；publication status 分離 |
| I | P01/P20/P45/P51；recon/owners | 重新查一手資料、日期/版本/hash/license |

Manifest 應以 A/B/C requirement、`Pxx-nn`、`UAT-nn` 單項粒度記 `ownerTaskId, implementationPaths, testIds, evidencePaths, reviewerAgentId, status`；未知/未跑維持 NOT_IMPLEMENTED/NOT_VERIFIED。

## 恢復狀態唯讀判斷

- `git rev-parse HEAD` 與 `git status --short` 均 exit 128：此目錄不是 Git repository；無 base commit、dirty state 或歷史可核實。本包不初始化 Git。
- 檢查時 `.agent/STATE.md`、`TASKS.json`、`OWNERSHIP.json`、`NEXT_SESSION.md` 均不存在，無舊 ACCEPTED/RUNNING 或 evidence 可恢復；規格要求的可恢復起點尚未成立。
- 已有 Gradle wrapper、根 build files、IDE metadata；存在檔案不是完成證據，不能推定 P02/P09 已完成。
- 建立本報告前沒有 ownership registry；委派訊息明確授予本路徑給 `/root/p00_spec`。除本報告外未寫產品碼或總帳。

## 一致性結論

目前只能判定 `P00 IN_PROGRESS`。canonical subagent `/root/p00_spec` 證明 spawn 能力，但 Git/dirty 基準、工具/指示/環境/等待 probes、帳本與獨立 review 未由本包驗證，不得將 P00 標為 ACCEPTED。
