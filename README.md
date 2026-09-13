# Teyru

**Teyru 是一門獨立實作的程式語言，編譯器完全用 Go 撰寫，直接產生原生執行檔——不依賴 JVM、不依賴 javac、不產生任何 bytecode。**

Teyru 的語法對 Java 開發者高度熟悉（類別、介面、泛型、lambda、例外、record、enum、annotation），
但拿掉了分號，補上原生 property，並且用**原生機器碼**執行：編譯器把整個程式降成 C，
再由 clang/LLVM（或 gcc）編成執行檔。執行時只有一個 4 KB 等級的執行期，裡頭有自己的
垃圾回收器（conservative mark-and-sweep）、字串、陣列與例外實作。

```
Teyru 原始碼 (.teyru)
      │  Go 寫的編譯器：lexer → parser → 語意分析 → C 產生器
      ▼
  generated C  ──clang/LLVM──▶  原生執行檔（無 JVM、無 bytecode）
```

---

## 為什麼比 JVM 快

同一個 `Hello` 程式，在同一台機器上實測（100 次執行）：

| 指標 | Teyru（原生） | Java（HotSpot） | 差距 |
|---|---|---|---|
| 啟動 100 次總時間 | **0.075 s**（0.75 ms/次） | 2.00 s（20 ms/次） | **約 27 倍快** |
| 執行檔／執行期大小 | **56 KB** | JDK 執行期約 200 MB | 約 3600 倍小 |
| 尖峰記憶體 | **4.4 MB** | 50.9 MB | **約 11 倍省** |
| `fib(32)` 遞迴 | **6 ms** | 27 ms | 約 4.5 倍快 |
| 編譯時間 | 數十毫秒 | javac 較慢且需 JIT 暖機 | — |

**為什麼會快：**

1. **沒有 JVM 啟動成本。** 沒有 class loading、沒有 JIT 暖機、沒有 GC 執行緒啟動。
   適合 CLI 工具、短命 process、容器啟動、serverless。
2. **編譯期就做完的事不留到執行期。** 泛型在編譯期抹除、方法呼叫在編譯期定址、
   字串常數靜態配置、`static final` 常數直接折疊。執行期是純機器碼。
3. **沒有 bytecode 解譯階段。** clang/LLVM 直接最佳化整份程式（跨方法 inline、
   常數傳播、迴圈向量化），不需要等 JIT 觀察熱點。
4. **可預測的效能。** 沒有 deopt、沒有暖機曲線、沒有 GC 調校參數，第一次執行
   就是最快速度。

**誠實的邊界：** 在「大量短命物件」的 microbenchmark 上，HotSpot 的逃逸分析可能
直接把物件消除（scalar replacement），此時 JVM 反而會贏（實測 20M 次配置：Teyru
0.13 s vs JVM 0.03 s）。Teyru 目前的 GC 是保守式標記清除，不是分代複製式；
這是後續最佳化的重點，也是我們不宣稱「所有情境都比較快」的原因。

---

## 快速開始

需要 **Go 1.24+** 與 **clang**（或 gcc）。

```sh
# 建置編譯器
go build -o teyru ./cmd/teyru

# 編譯並執行
./teyru run hello.teyru

# 產生執行檔
./teyru build -O2 -o hello hello.teyru
./hello

# 看編譯器產生的 C 程式碼
./teyru emit hello.teyru

# 版本
./teyru version
```

`hello.teyru`：

```teyru
class Hello {
  public static void main(String[] args) {
    System.out.println("Hello, Teyru!")
  }
}
```

注意：**Teyru 不用分號**。每個敘述以換行結束；`for` 標頭用兩個冒號分隔三段。

---

## 語言速覽

```teyru
interface Shape {
  double area()
  default String describe() {
    return "area=" + area()
  }
}

class Rect implements Shape {
  public double w
  public double h
  public Rect(double w, double h) {
    this.w = w
    this.h = h
  }
  public double area() {
    return w * h
  }
}

class Circle implements Shape {
  private double r
  public double radius {
    get {
      return field
    }
    set {
      field = value < 0 ? 0 : value
    }
  }
  public Circle(double r) {
    radius = r
  }
  public double area() {
    return Math.PI * r * r
  }
}

record Point(int x, int y) {
}

enum Color {
  RED, GREEN, BLUE
}

class Main {
  static int twice(int v) {
    return v * 2
  }

  public static void main(String[] args) {
    Shape s = new Rect(3, 4)
    System.out.println(s.describe())

    // for ( init : condition : update )
    for (int i = 0 : i < 3 : i++) {
      System.out.println(i)
    }

    int[] xs = {1, 2, 3}
    for (int x : xs) {
      System.out.print(x)
    }
    System.out.println()

    // lambda 與方法參照
    Fn<Integer> f = (v) -> v + 1
    Fn<Integer> g = Main::twice
    System.out.println(f.apply(41))
    System.out.println(g.apply(21))

    // switch 運算式
    Color c = Color.GREEN
    String name = switch (c) {
      case RED -> "red"
      case GREEN -> "green"
      default -> "other"
    }
    System.out.println(name)

    // 例外
    try {
      System.out.println(10 / 0)
    } catch (ArithmeticException e) {
      System.out.println("除以零")
    } finally {
      System.out.println("cleanup")
    }
  }
}

interface Fn<R> {
  R apply(int v)
}
```

### 支援的語言特性

| 類別 | 內容 |
|---|---|
| 型別 | 原生型別、類別、介面、enum、record、annotation type、泛型（含 bound 與 wildcard）、陣列（含多維） |
| 成員 | 欄位、方法、建構子、靜態／實例初始化區塊、巢狀／內部／區域／匿名類別、`sealed`／`permits` |
| 陳述式 | `if`、`while`、`do-while`、基本與增強 `for`、`switch`（語句與運算式、箭頭與冒號、`case` 多標籤、型別 pattern）、`try`/`catch`/`finally`、try-with-resources、`throw`、`yield`、`assert`、`synchronized`、標籤與 `break`/`continue` |
| 運算式 | 完整運算子與優先序、三元、cast、`instanceof`（含 pattern）、lambda、方法參照、`new`／匿名類別、字串串接、boxing／unboxing、物件初始化列表 |
| 原生擴充 | `val`（不可重綁的推斷區域變數）、`var`、原生 property（`get`/`set`、`field`）、無分號語法、`for` 的雙冒號標頭、try-with-resources 以換行分隔 |

### 標準程式庫（以 Teyru 撰寫）

`Object`、`String`、`StringBuilder`、`Math`、`System`、`PrintStream`、`Iterable`／`Iterator`、
`Comparable`、`AutoCloseable`、`Cloneable`、`Enum`、`Record`、八種原生包裝類別，
以及 `Throwable` 家族的例外類別。程式庫原始碼就是 Teyru 原始碼
（`internal/prelude/prelude.go`），每一次編譯都與使用者程式一起被編譯。

---

## 專案結構

| 路徑 | 說明 |
|---|---|
| `cmd/teyru` | CLI 進入點（`build`／`run`／`emit`／`version`） |
| `internal/source` | 檔案、位置、診斷 |
| `internal/lexer` | 詞法分析；換行不產生 token，只標記「前面有換行」 |
| `internal/parser` | 遞迴下降剖析器，用前瞻判斷敘述是否結束 |
| `internal/ast` | 語法樹、符號、型別 |
| `internal/sema` | 名稱解析、型別檢查、泛型抹除、方法多載、vtable／selector 配置、property 降階 |
| `internal/codegen` | 產 C：類別→struct、虛擬呼叫→vtable、介面呼叫→itable、GC 有根掃描資訊 |
| `internal/runtime/src` | C 執行期：GC、字串、陣列、例外、boxing、Math／System |
| `internal/prelude` | 以 Teyru 撰寫的標準程式庫 |
| `tests/programs` | 端到端測試程式與期望輸出（`go test` 會逐一編譯並比對） |
| `bench` | 與 JVM 對照的效能腳本 |

### 執行期模型

- **物件**：C struct，第一欄是 `tyobj { tyclass* cls }`；每個類別一張 `tyclass`
  記錄父類、介面、vtable、介面表、GC 需要追蹤的參考欄位位移。
- **虛擬呼叫**：`obj->cls->vtable[slot]`；**介面呼叫**：`ty_itab(obj, selector)`。
- **介面**：每個介面方法有全域唯一的 selector，每個類別的 itable 由編譯期填好。
- **泛型**：編譯期抹除（erasure），執行期沒有泛型資訊，與 Java 相同。
- **例外**：`setjmp`/`longjmp` 為基礎的 handler 鏈，`finally` 以巢狀 handler 保證執行。
- **GC**：保守式標記清除。根包含原生堆疊（保守掃描）、靜態欄位註冊表與暫存器
  （`setjmp` 溢出）。位址不搬移，因此 C 端暫存指標永遠有效。
- **字串**：UTF-8，`tystr { tyobj; int64 len; char* data }`；字面值在編譯期配置為靜態物件。

---

## 與 Java 的差異

Teyru 不是 Java 的子集，而是「Java 開發者一看就懂」的獨立語言。主要差異：

1. **沒有分號。** 分號會直接被編譯器拒絕（`TY-SYN-0001`）。
2. **`for` 標頭用冒號**：`for (int i = 0 : i < n : i++)`。
3. **try-with-resources 用換行分隔**，不用分號。
4. **enum 常數與成員之間用一個冒號**分隔（沒有成員時可省略）。
5. **原生 property**：欄位後面接 accessor 區塊即成 property；`field` 代表底層儲存。
   沒有 accessor 區塊的欄位就只是普通 Java 欄位。
6. **`val` 是新的**：推斷型別的不可重綁區域變數（不是深度不可變）。
7. **沒有 `System.out.printf`、沒有執行期反射、沒有 annotation processor**；
   annotation 會被解析與保留，但不會有 Lombok 那種 AST 注入。
8. **不是 bytecode 平台**：沒有 `.class`、沒有 `java.lang`、沒有 JNI。
   目前也**無法**與既有的 Java 程式庫互通——這是刻意的取捨。

### 目前不支援

- checked exception 的編譯期檢查（`throws` 會被解析但不強制）
- `interface` 的 private method 已有解析，但 `sealed` 家族未做窮盡性檢查
- 反射、thread、`java.util` 集合類別
- 與 Java 生態互通（JAR、JDK 類別庫）

---

## 開發

```sh
go build ./...          # 建置
go test ./...           # 端到端測試（會編譯 tests/programs 下每個程式）
go vet ./...
sh bench/bench.sh       # 與 JVM 對照的效能測試（需要 java 才會跑 JVM 那一半）
```

新增一個測試只要在 `tests/programs/` 放 `xxx.teyru` 與 `xxx.expected`；
`go test` 會自動編譯並比對輸出。

## 授權

見 `LICENSE`。
