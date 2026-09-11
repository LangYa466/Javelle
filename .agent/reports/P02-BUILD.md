# P02-W02 Build／Monorepo 實作報告

## 契約

- Agent：`/root/p00_repair`
- 基準 revision：`f4e67788ea0bff07c2ccbfcc9164372974324bc6`
- 範圍：P02 固定 15-project Gradle monorepo、依賴邊界、共用 Java 建置慣例、可信快速驗證與未完成 gate 的 fail-closed 行為。
- 未修改：協調總帳、checklist、`.idea/vcs.xml`；未 commit。

## 實作結果

1. `settings.gradle.kts` 明列 15 個 contract project，並以 included build 載入 `build-logic`。
2. `org.javelle.java-conventions` 固定 Java toolchain 25、`--release 21`、UTF-8、`-Xlint:all -Werror`、決定性 test locale/timezone 與可重現 JAR 設定。
3. 每個 project 有可編譯的 module-boundary API；production `implementation` 邊完全符合 P02-CONTRACT 固定 DAG。根任務 `verifyArchitecture` 比較完整期望圖、偵測 cycle、禁止核心層 import Gradle/IntelliJ/LSP 類別。
4. `testkit` 提供有 timeout 與輸出上限的外部 process runner、archive inspector，並以真實子程序、timeout、錯誤 Java 編譯 fixture 測試。
5. Gradle plugin 以隔離外部 compiler executable 為契約：未設定或不可執行會明確失敗，不將 compiler internals 載入 Gradle classloader。
6. `verifyQuick` 執行所有 project 的 compile/test/Spotless、architecture 與 manifest 基線檢查；`verifyAll`、`releaseCheck` 對尚未實作的功能族列出結構化 `INCOMPLETE` 並非零退出。

## 真實驗證

| 命令 | Exit | 結果 | 證據 |
|---|---:|---|---|
| `./gradlew --write-verification-metadata sha256 projects` | 0 | included build 與 15 projects 可解析 | `.agent/logs/P02-BUILD/projects-bootstrap-5.txt` |
| `./gradlew --dependency-verification=strict spotlessApply` | 0 | 所有新增 Java 來源套用固定 formatter | `.agent/logs/P02-BUILD/spotlessApply.txt` |
| `./gradlew --dependency-verification=strict verifyQuick` | 0 | `BUILD SUCCESSFUL`; 65 tasks（61 executed, 4 up-to-date）；testkit 正／反／timeout fixture 通過 | `.agent/logs/P02-BUILD/verifyQuick-final.txt` |
| `./gradlew --dependency-verification=strict verifyAll` | 1 | 預期 fail-closed；列出 7 個尚未實作 gate | `.agent/logs/P02-BUILD/verifyAll-incomplete.txt` |
| `./gradlew --dependency-verification=strict releaseCheck` | 1 | 預期 fail-closed；列出 verify-all、archives、SBOM、consumer、metadata | `.agent/logs/P02-BUILD/releaseCheck-incomplete.txt` |

## 限制與下一依賴

- 本包只證明 P02 build baseline，不聲稱 compiler、LSP、IDEA、網站或 release 已完成；因此 `verifyAll`/`releaseCheck` 必須保持紅燈。
- `verifyManifests` 目前只做 tracked JSON object-boundary 基線；完整 schema/語意驗證仍依賴專屬 P02 schema 工作包。
- 尚待獨立 reviewer 重現 `verifyQuick`、檢查 DAG 反例並決定 ACCEPT/REJECT。
