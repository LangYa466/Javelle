# P02-R03 Testkit 修補報告

## 契約與範圍

- Agent：`/root/p00_repair`
- Requirement：P02-07
- 寫入僅限 `testkit/**` 與本報告；未修改 root build、build-logic、總帳或 `.idea/vcs.xml`。

## 實作

- `ProcessRunner`：真實子程序、獨立 stdout/stderr、exit code、timeout、強制終止後的有界等待、每串流 byte cap，以及嚴格 UTF-8 解碼。
- `TemporaryWorkspace`：OS temp root、正規化 traversal 防護、遞迴確定性清理。
- `DeterministicArchive` / `ArchiveInspector`：排序與固定 timestamp 的決定性 JAR、路徑 traversal/反斜線、symlink container、重複名稱、entry count、未知/總解壓大小與 overflow 防護。
- `JavaConsumer`：以真實外部 `javac` 編譯暫存 consumer，再以真實 `java` 執行；編譯錯誤保留原生 diagnostics。
- `ClassInspector`：以 reflection 驗證 public method、參數與回傳型別契約。

## 驗證證據

命令：`./gradlew --dependency-verification=strict :testkit:spotlessApply :testkit:test`

- Exit code：0
- 結果：BUILD SUCCESSFUL，14 tests、0 failures、0 errors、0 skipped。
- 分類：broken compiler fixture 1；process exit/streams/timeout/input/output cap/UTF-8 5；workspace/archive/JAR/reflection/consumer 正反與邊界 8。
- 原始輸出：`.agent/logs/P02-TESTKIT-test.txt`
- JUnit XML：`testkit/build/test-results/test/TEST-*.xml`

## 尚待審查

- Symlink 建立若平台不支援會由該測試返回；本次 Linux 環境實際執行且 JUnit 顯示 0 skipped，但仍需其他 OS matrix。
- 實作者未自行 ACCEPT；須由獨立 reviewer 重跑主要驗收並補 archive/class 反例。
