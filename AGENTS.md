# AGENTS.md — Teyru 專案工作規範

> 適用：本儲存庫內所有人類貢獻者與 Coding Agents。版本 2.0；2026-09-13 改寫（v1.0 為 Java/JVM 時期規範，已作廢）。

## 0. 產品契約

- 語言名稱固定 **Teyru**；副檔名 `.teyru`；CLI `teyru`；Go module `github.com/LangYa466/Teyru`。
- **編譯器用 Go 撰寫，只使用標準函式庫。** 不得引入第三方 Go 模組（`go.mod` 目前無 require，請維持）。
- **不依賴 JVM、javac、bytecode。** 產物是原生執行檔。任何形式的「產生 Java 再交給 javac」、
  「產生 bytecode」、或執行期需要 JVM 的設計都視為違反產品契約。
- 主要編譯路徑固定：`.teyru → Go 前後端 → C → clang/LLVM 或 gcc → 原生執行檔`。
  執行期在 `internal/runtime/src`，以 C 撰寫。
- 標準程式庫以 **Teyru 本身**撰寫（`internal/prelude/prelude.go`），不得改成 Go 或 C 實作，
  除非是無法用 Teyru 表達的原生操作（此時標記 `native` 並在 runtime 提供）。
- 效能是產品目標：宣稱必須有可重現的實測（`bench/bench.sh`），不得憑感覺或只挑有利情境。

## 1. 事實與證據

1. 不得偽造測試、效能、部署或相容性結果。跑不動就說跑不動。
2. 文件中的數字必須來自實際執行，並註明量測方式。
3. 「支援某語言特性」的定義是：`tests/programs/` 有對應程式且 `go test ./...` 通過。
   只會剖析不算支援。

## 2. 程式碼風格

### Go
- 依照標準 `gofmt` / `go vet`；提交前兩者都要乾淨。
- 套件邊界：`source`（檔案與診斷）→ `lexer` → `parser` → `ast` → `sema` → `codegen`。
  不允許反向依賴。
- 不使用全限定名稱、不用 wildcard import；錯誤用 `diags.Errorf` 回報，不要 panic。
- 註解解釋「為什麼」，不要逐行翻譯程式碼。

### C（執行期與產生的程式碼）
- 產生的 C 一律使用 `-std=gnu11`，並允許 GNU 敘述運算式（statement expression）。
- 執行期程式碼必須在 `-Wall -Wextra` 下無警告（`gcc -c -O2 -Wall` 檢查）。
- 物件配置一律走 `ty_alloc`；不得直接 `malloc` 需要被 GC 追蹤的物件。
- 新增類別欄位時，`tyclass.refoffs` 必須正確包含參考欄位位移，否則 GC 會漏追。
   產生器已按 C 的對齊規則計算（`alignOf`），修改欄位配置時務必同步。

## 3. 測試規範

- 端到端：在 `tests/programs/` 新增 `xxx.teyru` 與 `xxx.expected`，`go test` 會自動比對。
- 診斷：在 `driver_test.go` 的 `TestDiagnostics` 加入「應該被拒絕」的案例與錯誤碼。
- 修 bug 時先加一個會失敗的測試，再修。
- 提交前至少跑 `go build ./... && go vet ./... && go test ./... -count=1`。

## 4. 提交規範

- 遵循使用者全域規則：不要加 `Co-Authored-By: Claude` 或任何 AI 署名。
- commit 一律使用 `git -c user.email=langya466@gmail.com -c user.name=LangYa466 commit`。
- 訊息用 `feat(compiler):`／`fix(runtime):`／`test:`／`docs:` 這類範圍前綴。

## 5. 已知限制（不要當成已完成）

- checked exception 沒有編譯期檢查。
- `sealed` 家族沒有窮盡性檢查。
- 沒有反射、執行緒、`java.util` 集合。
- 與 Java 生態不相容（沒有 JAR、沒有 JDK 類別庫、沒有 JNI）。
- GC 為保守式標記清除，非分代；大量短命物件的情境仍落後 HotSpot 的逃逸分析。
