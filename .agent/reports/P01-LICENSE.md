# P01-W02 — 授權與供應鏈基線

- Agent: `/root/p00_spec`
- Checked: 2026-09-11 (Asia/Taipei)
- Base: `NO_GIT_REPOSITORY`; current root build declares `group=io.langya`, `version=1.0-SNAPSHOT`, JUnit BOM 6.0.0
- Scope: P01、C7、I、AGENTS §§6–7；研究報告，不構成法律意見或相容性保證

## 已核驗的不變結論

1. Teyru 自有工具程式碼的規定 SPDX expression 是 `GPL-2.0-only WITH Classpath-exception-2.0`。SPDX 將舊 `GPL-2.0-with-classpath-exception` 列為 deprecated，現行表達方式應使用 license + `WITH` exception。
2. OpenJDK `LICENSE` 同檔包含 GPL v2 與 Classpath Exception，但 exception 明示只套用到權利人已在特定來源檔 header 指定的檔案；因此不能以 OpenJDK LICENSE 存在就替任何第三方或未標示檔案套用 exception。
3. Project Lombok 1.18.48 官網標為 current release/MIT；其 tag LICENSE 也包含 bundled component notices。Teyru production compiler 不得依賴 Lombok processor；oracle jar、移植/重用的 annotation definition、fixture 或原始碼各自保留對應 copyright/license/notice。
4. GPL 本文說明 compiler 的輸出只有在包含/衍生自受保護程式碼時才可能受其涵蓋；這不是自動豁免。每個 emitted template、runtime helper、compat annotation、source/Javadoc JAR 都需 provenance 檢查。

## 官方來源與可執行 pins

| 項目 | 官方來源 / 建議 pin | 本次下載 SHA-256 |
|---|---|---|
| OpenJDK GPLv2+CE 完整文本參考 | `https://raw.githubusercontent.com/openjdk/jdk/master/LICENSE`；正式納入前改 pin 到 P01 選定 JDK 25 tag/commit | `4b9abebc4338048a7c2dc184e9f800deb349366bdf28eb23c2677a77b4c87726` |
| SPDX GPL-2.0-only 文本 | `https://raw.githubusercontent.com/spdx/license-list-data/main/text/GPL-2.0-only.txt`；正式納入前 pin commit | `aaf135472f81c5b4a0dca9367e5bb5e9750032b5bebe5442b36e4c0a47430df3` |
| SPDX Classpath exception record/text | `https://raw.githubusercontent.com/spdx/license-list-data/main/json/exceptions/Classpath-exception-2.0.json`；正式納入前 pin commit | `1da5f30a136e02ed2465305246fd0761c997a2fbd26829c87d85c23505e3ef06` |
| Lombok oracle | `https://projectlombok.org/downloads/lombok-1.18.48.jar`; tag `v1.18.48` | `85477a4655ebb2c074a9099cfb749be454449fee564d4282610df1b85f7c508b` |
| Lombok tag LICENSE | `https://raw.githubusercontent.com/projectlombok/lombok/v1.18.48/LICENSE` | `76479448741d7a7a3a97b6afd9ab9699d95621faafc65a1b8e9149342ea00feb` |

Evidence copies are under `.agent/logs/P01-LICENSE/`. Mutable `master/main` downloads are research snapshots only; release inputs must use immutable Git commit URL plus recorded hash. Lombok artifact should also be verified against Maven Central checksum/signature when P01 baseline is accepted.

## 產物／生成輸出授權矩陣

| 類別 | 預設處理 | releaseCheck 必查 |
|---|---|---|
| Teyru compiler/CLI/LSP/Gradle/IDEA/migration 自有源碼與 binary | GPL-2.0-only WITH CE；適用源檔有 SPDX header，distribution 附完整 GPLv2 與 CE | source offer/對應源碼、LICENSE、copyright、例外適用範圍、binary/source JAR 一致 |
| 生成 `.java` / `.class` | 使用者程式授權不因「執行 compiler」自動改變 | 禁止生成內容複製 Teyru/第三方受保護實作；模板/helper provenance manifest；golden sample 授權分開 |
| tiny `runtime` helper | 不得藏起；預設同專案政策，若採別授權需權利人明確批准 | ABI、runtime dependency、package manifest、source、NOTICE、consumer 缺 class 測試 |
| `compat-annotations` | 優先隔離 artifact；若重用 Lombok MIT definitions，保留 MIT notice/原 copyright，不綁 processor | class collision、不得同時打包重複 `lombok.*`、target/retention、compile-only/runtime 行為、NOTICE |
| Lombok oracle/delombok fixtures | 僅 test/migration controlled path；不進 production compiler/distribution | jar hash/tag、MIT + bundled component notices、fixture provenance、release archive absence assertion |
| 第三方 runtime/compile/test/build deps | 保留上游 license/NOTICE；逐 configuration 分類是否 shipped | resolved graph、artifact hashes/signatures、transitives、modified files、NOTICE obligations、source/binary bundling |
| Gradle wrapper/start scripts | Gradle upstream Apache-2.0；視為第三方 generated tooling，不重標 Teyru license | wrapper jar/distribution checksum、upstream notice/source、與 GPLv2-only 組合/分發需專業審查，不自行下法律結論 |
| docs/website/examples/fixtures | 依專案政策建立 manifest；不能擅自 public-domain 化 | copied snippets/assets/fonts/search indexes、attribution、i18n content、npm transitive licenses |
| RC archives/SBOM/notices/checksums | 每個實際 archive 對應一份 manifest/SBOM | SBOM component/version/hash/purl/license、nested jars/npm assets、NOTICE coverage、no absolute/private paths |

## 目前/候選第三方授權清冊

- 已觀察：Gradle/Wrapper 為 Apache-2.0；JUnit 6.0.0 專案標示 EPL-2.0。測試依賴通常不隨 runtime 發行，但仍須在 development/test SBOM 與 notices 表示。
- 候選 SBOM generator：CycloneDX Gradle plugin 3.x 為 Apache-2.0，官方文件稱支援 Gradle 8.4+ 並生成 CycloneDX JSON/XML。不要直接採 mutable README 顯示版本；由 P01 build owner pin release/tag/plugin artifact hash後再導入。
- 未選定的 parser、JSON、LSP、IntelliJ Platform、Astro/Starlight/npm 套件不可預先標示「相容」。選版後輸出 resolved direct+transitive inventory，逐一確認 license expression、NOTICE、source availability、是否 bundled/shaded。
- `group=io.langya` 只是本地建置設定；不證明 Maven namespace/Plugin Portal ownership。`dev.teyru` 只允許 internal package root，公開 coordinates 仍是外部 gate。

## 供應鏈與 SBOM 驗收

1. 釘選 Gradle wrapper distribution SHA-256、Maven/npm lockfiles、plugin versions；啟用 Gradle dependency verification metadata（checksums，能使用時再加 signatures），禁止 dynamic/latest 與 `curl | sh`。
2. 生成至少 CycloneDX JSON（建議同時 XML）aggregate + per-distribution material inventory；SBOM 要基於 resolved graph，且把 shaded/nested/前端資產/runtime/compat annotations 納入。
3. 對 compiler CLI、LSP、Gradle marker/plugin、IDEA ZIP、source/Javadoc JAR、website、offline AI package 分別解包核對 SBOM/NOTICE/manifest；以 archive 真內容反查，不能只相信 dependency declaration。
4. `releaseCheck` 驗證 license expression 格式、完整 LICENSE/exception/THIRD_PARTY_NOTICES、每個 component 的 version/hash/license/purl、Lombok oracle 未滲入 production、runtime 依賴透明、source offer/源碼包可取得。
5. 保存掃描工具版本、命令、exit code、raw reports；unknown/no-assertion、license conflict、缺 notice/source、hash mismatch 均為 release blocker，不可自動改綠。

## 外部發布與授權 gates

- 未授權：push/建立 remote、GitHub release、Maven Central/namespace、Gradle Plugin Portal ID、JetBrains Marketplace、npm/網站 hosting、production domain/DNS、公開 artifact/SBOM/source upload。
- 另需使用者明確授權與實際憑證：各平台帳號/token、signing key/passphrase、namespace/domain ownership、release approver、商標/名稱確認；秘密不可寫入 repo/log/SBOM。
- 本地可繼續：產生 unsigned/local RC、checksums/SBOM/notices、乾淨 consumer install、publish-to-local/dry-run。簽章與公開發布維持 `NOT_AUTHORIZED`。
- 法務 gate：Apache-2.0 build/generated assets 與 GPL-2.0-only WITH CE 的分發組合、任何第三方原始碼移植、compat annotations 的重用範圍、輸出模板/ runtime 授權，需正式 release 前由合格 reviewer 判斷；本報告不作法律保證。

## 可重現命令

```bash
sha256sum .agent/logs/P01-LICENSE/*
unzip -p .agent/logs/P01-LICENSE/lombok-1.18.48.jar META-INF/MANIFEST.MF
jar tf .agent/logs/P01-LICENSE/lombok-1.18.48.jar | sort
./gradlew dependencies
./gradlew --write-verification-metadata sha256 help
```

後兩項僅是 P01/P02 建議入口，尚未在本包執行或批准；寫 verification metadata 是 build owner 的 owned-path 變更。

## 結論與未完成

- `P01-08` 已建立可執行 baseline 建議與 blockers，但未達 VERIFIED：還需確定全部依賴/產物、不可變 source commits、完整 license files 落庫、archive/SBOM scan 與獨立 reviewer。
- `P01-05` 只能建議 Lombok 1.18.48 為 2026-09-11 current baseline；採用決定、Maven Central 二次 hash/signature、API/config inventory 屬 P01/P20 owner。
- release 状态應維持 `NOT_AUTHORIZED`；本地開發與 dry-run 可繼續。

## 官方來源

- OpenJDK LICENSE: https://github.com/openjdk/jdk/blob/master/LICENSE
- SPDX license list: https://spdx.org/licenses/ ; exception: https://spdx.org/licenses/Classpath-exception-2.0.html
- Lombok download/version: https://projectlombok.org/download ; tag LICENSE: https://github.com/projectlombok/lombok/blob/v1.18.48/LICENSE
- Gradle dependency verification: https://docs.gradle.org/current/userguide/dependency_verification.html ; Gradle LICENSE: https://github.com/gradle/gradle/blob/master/LICENSE
- JUnit: https://github.com/junit-team/junit-framework
- CycloneDX Gradle plugin: https://github.com/CycloneDX/cyclonedx-gradle-plugin
