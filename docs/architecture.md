# Teyru 編譯器架構

## 流程

```
來源檔 (UTF-8)
   │  internal/source      — 檔案、行號對應、診斷容器
   ▼
Token 串
   │  internal/lexer       — 關鍵字、運算子、字面值、文字區塊
   │                        換行不是 token，只在 token 上標記 NL 旗標
   ▼
AST
   │  internal/parser      — 遞迴下降；游標 + 前瞻 + 選擇點回退
   ▼
已檢查的程式模型
   │  internal/sema        — 符號表、型別、泛型抹除、多載、佈局
   ▼
C 原始碼
   │  internal/codegen    — 類別→struct、vtable／itable、GC 根資訊
   ▼
原生執行檔
      clang/LLVM 或 gcc + internal/runtime/src（GC、字串、陣列、例外）
```

## 換行當敘述終止符

Teyru 沒有分號。詞法分析器不產生 NEWLINE token，而是在每個 token 上記錄
「前面是否有換行」。剖析器用兩件事判斷敘述是否結束：

1. **目前剖析位置的換行是否顯著**（`nl` 堆疊；括號內、引數列表內不顯著）。
2. **前綴是否已完整**。例如 `return` 後面直接換行就是無值 return，
   但運算子、逗號、`.`、`::`、`->` 之後的換行不終止敘述，
   行首是 `.`／`::` 時也視為延續。

`internal/parser/parser.go` 的 `continues()` 是唯一的判斷點。

## 型別與符號

- `ast.Type` 有六種：原生、類別（含型別引數）、陣列、型別變數、萬用字元、null／error。
- 泛型在 `sema.erasure` 抹除；執行期只知道類別，不知道型別引數。
- 多載解析（`pickOverload`）：先算每個候選的轉換成本（完全相同 0、拓寬／上轉 1、
  boxing 2、unboxing 3、varargs 另計），取總成本最低者。
- 方法的 vtable 槽位在 `layout()` 決定：由父類複製，覆寫者沿用同一槽位；
  介面方法另有全域唯一的 selector（`Selector`），供 itable 使用。

## 產生 C 的關鍵對應

| Teyru | C |
|---|---|
| 類別 `Foo` | `struct C_Foo { tyobj obj; ... }`（欄位依 `InstFields` 平鋪，含繼承） |
| 實例方法 | `M_<class>_<name>_<idx>(C_Foo* this, ...)` |
| 虛擬呼叫 | `this->obj.cls->vtable[slot](...)` |
| 介面呼叫 | `ty_itab(obj, selector)(...)` |
| `new Foo(...)` | GNU 敘述運算式：配置 → 設 `cls` → 呼叫建構子 |
| 陣列 | `tyarr { tyobj; len; data; esize; refs }`，元素內嵌 |
| 字串常數 | 靜態 `tystr`（不經 GC） |
| `try`/`catch` | `tycatch` + `setjmp`/`longjmp` |
| property 讀寫 | 降階成 getter／setter 呼叫（`sema.Props` 記錄） |
| `for (a : b : c)` | `for (init; b; c)` |
| 記錄 `Point(int x,int y)` | struct + 建構子 + `x()`/`y()` + `toString`/`hashCode`/`equals` |
| enum 常數 | 靜態欄位，於 `<clinit>` 建立並填入 ordinal／name |

## 效能設計

產生的 C 由 clang/LLVM 以 `-O2` 加 LTO 編譯（`--no-lto` 可關閉；不支援 LTO 的
工具鏈會自動退回），跨函式 inline、常數傳播與迴圈向量化都由 LLVM 負責。在此之上，
編譯器與執行期刻意讓熱路徑保持單一指令層級：

| 機制 | 位置 | 說明 |
|---|---|---|
| 行內配置 | `tyrt.h` 的 `static inline ty_alloc` | 指標碰撞（bump pointer）路徑完全內聯，只有區塊用盡或超過 GC 門檻才呼叫 `ty_alloc_slow` |
| 行內邊界檢查 | `codegen.boundCheck` | 檢查以敘述運算式內聯在取用點；索引為常數時由 `sema` 先摺疊，不產生多餘比較 |
| 常數折疊 | `codegen.foldBinary`、`ident` | 字面值運算、`static final` 常數、字串相加在編譯期算完 |
| 死 chunk 回收 | `tyrt.c` 的 sweep | 一個 chunk 內若沒有任何存活物件就整塊 `free` 還給系統，回收成本因此與存活量成正比，而不是與歷史配置量成正比 |
| 字串常數 | `codegen.strLit` | 字串字面值是靜態 `tystr`，不經配置、不進 GC |
| 類別初始化 | `ty_clinit` | 惰性初始化，且只在靜態成員存取與 `new` 時檢查 |

已知的效能邊界：GC 是保守式標記清除（無分代假設），因此「大量短命物件」的
microbenchmark 上會輸給 HotSpot 的逃逸分析。`bench_alloc` 是唯一落後的項目，
其餘四項（`fib`、`loop`、`oop`、`string`）皆快於 JVM；重現方式見
`sh scripts/bench.sh`。

## 垃圾回收

- **保守式標記清除**。物件不搬移，所以 C 端的暫存指標永遠有效。
- 根：shadow stack（供 C 端與未來使用）、註冊的靜態欄位位址表、
  以及**原生堆疊的保守掃描**（起點為當前堆疊指標，終點為執行緒堆疊頂端，
  由 `pthread_getattr_np` 取得）。
- 標記：`tyclass.refoffs` 列出每個類別需要追蹤的參考欄位位移；陣列用 `refs` 旗標。
- 清除：未標記的區塊進入大小分級的 free list；下一次配置優先重用。完全空掉的
  chunk 直接 `free` 還給系統，所以長時間執行的程式不會一直佔住尖峰記憶體。
- 觸發：配置量超過 `gc_threshold`（初始 4 MB，每次回收後設為存活量的兩倍）。
- 已知代價：每次回收都要掃描整個使用中的堆疊，且沒有分代假設。

## 例外

`ty_cur_catch` 是一條 handler 鏈。`throw` 呼叫 `ty_throw`，後者 `longjmp` 到最近的
handler；沒有 handler 時印出訊息並以狀態 1 結束。

`finally` 有兩條路徑，缺一不可：

1. **例外路徑**：`try` 外層包一個 handler，`setjmp` 回來後先跑 `finally`，
   再把例外重拋。catch 區塊內再拋出時也走同一條路。
2. **正常離開路徑**：`return`、`break`、`continue` 不會經過 `longjmp`，
   所以程式碼產生器維護一個 finally 堆疊（`Emitter.finallys`），在每個
   跳躍敘述前先跑完被離開的 `finally`（由內而外），最後才跳。
   `try`-with-resources 的 `close()` 是同一個機制的隱含 `finally`，
   因此資源在 return 與例外兩條路徑上都會關閉。

區域與匿名類別捕獲的區域變數會變成合成類別的欄位（`Class.CapFields`），
由建構子或 closure 建立運算式填入；這讓「方法參考的接收者」也只在建立時求值一次。
