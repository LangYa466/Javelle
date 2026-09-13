# Teyru — 完整產品實作規範與逐階段執行清單

**文件版本：1.0 · 日期：2026-09-11（UTC+8） · 性質：需要真正執行的交付契約**

> 給 Codex／其他具備真正 subagent 能力的開發代理：你不是被要求再寫一份建議書，而是依本檔實現 Teyru。先遵守根目錄 `AGENTS.md`，再從 P00 開始，沿依賴圖完成全部必要工作。腳手架、可展示原型、MVP 和未驗證的功能矩陣都不是最終交付。
>
> 本文件所有 checkbox 初始均為未完成。階段編號用於追蹤，不代表必須依數字串行：例如 P43 遷移器需先於 P39 的完整 CLI 驗收完成。本文提到的 Teyru 命令、模組、功能、網址路由與任務名稱是**要建立的產品契約**，不代表目前已經存在。

## 閱讀與執行入口

主 session 讀 A～D 的總體契約與 E 的階段索引，不要將本檔與全部實作日誌反覆全文載入。將相關規範、工作包、必要附錄交給對應 subagent 逐段讀取。規範不能漏讀：主協調者建立 `requirement → owner → implementation → test → evidence` 覆蓋表，確保每章都有負責者與驗收者。

執行次序：**P00 能力／環境 → P01 版本與授權 → P02 儲存庫／協作 → P03 規範 → early end-to-end slice → 完整語言／工具／網站 → 總驗收**。IDEA 的第一次真實 smoke test 必須在完整 Lombok 開發之前完成，之後每個階段持續回歸，不可把 IDE 留到最後。

如果已有程式碼，先盤點並沿用正確部分，不要無條件重建。遇到沒有討論過的細節，以本文明確預設與最小 Java 語義差異決策，記入 ADR 後推進；不能藉機重設產品方向。

---

# A. 交付範圍與不可變決策

## A1. 已確定的產品身分

| 項目 | 本次規範 |
|---|---|
| 名稱 | **Teyru** |
| 主張 | **Familiar Java. Less ceremony.** |
| 核心方向 | Java-first，保留熟悉語法與語義，只移除已指定痛點 |
| 源檔／語言 ID | `.teyru`／`teyru` |
| 源碼風格 | Java 型別優先；沒有語法分號；保留大括號 |
| 新能力 | 原生 property；局部 `val`／`var`；完整釘選 Lombok 功能相容層 |
| 編譯後端 | 人類可讀 Java source，再交給 javac |
| 工具 | 獨立 CLI、獨立 LSP、Gradle 外掛、解耦 IDEA 外掛、遷移器 |
| 文件 | 正式語言規範、官網、API／錯誤碼、changelog、AI 可讀格式 |
| 自有工具主授權 | `GPL-2.0-only WITH Classpath-exception-2.0` |
| 主要工程語言 | Java；網站可 TypeScript；Gradle 建置 DSL 可 Kotlin DSL |
| 公開發布 | 先完成可發布產物與 dry-run；實際發布需另有明確授權 |

本次不新造 Kotlin 型別系統、coroutine、operator overloading、另一套 collection／stdlib、內建 AI 聊天服務或自製 JVM bytecode backend。這些不是「完整」的判斷條件，也不能拿來擴大範圍。

## A2. 版本策略與相容性口徑

以下是本次選定的工程基線，不是聲稱它們是永遠的「最新版本」：

- Java 語法與語義目標：**Java SE 25 的非 preview 語言特性**。需有 JLS 章節／feature inventory，不可只支援 Java 8 然後宣稱「大部分 Java」。同時驗證 Java 21 相容 profile；在 `--release 21` 下使用更高版本特性須有準確錯誤，不能靜默改變語義。[S03]
- 工具自身的最小執行 JDK、Gradle daemon JDK、Teyru compiler JDK、target release 與 IDEA 自帶 JBR 必須分開建模。P01 釘選可取得的 JDK 25 工作工具鏈；Java 21 profile 在對應工具鏈／API 下測試。IDEA client 不能因 server JDK 較新就與 IDE classloader 耦合。
- Gradle 與 IntelliJ Platform SDK／IDE 發行版：由 P01 官方相容性盤點選擇確定版本並鎖定。不得把當天頁面的示例版本直接當成最低相容版本。
- Lombok：在 P01 鎖定正式發行版、artifact SHA-256、來源 tag／commit，稱為 `lombokBaseline`。**完整支援**指本基線公開功能、參數、設定與受支援組合的可觀察行為相容，不是追逐未來版本或所有內部 bug。[S09][S10][S11]
- LSP：以官方 3.18 規範建立完整 method inventory，同時透過 capability negotiation 與常見較舊客戶端互通；不發明標準不存在的「LSP version handshake」。實際可用 API 依選定 library／client 版本驗證。[S05]
- 所有相容矩陣初始為 `NOT_IMPLEMENTED`／`NOT_VERIFIED`，以證據改成 `VERIFIED`。`PARTIAL` 不算完成；上游明確不合法的情境應通過負面測試，而不是歸類為缺功能。
- 「全部 Lombok」不包含替代 Lombok 的舊 IDE 安裝器或 patch 它所有歷史版本 javac 的機制；Teyru 必須實現全部語言功能、對應設定、必要 utility／annotation 相容與遷移能力。這個邊界必須公開，不能拿它刪除難做的 annotation。

## A3. 本文件補齊的設計決策

前期對話未定義所有邊界。為使 agent 不必來回詢問，本規範採以下可執行預設；先寫成 ADR，再實現。使用者日後可明確變更，但 agent 不可為省事自行改寫：

1. accessor 未寫可見性時，**繼承 property 宣告的可見性**；外層 `private` 不會偷偷生出 public API。可在 accessor 上明確擴大／縮小可見性，如 `public get`。
2. 普通 Java 欄位不自動成為 property；Java library 的 `getX()` 也不在預設模式下自動變成 `x`。
3. 傳統三段 `for` 的兩個結構分號改成 **兩個 `:`**；enhanced-for 保持原本單個 `:`。`:` 不用作型別宣告。
4. `try` 的多 resource 用語法感知換行分隔；`enum` 常量區與成員區用單個 **`:`** 取代原本的結構分號。這兩項必須在遷移器、formatter 和官網突出說明。
5. 語句邊界由正式 grammar 決定；無「每一個換行直接替換成分號」的文字轉換。
6. Lombok annotation 依**已解析的全限定名稱**識別，保留 `import lombok.Data` 等相容寫法；不能看到任何名叫 `Data` 的 annotation 就擅自展開。無 import 的魔法 annotation prelude 不列入本次預設語法。
7. runtime 以最少為目標而不是不誠實的絕對零：大部分生成程式不需要 Teyru runtime；若個別功能需清楚隔離的 helper，必須公開依賴與授權。

---

# B. Teyru 語言與編譯行為規範

## B1. 基本語法、val／var 與 null

```teyru
package demo

import java.util.ArrayList
import java.util.UUID

public class Example {
    private String name = ""
    private String nullableName = null
    private final UUID id = UUID.randomUUID()

    public void run() {
        var values = new ArrayList<String>()
        val snapshot = values.size()
        String missing = null
        System.out.println(snapshot)
    }
}
```

`val` 約束的是局部變數重新賦值，不是深不可變。`val list = new ArrayList<String>()` 仍能 `list.add(...)`。`var`／`val` 需初始化；僅允許推斷規則明確的局部宣告、enhanced-for 和 resource 等上下文，對 basic-for 的 val 寫入等依 final 規則診斷。

`var x = null`、`val x = null`、沒有 target type 的 lambda／method reference 推斷是錯誤。`var x = (String) null` 可以推斷 String。`String x = null` 合法；不加 Kotlin 的 `?`，不偷偷生成 null check。匿名類型、intersection、capture type 不能為了印出 Java 而隨便退成 `Object`；可保留合法 Java `var`／`final var`，並驗證 ABI 不外漏不可表達型別。

不允許 `private var name`、`private val id`、`var` 回傳型別與 `name: String`。JLS 中 Java `var` lambda parameter 的語法應按 Java 基線保留，但 `val` 不作參數語法；這是「保留 Java 能力」的細節，不等於允許欄位推斷。遷移器需分清 `lombok.var`、Java contextual `var` 和普通識別符。[S03][S09]

## B2. 完全無語法分號

### 語句與延續

- 原始碼先按規範處理 Unicode escape，lexer 保留原始位置到處理後文字的映射；有意義的 `;` token 一律診斷 `TY-SYN-0001`，包含利用 Unicode escape 產生的語法分號。
- 字串／字元／text block／註解中的 `;` 是內容，不刪除。所有真實 `.java` fixture 與生成 Java 都保留 Java 分號。
- 換行可以結束已完整的 statement；未完成運算式、開啟括號的參數／運算式、下一行的明確鏈式 `.`／`::` 或合法續接運算子可延續。
- `return`／`throw`／`yield` 的換行規則明定：回傳值須在同一行開始或使用同一行開啟的 `(`。`return` 後立即換行是無值 return；`throw`／需要值的 `yield` 後換行沒有開啟表達式則報錯。不猜測下一行是不是返回值。
- `++`／`--` 跨行不得自動改變 prefix／postfix 歸屬。`a\n+b` 等 leading-operator 續行由 grammar 統一解析，formatter 必須產生不含歧義的格式。
- 不支援用 `;` 在同一行塞多條 statement。大括號隔離的完整結構可同列；一般 statement 以換行或 block 結尾結束。
- 原本單獨 `;` 的空語句，遷移成 `{}`；抽象／interface 方法、annotation element、module directive 以換行結束。`do { ... } while (...)` 不帶尾分號。

### Lambda 與鏈式呼叫：仍是 Java

```teyru
var names = users.stream()
    .filter(user -> {
        if (!user.isEnabled()) {
            return false
        }
        return user.getAge() >= 18
    })
    .map(User::getName)
    .toList()
```

保留 Java 的 target typing、overload resolution、capture、effectively-final、checked exceptions 與方法參照語義，不能轉成「所有 lambda 都是 Object 的動態呼叫」。

### Basic for：只替換結構分隔符，不改語義

```teyru
for (int i = 0 : i < 10 : i++) {
    System.out.println(i)
}

for (
    int left = 0, right = 10 :
    left < right :
    left++, right--
) {
    consume(left, right)
}

for (: : ) {
    tick()
}

for (String name : names) {
    consume(name)
}
```

保留初始化、條件、更新的求值順序、scope、label／continue／break；省略條件仍是真。parser 必須區分三元運算式內部的 `:`、enhanced-for 的 `:` 與 basic-for 的兩個分隔符，不得用字串 split。允許條件內 `a ? b : c`，解析測試必須覆蓋。

### Try-with-resources：每個完整 resource 一行

```teyru
try (
    InputStream input = openInput()
    OutputStream output = openOutput()
) {
    input.transferTo(output)
}
```

resource initializer 可以跨行；只有當 expression 完成且出現換行時才結束 resource。保留 Java 已宣告且 effectively-final resource 的使用方式、反向關閉順序、部分初始化失敗與 suppressed exception 行為。Lombok `@Cleanup` 的行為另外依 oracle 實現，不可錯當成完全相同的 try-with-resources。

### Enum：成員區用 `:` 作邊界

```teyru
public enum Status {
    READY,
    RUNNING,
    DONE
    :
    public boolean isDone() {
        return this == DONE
    }
}
```

沒有成員區則省略 `:`；空常量區但有成員區用開頭 `:`。保留常量參數、常量匿名類別 body、尾逗號、constructors 與 interface implementations。這是對 Java enum 結構分號的一對一替換，不要靠空白行猜測常量列表結束。

## B3. 原生 property：明確的 storage 與 accessor 契約

```teyru
public class User {
    private String name = "" {
        public get {
            return field
        }

        public set(value) {
            field = value.trim()
        }
    }
}
```

應生成語義等價且可讀的 Java：

```java
public class User {
    private String name = "";

    public String getName() {
        return this.name;
    }

    public void setName(String value) {
        this.name = value.trim();
    }
}
```

### 宣告與可見性

- `private String raw = ""` 是原有 Java field，不生成 API。
- accessor block 存在才是 property。宣告的型別是 property／backing storage 型別；`private` 等是 accessor 預設可見性，backing field 固定 private，保留必要的 `static`／`final`／`volatile`／`transient`。
- 未寫 visibility 的 `get`／`set` 繼承 property visibility；顯式 `public get`、`protected set`、`private set` 可以覆蓋。沒有 setter 不允許正常 property 寫入；沒有 getter 不允許正常讀取。
- `get`／`set` 的無 body 形式產生預設實作；`set(value)` 的參數型別由 property 決定；block 順序不影響語義；同一 accessor 不可重複宣告。
- `field` 是 accessor 語境的 contextual 名稱，不可在其他地方拿來指 backing storage；不能簡單把所有字串 `field` 替換掉。合法 Java 識別符名為 field 的其他作用域應仍可使用；accessor 中同名遮蔽規則需在 grammar fixture 鎖定。
- backing field 優先保留來源欄位名稱 `name`，不是無理由改成 `name$field`，以降低 field annotation／反射遷移差異；真衝突須診斷，synthetic helper 則使用可預測且避碰的名字。
- accessor 的 `getName`／`setName` 命名、boolean `isX`、大寫縮寫與 JavaBeans 規則有獨立 ABI 測試。`@Accessors` 明確套用時依相容設定展開。

### 初始值、final 與 computed property

```teyru
private final String id = createId() {
    public get
}

public int size {
    get {
        return values.size()
    }
}
```

初始值直接初始化 storage，一次且按 Java field initializer 順序，不先跑 setter。`final` storage 禁止 setter；未初始化的 final storage 可在宣告類的 constructor definite-assignment 規則下初始化，不得把該初始化改成不存在的 setter。

僅自訂 getter、無初始化且不引用 `field` 可是 computed property，不生成 field。預設 getter／setter、初始化值或引用 `field` 需要 storage，未顯式初始化時遵循 Java 預設值；不能擅自把初值設成空字串或自動 lazy。

computed property 的無意義 storage modifier（如 `volatile`）須診斷；`final` 與 accessor override 的關係不可混淆，不能把 Java final field 任意翻譯成 final method。

### 存取、賦值與副作用

```teyru
User user = new User()
user.name = " Alice "
System.out.println(user.name)
```

依 symbol resolution 將 property 存取變成 accessor 呼叫，包括本類別中正常的 `name`／`this.name` 使用；只有 `field`／明確的初始化 lowering 是 raw storage。明確的 `user.getName()`／`setName()` 仍合法，Java consumer 也能直接呼叫生成的方法。

必須處理 `p += rhs`、`++p`、`p++`、assignment expression、chained assignment、lambda、short-circuit、conditional、switch expression。receiver、getter、RHS、setter 的次數與順序必須保持契約：receiver 不重複執行；simple assignment 不讀 getter；compound assignment 在 RHS 前讀 getter；expression 結果按 Java 賦值轉型，不改成 setter 正規化後重新 getter 的值。

對 numeric compound assignment 保留 narrowing／boxing／unboxing／overflow；對 null receiver 保留各操作的例外時點。若需暫存與控制流程 lowering，產生可讀局部變數，不用會改變 capture／exception 的 lambda 包裝蒙混過關。

必測 `factory().count++`、`sideEffect().name = value()`、`flag && updateProperty()`、for-update 中的 property、labelled continue、getter/setter 拋錯與 native field 同名遮蔽。

### 繼承、註解與互通

- 繼承、override、interface abstract property、default computed getter、static property、generic property、只讀／只寫都需規範與測試；interface 不能憑空有 instance backing field。
- record 不增加非法 instance storage；可以有合規 computed property，record component 本身按 Java 規則處理。
- 顯式 annotation 的 target／retention、field-target 與 accessor-target 必須清晰；accessor 前可放 annotation。不能把只允許 FIELD 的 annotation 複製到 METHOD。
- 原生 property 與 `@Data`／`@Getter` 等同時出現，顯式 accessor 優先、禁止重複 API；遇到無法安全共存的 setter 型別／可見性衝突須診斷，不得靜默丟失。
- Java library 的 bean getter 不預設變成 property；後續可新增 opt-in，但不得偷改真實 Java field 存取語義。
- 跨 jar 使用 Teyru property 需版本化的 property metadata（建議 `META-INF/teyru/` 資料資源），不是靠猜所有 `getX()`。Java consumer 不需要認識這份 metadata。

## B4. Java 相容、解析與聯合編譯

必須保留：package/import/static import；primitive／reference／array；泛型 bounds、wildcards、capture、diamond、varargs；checked exceptions；annotation declaration／type-use／repeatable／retention；class/interface/enum/record/sealed/non-sealed；inner/local/anonymous classes；instance/static initializer；constructors／this／super；synchronized/assert；全部 Java 控制流程、switch expression／patterns、instanceof patterns、text blocks、Java 25 非 preview 新語法。確切 inventory 對照 JLS，逐項建立需求 ID。[S03]

不得自行把 `==` 改成 equals、把 final 變深不可變、移除 checked exceptions、改變 overload 選擇或空指標行為。JDK APIs 是 Java library interop，不代表得重新實作標準函式庫。

Java resolver 至少讀取 JDK、JAR、source JAR、目前 module Java sources、其他 module 輸出、module path、生成來源、未保存 editor buffer。JDK 公開的 compiler/tree/model APIs 可作為解析工具；不假定它們直接提供所有 IDE completion 能力。[S04]

聯合編譯採兩階段／必要的有限多階段協作：

```text
解析 Teyru + 收集宣告與生成成員形狀
    → 建立 Java-facing header／analysis projection + source map
    → 與 Java sources 聯合解析／屬性化，取得需要的型別
    → 完成 property／Lombok lowering 與可讀 Java emitter
    → Java sources + 生成 Java 交給同一 javac compilation
    → class／source JAR／metadata／診斷映射
```

generated stub 不是最終產品，不能把它們當真實class發佈。annotation processor 可能產生新型別，需顯式 rounds／task 模型、可終止性、重複生成防護；禁止無界重試。IDE 即時分析預設 `-proc:none`，不執行不受信任 processor。

## B5. Lombok 完整功能相容契約

**實現原理：**以 Teyru 自己的 symbol／AST／lowering 生成功能，Lombok 正式發行物僅作差分測試 oracle／受控遷移輔助，不得藏在正常 compiler 執行路徑中替代原生實作。

兼容名稱：解析 `lombok.*`／`lombok.experimental.*`／log packages 的明確 imports、wildcard imports 與全限定名稱。自訂同名 annotation 必須保持原本語義。compiler-consumed annotation/import 才能移除；第三方 annotation 不得任意丟失。

### 最低功能 inventory（不是允許忽略未列出的 API）

| Family | 必須實現的內容 |
|---|---|
| 型別推斷 | `val`、`var`、對應合法局部上下文、legacy import 遷移 |
| 存取器 | `@Getter`、`@Setter`、visibility、boolean 命名、`AccessLevel.NONE`、Javadoc／annotation 傳播 |
| Lazy | `@Getter(lazy=true)`、null cache、並行初始化、例外／重入、泛型／primitive |
| Null／cleanup | `@NonNull` 全部支援位置／設定；`@Cleanup` 預設／自訂方法、作用域與例外順序 |
| Construction | `@NoArgsConstructor`、`@RequiredArgsConstructor`、`@AllArgsConstructor`、force／staticName／access／參數註解 |
| Value methods | `@ToString`、`@EqualsAndHashCode`、Include／Exclude、callSuper、canEqual、cacheStrategy、陣列／浮點／繼承 |
| 聚合 | `@Data`、`@Value`、顯式成員優先、constructor／builder／final 交互 |
| Builder | `@Builder`、`@Builder.Default`、`@Builder.ObtainVia`、`@Singular`、toBuilder、method／constructor target、命名／access |
| SuperBuilder | 釘選版本的 `@SuperBuilder`、泛型繼承、抽象類、toBuilder、singular 與自訂 builder |
| 複製／函式更新 | `@With`、`@WithBy`（若 baseline 存在）、constructor、primitive operators、same-instance 行為 |
| Exceptions／locks | `@SneakyThrows`、`@Synchronized`、`@Locked` 及 Read／Write／自訂鎖 |
| Logging | `@Log`、`@CommonsLog`、`@Log4j`、`@Log4j2`、`@Slf4j`、`@XSlf4j`、`@JBossLog`、`@Flogger`、`@CustomLog` 和 baseline 新增項 |
| Access/defaults | `@Accessors`、`@FieldDefaults`、`@NonFinal`、`@PackagePrivate`、config 級預設 |
| Advanced | `@Delegate`、`@ExtensionMethod`、`@UtilityClass`、`@Helper` |
| Metadata | `@FieldNameConstants`、其 Include／Exclude、enum／String 模式；`@Tolerate`；`@StandardException` |
| Framework | `@Jacksonized`：對應 baseline 的 Builder／SuperBuilder／Accessors 支援與 Jackson 版本設定 |
| onX | `onMethod`／`onParam`／`onConstructor` 及 baseline 的兼容拼法／位置限制 |
| Config | 全部可取得設定鍵、階層、stopBubbling、clear、list 操作、import、來源定位、flagUsage |
| Annotation／utility surface | `@Generated` 等公開 marker、retention／target；`lombok.Lombok` 公開 utility 的遷移／必要兼容；舊別名依 baseline 規則 |

以官方功能索引、API 與 baseline 實際 classes／config 匯出共同盤點，避免漏掉索引之外的 `WithBy` 等功能。[S09][S10][S11][S15]

### 相容程度必須可檢驗

每個 feature 有：來源 URL／baseline artifact／參數與預設／合法位置／negative cases／ABI signature／runtime behavior／config cases／組合 cases／IDE 可見性／evidence。

差分路徑：一邊 Java+官方 Lombok 編譯／必要時 delombok；另一邊 Teyru 原生 lowering+javac；比較可觀察行為、public/protected/package API、必要 private 結構、generic signatures、annotation 與 framework 結果。不要要求整個 classfile 位元組相等，也不能把 public API 差異全正規化掉。[S12]

**關鍵陷阱：**

- `@SneakyThrows` 不能包成 `RuntimeException`；應保留原 throwable。若需要 helper，不得對 default build 留下 unresolved `lombok.Lombok.sneakyThrow`。[S13]
- lazy getter 必須正確快取 null；例外重試等以 baseline 真正行為測試，不把「一次」口號理解成所有例外路徑都永不重算。[S14]
- `@Cleanup` 的錯誤優先順序不自動等於 try-with-resources。
- `@Singular` 所有 baseline 支援集合與設定都要測，包含快照不受後續 builder mutation 影響、排序與 null policy。[S16]
- logger annotation 不代表自動選 logging implementation；依賴由 consumer 明確提供，missing dependency 有好診斷。
- retained annotation、`lombok.Generated`／nullness marker／Jackson annotations 的 namespace 與 retention 可能需要 compile-only annotation definitions。原生模式與嚴格 Lombok compatibility profile 必須明確區分，不能一邊丟掉它們一邊宣稱全部 metadata 相容。
- 若使用 baseline 的 MIT annotation 定義作 compile-only 相容 artifact，要隔離與保留原授權，不能同時塞入重複的 `lombok.*` classes；不得把 annotation processor 一起啟用。一般程式無 Teyru runtime 的目標不能掩蓋這類 profile 的真實依賴。

已知而未實作的 baseline feature 必須報具體錯誤，不得悄悄忽略；這只是開發期保護，**不表示該 feature 已通過最終完整交付**。

---

# C. 工具、生態與工程架構

## C1. 儲存庫結構

```text
teyru/
├── AGENTS.md
├── prompts/TEYRU_IMPLEMENTATION_PLAN.md
├── compiler-core/             # lexer/parser/CST/AST/symbol/diagnostic/IR/source-map
├── java-resolver/             # JDK/JAR/source/module 與 javac bridge
├── compiler-driver/           # pipeline 組裝、聯合編譯與取消
├── compiler-cli/              # teyru
├── language-tooling/          # editor-neutral analysis snapshots/navigation/edits
├── language-server/           # teyru-lsp process 與 LSP handlers
├── language-protocol/         # 版本化 Teyru 擴充 DTO
├── workspace-model/           # sourceSet/module/classpath/JDK schema
├── gradle-plugin/
├── intellij-plugin/
├── migration/
├── runtime/                   # 只容納必要且明示的 tiny helper
├── compat-annotations/        # 僅在嚴格 metadata profile 必要時建立
├── spec/
├── docs/
├── website/
├── examples/
├── compatibility/             # Java/Lombok/LSP/IDE/build 機器可讀矩陣
├── testkit/
├── compatibility-tests/
├── integration-tests/
├── release-tools/
├── build-logic/
├── gradle/
├── .codex/                    # 專案角色範本；能力確認後才啟用
├── .agent/                    # 可恢復狀態；logs/tmp 不入庫
└── .github/workflows/         # 若使用 GitHub；不自行建立遠端
```

不要為每個微小函式建立空 module。必要新增模組需 ADR；不能把整個 compiler、LSP、IDE 寫進一個「方便共享」的萬能 module。

## C2. 穩定契約與 workspace model

先定義 immutable／版本化資料模型：`SourceFile`、`TextRange`、`SourceMapSegment`、`Diagnostic`、`SymbolId`、`PropertyDescriptor`、`AnalysisSnapshot`、`CompileRequest`、`CompileResult`、`WorkspaceEdit`、`WorkspaceModel`。

model 至少記錄 schemaVersion、workspace root、modules、source sets、Java/Teyru roots、generated roots、source JARs、classpath、module path、toolchain executable／version、source/target release、compiler options、processor path／trust policy、test/runtime dependencies、encoding、property metadata 與來源 fingerprint。

Gradle adapter 匯出 model；CLI 可讀 JSON model；LSP 消費 model，不直接載入 Gradle Project 或在 compiler-core 執行 Gradle script。不要在 checked-in manifest 存每台機器的絕對 JDK 路徑；本機 resolved model 可使用正確 absolute URI，cache key 需有可移植策略。

## C3. Gradle 外掛與 CLI

對 consumer 的契約：

```text
src/main/java + src/main/teyru
  → generateTeyruJava
  → compileJava
  → classes / jar / test

src/test/java + src/test/teyru
  → generateTestTeyruJava
  → compileTestJava
  → test
```

生成位置固定在 `build/generated/sources/teyru/<sourceSet>/`；project model／source map 在 `build/teyru/` 下。支援 custom source sets、multi-module、test fixtures／必要 composite build、Java toolchains、module path、source JAR／Javadoc、application plugin、external annotations 與 processors。分析階段可能需要 Java sources 作 inputs，但不能造成 compileJava ↔ generation task cycle。

必測 up-to-date、刪檔、改簽名、改 Lombok config、改 classpath、改 JDK／target、relocation、configuration cache、build cache、並行 source sets；不能只測 clean build。採懶載入配置與 declared inputs/outputs，避免 afterEvaluate 拼湊與 configuration-time subprocess。[S07][S08]

CLI 必須實作一致且有 `--help` 的命令面：

```text
teyru --version
teyru doctor --format json
teyru check --project <model-or-project> --diagnostics json
teyru compile --project <model-or-project>
teyru emit-java <file-or-project> --output <directory>
teyru format <paths> --check
teyru explain <diagnostic-code>
teyru migrate <paths> --dry-run --report json
teyru migrate <paths> --apply
teyru inspect <file> --kind ast|symbols|source-map --format json
teyru-lsp --stdio
```

長旗標、子命令、JSON schemas 與 exit codes 在 P07 凍結。建置優先沿用 consumer 的 Gradle Wrapper；CLI project discovery 不得偷偷下載不受信任 script。stdout 為機器資料，stderr 為進度／日誌；LSP stdout 專供 framed JSON-RPC。

## C4. LSP：獨立實作、完整矩陣

建立 `compatibility/lsp-methods.json`，從官方 method inventory 逐項分類：必要支援、協定上不適用、未完成。N/A 需具體理由與審查；不可把 rename、Java completion 或 type hierarchy 這類需求歸為 N/A。協商 client capability，未實作不得 advertise。[S05]

必要 family：

- lifecycle、initialize／initialized／shutdown／exit、content-length framing、錯誤碼、cancellation、work-done／partial progress、dynamic registration、workspace configuration／folders／file watch；
- full／incremental text sync、版本一致、UTF-16／協商 positionEncoding、open unsaved buffers、中文／emoji／CRLF；
- push／pull document diagnostics、workspace diagnostics、related info／codeDescription／stale clearing；
- completion／resolve、hover、signatureHelp、definition／declaration／typeDefinition／implementation；
- references、documentHighlight、documentSymbol／workspaceSymbol；
- prepareRename／rename、workspace edit、file operations、preview／collision checks；
- codeAction／resolve、quick fix、organize imports、extractable concrete fixes、command routing；
- formatting／rangeFormatting／onTypeFormatting、semantic tokens full／delta／range、inlay hints／resolve；
- codeLens／resolve、folding、selectionRange、document links／resolve；
- callHierarchy、typeHierarchy、linked editing 的有意義情境；
- `executeCommand` allowlist、client refresh requests 與 capability gating。

Color／notebook／inline-value 等協定 family 要盤點；對沒有顏色語法、notebook host、debug adapter 的 Teyru 可合理不提供，不能為「全 LSP」造假資料。完整指必要語言服務與清楚的全 method 矩陣，不是宣稱所有客戶端可顯示規範每個選項。

同一份 language-tooling 提供 compiler 的 AST／semantic truth；LSP 不再解析一份不同的文法。每次 edit 形成可取消、不可變 snapshot；舊 request 不可覆蓋新結果。server crash 可重啟並重建狀態，不能讓 IDE 卡住。

## C5. IDEA：先接入、再完成深度體驗

插件必須可在沒有 IntelliJ 的環境外獨立運行同一 language-server。IDEA 只管理檔案類型、LSP lifecycle、設定、classpath／Gradle import 的 adapter、generated API projection、run/debug 與平台 UX。

官方 LSP API 有 IDE 發行版與版本限制，原生語言 API 也能提供較深整合；因此建立**server 能力**與**各 IDEA build client 呈現能力**兩張矩陣。不可假定 open-source IDEA build、Android Studio 或某版本免費／付費發行物皆具備相同 LSP module。[S06]

必須解決：Java 編輯器中 `new TeyruClass()`、getter／builder completion、Ctrl+B 回 Teyru declaration、Java ↔ Teyru references／rename、未 build 的生成成員可見、避免 generated Java 與 light projection 重複 symbols。

允許 IDE-specific PSI/light classes/projection adapter，但所有 signature／property mapping／語義判斷由共用模型提供。Java host 的既有 PSI 分析不是 Teyru 第二套語義引擎；若 host API 無法呈現某能力，寫 adapter／明確支援版本，不用「LSP 有做」冒充 IDEA 完成。

Debug 是另外的交付：選擇 IDEA Java debugger position manager／JDWP 映射，或經 ADR 決定的獨立 DAP adapter。驗證 Teyru 斷點、step、locals、exceptions、多行 lambda、getter/setter／生成區域。診斷 source map 不自動等於 JVM debug strata；不能承諾任意第三方 debugger 自動映射。

## C6. 官網、文件與擁抱 AI

預設採 **Astro + Starlight** 靜態文件站，TypeScript 與 lockfile；用真實可建置的 docs routing、搜尋與 code rendering，而非一頁假 landing。可因既有 repo 技術棧改用同級方案，但需具體 ADR，不為換框架浪費主要實作。[S19]

必備路由：首頁、download、getting-started、language、properties、var-val、Java interop、Lombok compatibility、Gradle、IDEA、LSP、migration、debugging、diagnostics、spec、examples、changelog、compatibility、licensing、security、contributing。

首頁用真正 Teyru／生成 Java 並排範例，清楚展現熟悉語法，避免大量漸層卡片、AI 宣傳詞或虛構統計。正式淺／深主題、可讀 typography、手機／桌面 navigation、鍵盤操作、copy code、anchors、404、搜尋、版本選擇、英文與繁體中文核心頁。

生成：`/llms.txt`、`/llms-full.txt`、每頁 `.md`、版本化 machine-readable compatibility／diagnostics／release manifests、離線 `ai-context.md`。HTML 與 Markdown 來自同一份版本化內容；用 `rel="alternate"`／`rel="describedby"` 連結機器可讀版本；按當時提案核對，不假定所有 AI 自動使用。[S18]

`llms-full.txt` 應可用但不是強迫 agent 全量讀取：提供小索引、主題分包、版本與來源資訊，避免主上下文污染。AI quickstart 應明確「這不是 Kotlin／Java 檔案，保留 Java 型別、沒有分號，先查 compile diagnostics」。

sitemap、robots、canonical、meta、Open Graph、RSS／Atom 與 changelog.json 自動生成。正式域名從配置取得，未註冊不得自稱擁有 `teyru.dev`／其他域名，測試使用 localhost／example.invalid。預覽站不冒充 production；不偷偷開放上傳 Java 並執行的線上 playground。

## C7. 版本、授權與發布

前期 compiler／CLI／LSP／Gradle／IDEA 採同一產品版本；IDEA build metadata 可以另帶相容平台資訊。維護 `CHANGELOG.md` 與變更片段，分類 Added／Changed／Fixed／Deprecated／Removed／Security／Compatibility。Breaking change 要 migration recipe，不只改版本數字。

可發布產物包括 compiler／CLI distributions、Gradle plugin marker、language-server distribution、IDEA plugin ZIP、source／Javadoc JAR、網站靜態輸出、checksums、SBOM、license notices、release manifest 與 consumer smoke project。

OpenJDK 授權模式適用於 Teyru 自有程式碼；第三方及輸出 helper 逐項審查，不能替他人程式碼重新授權。[S20][S21] 本地 `releaseCheck` 能完成不等於已發布；外部平台憑證／signing keys／domain 是獨立授權 gate，既不偽造也不阻礙其餘本地工作。

---

# D. 執行規則、依賴與共同驗收

## D1. 每個步驟的執行方式

每個 Pxx 是一個階段，不是一個要讓單 agent 帶著全世界上下文跑到底的巨型任務。協調者將其細分 `Pxx-Wyy`，每包依 `AGENTS.md` 帶 input、owned_paths、契約、測試、回報路徑。實作者與驗收者分離；同一檔案單一 owner。

每項 `[ ]` 都是必做驗收點，不是選單。所有步驟均附加根 AGENTS 的共同 DoD。章節中列出的「驗收案例」是最低限度，不能以僅寫這幾個測試聲稱完整。

若外部限制阻塞某一步，建立 blocker ID、具體證據、可重現命令與未完成責任人；可執行的獨立包繼續。未通過不得勾掉，也不得刪去 requirement 或改成 OPTIONAL。只有使用者明確改範圍才可刪減。

## D2. 依賴圖與先做 IDEA 的節點

```text
P00 → P01 → P02 → P03 → P04 → P05 → P06 → P07
                                      │
                                      └→ P08 → P09 → P10 → P11  [早期 IDEA 真實閉環]

P03/P04 → P12 → P13 → P14 → P15 → P16 → P17 → P18 → P19
P01/P06/P17 → P20 → P21 → P22 → P23 → P24 → P25 → P26 → P27 → P28 → P29
P08/P16/P17/P19 → P30 → P31 → P32 → P33 → P34 → P35 → P36
P09/P19/P29 → P37 → P38 → P43 → P39
P11/P30/P34/P36/P38 → P40 → P41 → P42
P03/P19/P29/P38 → P43 → P44
P03/P11 → P45 → P46 → P47 → P48
全部相關已驗收 → P49 → P50 → P51 → P52 → P53 → P54 → P55
```

箭頭表示必要關係的主幹；各 phase 下的精確依賴補足主幹。可提早平行寫 fixtures、網站殼與 docs skeleton，但內容標記 draft，不可提早宣稱 feature 已完成。Lombok 各 family 在 registry／resolver 契約穩定後可平行，server handlers 也可按不重疊 ownership 分工。

## D3. 產物與證據格式

每個驗收報告至少：需求 ID、agent ID、base commit／working tree fingerprint、執行環境、命令、exit code、pass/fail/skip 數、JDK/Gradle/IDE/oracle 版本、evidence path、已知限制、reviewer 與結論。

`compatibility/requirements.json` 是總追蹤表，包含本文 B/C 需求與每個 phase checkbox。生成 docs status，不可人工把官網 badge 改成綠色。`all implemented` 與 `all verified` 分開；release gate 僅接受兩者都達標且無阻斷缺陷的範圍。

進度驗證不得形成自我相依：`verifyAll` 檢查實作、測試與證據格式，不要求正在執行的這一次測試或 P55 先被標記 ACCEPTED。測試結束後由 reviewer 補上實際證據再核准；不得為避開循環而放鬆功能測試。

---

# E. 56 個實作階段與詳細清單

> P00～P55 均屬本次範圍。下列每個階段需要真實 subagent 工作包、獨立驗收與證據，不是把標題複製成空資料夾。

## P00 — 真實環境與子代理能力驗證

**負責：** 主協調者 + recon（只讀）  
**依賴：** 無  
**產物：** .agent 初始狀態、environment-report、真實 agent registry

- [x] **P00-01** 確認目前所在目錄就是使用者指定專案，記錄 Git root、工作目錄、dirty files 與既有 AGENTS／override；不修改使用者既有變更。
- [x] **P00-02** 讀取本檔 A～D；將 B/C/附錄的需求歸屬交給子代理建立覆蓋索引，禁止漏讀後直接實作。
- [x] **P00-03** 確認實際 Codex／客戶端版本與目前暴露的 subagent 工具，不能從名字或舊文章推定可用。
- [x] **P00-04** 真正建立一個 read-only recon 子代理，要求返回唯一 thread/agent ID、讀取範圍與環境短摘要；把證據寫入 registry。
- [x] **P00-05** 若無法建立子代理，標記 BLOCKED_SUBAGENT_CAPABILITY、提供最小已驗證修正方式，禁止主 thread 繼續單人實作。
- [x] **P00-06** 讀取官方 AGENTS 載入規則，檢查有效指示是否被 override／大小限制截斷；不擅自修改全域 project_doc_max_bytes。[S01]
- [x] **P00-07** 盤點 CPU、RAM、磁碟、JDK、Gradle、Node、套件管理器、GUI／headless、網路與 sandbox 限制，不假設可 sudo。
- [x] **P00-08** 設定預設最多 4 個活躍 agent、2 個寫入者、1 個重型 build；實際環境較小則下調並記錄。
- [x] **P00-09** 建立狀態／ownership／report 模板；安排不重疊的短 read-only 子包，確認回報不把原始 log 灌入主 session。
- [x] **P00-10** 驗證中止／關閉子代理、超時及等待能力；不要以 shell 背景程序或多個聊天角色冒充獨立 agent。
- [x] **P00-11** 列出外部發布憑證未提供、域名未定、Git remote 未授權等外部條件，與本地開發是否可行分開。
- [x] **P00-12** 完成可恢復起點：STATE、TASKS、OWNERSHIP、NEXT_SESSION 包含下一個可執行工作包，而非一句「環境正常」。

**階段出口：** 已有可追蹤真實子代理回報；若沒有，只能交付環境阻塞，不得把 P00 勾為完成。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P01 — 官方版本、依賴與授權鎖定

**負責：** recon + build + qa-review  
**依賴：** P00  
**產物：** docs/engineering/toolchain-matrix.md、gradle/libs.versions.toml、依賴/授權清冊

- [x] **P01-01** 查核 JDK 25／Java 21 profile 所需 API 與 javac 行為，記錄來源 URL、檢查日期、採用版本及 checksum。
- [x] **P01-02** 分別記錄 compiler runtime JDK、Gradle daemon JDK、Teyru target release、IDEA client bytecode level，禁止把四者混為一個版本。
- [x] **P01-03** 從 Gradle 官方相容性資料挑選確定可用的 wrapper 版本與測試下限，不以 dynamic latest 建置。
- [x] **P01-04** 挑選並驗證 IntelliJ Platform Gradle Plugin、最低／目前支援的 IDEA build、可用 LSP module；記錄不支援的發行物與原因。
- [x] **P01-05** 鎖定 Lombok 正式 baseline artifact 與 source tag，保存 SHA-256；實際匯出 API／config，不只抄索引頁。
- [x] **P01-06** 從 LSP 官方 method inventory 與選定 Java JSON-RPC library 確認協定支援；記錄需要自行補 DTO 的差異。
- [x] **P01-07** 盤點 parser／JSON／LSP／測試／網站依賴，選維護狀態可接受且授權相容的最小集合；不為個別 API 塞入龐大平台。
- [x] **P01-08** 檢查自有 GPLv2+Classpath、第三方原始碼、binary linking、打包、annotation definitions 與輸出 helper 邊界，建立待審查項。
- [x] **P01-09** 內部 package root 可使用 dev.teyru；公開 Maven group／Plugin ID 必須記錄 namespace 所有權驗證狀態，不假稱已註冊。
- [x] **P01-10** 為官方資料建立精簡 SOURCES 記錄與必要版本 snapshot；尊重上游授權，不把整站內容無審查複製進庫。
- [x] **P01-11** 鎖定 Node／網站套件管理器與所有前端依賴，記錄可重現安裝命令與 OS 支援範圍。
- [x] **P01-12** 由獨立 reviewer 重跑最小 toolchain／API probe，確認不是「文件看起來支持」而實際無法編譯。

**階段出口：** 工具鏈與依賴有可重現 probe、確定版本與授權記錄；不確定的外部發布條件另有 gate。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P02 — Monorepo、協作帳本與測試基礎

**負責：** build + integration  
**依賴：** P01  
**產物：** Gradle multi-project、協作模板、架構檢查、baseline CI

- [x] **P02-01** 建立 C1 模組與最小可編譯依賴圖；檢查現有程式碼可復用部分，不產生沒功能的相容聲明。
- [x] **P02-02** 建立 Gradle Wrapper、version catalog、build-logic、統一 Java formatter/lint 與 reproducible archive 設定。
- [x] **P02-03** 新增 .editorconfig、.gitattributes、.gitignore，統一 UTF-8／LF；保留 Windows launcher 與 CRLF 測試。
- [x] **P02-04** 建立 AGENTS 指定的 STATE／TASKS／OWNERSHIP／reports／logs 目錄，日誌和暫存不進 Git。
- [x] **P02-05** 建立 schema 驗證工作，確保 task ID 唯一、依賴可拓撲排序、每個工作包有 owner 與驗收 evidence 欄位。
- [x] **P02-06** 建立 module dependency architecture test，阻擋 core→Gradle/IDE/LSP、server→IDE 與循環依賴。
- [x] **P02-07** 建立 testkit 的 source fixture、expected diagnostics、Java consumer、JAR 檢查與 process harness。
- [x] **P02-08** 建立 verifyQuick／verifyAll／releaseCheck 契約；尚未有的必要測試要顯示 incomplete，不能用空聚合任務回傳「全部通過」。
- [x] **P02-09** 建立本地角色配置範本；按實際版本驗證 schema 後才啟用，不硬編模型名、不提高費率或移除 approval。[S02]
- [x] **P02-10** 將本文件需求 ID 匯入 requirements manifest；checkbox 狀態必須由實際驗收記錄推進，不能批量預勾。
- [x] **P02-11** 建立清潔 worktree／共享 worktree 的 ownership與整合流程；測試兩個 agent 不會同時寫根建置檔。
- [x] **P02-12** 建立最小 CI：編譯、lint、architecture、manifest 檢查；CI 尚未執行與本地成功清楚區分。

**階段出口：** 從乾淨 checkout 可跑真實初期測試；狀態與依賴圖能驗證，不能把未來 verifyAll 項目當成已完成。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P03 — 語言規範、ABI 與 ADR 定稿

**負責：** spec + compiler + qa-review  
**依賴：** P02  
**產物：** spec/ 完整骨架與正式規則、ADR、需求追蹤表

- [x] **P03-01** 將 B1～B5 的語言規則轉成版本化 spec，明確標記 normative／informative／examples，不由 README 決定語義。
- [x] **P03-02** 完成 lexical grammar 與可驗證 EBNF；記錄換行、Unicode escape、contextual names 與 source span 契約。
- [x] **P03-03** 凍結無分號 for／try／enum、空語句、abstract method／annotation element／module directive 的替代與遷移規則。
- [x] **P03-04** 凍結 property visibility、backing field、default accessor、field context、constructor initialization、final／computed 規則。
- [x] **P03-05** 凍結 JavaBeans accessor 命名、boolean／縮寫、synthetic name collision、跨 jar metadata schema 與版號政策。
- [x] **P03-06** 凍結 var／val 的允許上下文、target typing、匿名／不可表達型別與 null 診斷；保留 Java var lambda parameter 的既有能力。
- [x] **P03-07** 逐章盤點 Java SE25 非 preview 特性，建立 Java21 profile 差異及 feature-to-test mapping；不得只列幾個常見語法。
- [x] **P03-08** 規定 Lombok FQN resolution、native versus strict-metadata profile、annotation collision 與 unsupported-feature 開發期診斷。
- [x] **P03-09** 定義 Java/Teyru 混合編譯與 processor rounds 契約、型別解析 phase 順序與不可終止時的診斷。
- [x] **P03-10** 定義 diagnostics code namespace、位置 encoding、相關資訊、source map many-to-one／synthetic mapping 與 schema evolution。
- [x] **P03-11** 將未在前期對話明定的決策建立 ADR，附至少兩個反例與測試；不得把可自主決策事項全部推回詢問使用者。
- [x] **P03-12** 由 reviewer 用 Java 反例攻擊文法／語義，修正文法歧義；spec draft 未解決的問題不得以「實作時再看」通過。

**階段出口：** B/C 中的硬性需求都有正式章節與需求 ID；三段 for、enum 邊界與 property visibility 等不再含糊。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P04 — 核心資料模型與來源位置

**負責：** compiler + interop  
**依賴：** P03  
**產物：** compiler-core primitives、workspace-neutral 契約、source-map API

- [x] **P04-01** 實作不可變 SourceFile／SourceId／TextRange／LineMap，支持 UTF-8 檔案與 UTF-16 editor 座標換算。
- [x] **P04-02** 實作 Unicode 預處理到原始文字的 offset mapping，覆蓋 escape 改變換行與 surrogate pair 的案例。
- [x] **P04-03** 定義 token／trivia、CST／AST node ID、parent/child traversal 與錯誤節點，不依賴 IDE 類別。
- [x] **P04-04** 實作 Diagnostic code／severity／range／relatedInformation／fix metadata 與穩定 JSON serialization。
- [x] **P04-05** 定義 SymbolId、TypeRef、PropertyDescriptor、GeneratedMemberOrigin 與 ABI projection；ID 不用物件記憶體位址。
- [x] **P04-06** 定義 typed lowering IR／Java generation interfaces，記錄每個生成節點對應的來源或 synthetic 原因。
- [x] **P04-07** 實作 cancellation token、resource budget、輸入 fingerprint 與 thread-safe immutable snapshot 契約。
- [x] **P04-08** 實作 SourceMapSegment 的 direct／expanded／synthetic／related mapping，支援區間查詢而不是只存行號。
- [x] **P04-09** 為中文、emoji、CRLF、空檔、BOM、text block、Unicode newline 建立座標 roundtrip 測試。
- [x] **P04-10** 驗證 schema unknown fields／新版本拒絕策略與決定性 serialization，避免無序 Map 導致 snapshot 飄動。
- [x] **P04-11** 建立 internal implementation／public API 邊界與 API drift 檢查；禁止核心暴露 Gradle Project／PSI／LSP library types。
- [x] **P04-12** 由 interop reviewer 驗證一個來源節點展開多個 getter/setter 節點的定位，不容許只返回整個檔案位置。

**階段出口：** 核心模型可單獨測試、來源位置可 roundtrip，後端與 IDE 不用自行重新發明位置系統。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P05 — 最小但真實的 Teyru 前端

**負責：** compiler  
**依賴：** P04  
**產物：** 可解析 class/method/field/property 的 vertical slice

- [x] **P05-01** 實作真實 lexer 的識別符、關鍵字、字面量、註解、標點、newline 與 trivia，禁止 regex 全文替換。
- [x] **P05-02** 實作 package／import、class、method、顯式型別 field、局部宣告與基礎 expression grammar。
- [x] **P05-03** 解析 property block 的 get/set、自訂 body 與 access modifier，輸出帶 span 的 AST。
- [x] **P05-04** 實作 expression precedence、method call、member access、new、assignment 與基本 return/if/block。
- [x] **P05-05** 遵守無分號語句終止；對 `String x = "a;b"` 保留字串內分號，對真正語法分號產生具體 fix。
- [x] **P05-06** 新增未閉合 block、未完成 accessor、缺 expression 的 recovery node，不能遇到半行程式就 crash。
- [x] **P05-07** 建立 AST snapshot 但同時檢查 span／node kinds，不以單純 pretty-print 回顯源碼當 parser。
- [x] **P05-08** 建立 true-positive／true-negative fixture，確認非法 private var field／var null 不會被當成成功。
- [x] **P05-09** 只對已實作語法 advertise 能力；其他已知語法產生標記清晰的開發期 diagnostic，不忽略輸入。
- [x] **P05-10** 連接一個純 Java field 與一個 property 的 AST 差異測試，防止全域 auto-property 化。
- [x] **P05-11** 驗證同名普通識別符 get/set/field 在非 accessor 作用域的行為符合 spec。
- [x] **P05-12** 由 reviewer 用不完整編輯 buffer 重跑 parser，驗證穩定回復而非大量連鎖假錯。

**階段出口：** 有帶位置的真 parser，既能處理核心 property 範例，也會對錯誤輸入失敗；不算完整語言完成。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P06 — 可讀 Java emitter 與首次真實編譯

**負責：** compiler + interop  
**依賴：** P05  
**產物：** core property → Java → javac 的可執行垂直切片

- [x] **P06-01** 實作 Java emission 的縮排、imports、class/method/field、必要分號與穩定 member ordering。
- [x] **P06-02** 將原生 property 的 default/custom accessor 轉成正常 Java methods，backing field 名稱與可見性符合 B3。
- [x] **P06-03** 實作最小 symbol-bound property reads/writes；普通 field 保持直接存取，不能按字串名字全域改寫。
- [x] **P06-04** 生成每個節點的 source map，建立 generated Java header 的來源／版本資訊但不含不穩定 timestamp。
- [x] **P06-05** 透過 javax.tools.JavaCompiler／對應公開介面實際編譯，保存 javac exit 與 diagnostics。[S04]
- [x] **P06-06** 執行 Java consumer，驗證 setName trim 行為、getter 值與私有 accessor 的存取限制。
- [x] **P06-07** 將 javac 型別不相容與缺符號錯誤映射回 .teyru 的精確 range，而非只回 build/generated 路徑。
- [x] **P06-08** 驗證兩次 emit 內容一致；反覆執行不產生重複 methods 或累積 imports。
- [x] **P06-09** 驗證生成 Java 本身可打開、閱讀、用正常 javac 編譯，不需要 Lombok processor。
- [x] **P06-10** 建立 generated-files ownership manifest，避免後續 cleanup 刪除非 Teyru 產生內容。
- [x] **P06-11** 用 reflection 驗證 field/modifier、method return/parameter types，不能只比 Java 字串。
- [x] **P06-12** 由 reviewer 破壞 setter 實作確認 consumer 測試會失敗，證明不是無效成功測試。

**階段出口：** 真正產生、編譯並執行 class；可讀 Java 與來源錯誤定位有獨立證據。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P07 — CLI 初版與機器可讀診斷

**負責：** compiler + build  
**依賴：** P06  
**產物：** teyru launcher、核心命令與 diagnostics schema

- [x] **P07-01** 建立跨平台 launcher 與 --version／--help，不要求使用者手拼 internal classpath。
- [x] **P07-02** 實作 check／compile／emit-java 的 vertical slice，共用 compiler-driver，不複製編譯管線。
- [x] **P07-03** 凍結 exit code：成功、使用錯誤、編譯錯誤、工具鏈／I/O、內部錯誤需有獨立可測約定。
- [x] **P07-04** 提供 --diagnostics json、固定 schemaVersion、stable code、range、related info、建議，不用 ANSI 字串冒充 JSON。
- [x] **P07-05** 正確分開 stdout 與 stderr；quiet／no-color、路徑含空格、非 ASCII 路徑、Windows quoting 都需處理。
- [x] **P07-06** 建立 explain 命令的錯誤碼索引，未知 code 回合理錯誤，不編造解釋。
- [x] **P07-07** 建立 doctor 的 JDK／classpath／版本檢查，明確區分 missing javac 與 source compile error。
- [x] **P07-08** 檔案輸出使用 atomic replace；編譯失敗不留下可誤認成功的新產物。
- [x] **P07-09** 執行 Ctrl-C／cancel／timeout 測試，退出時關閉 file manager/process，不能留下 daemon 洩漏。
- [x] **P07-10** 更新 docs 中已可用命令與 fixture，未完成命令在開發狀態標明，不能 --help 宣稱它已可用。
- [x] **P07-11** 產生真 CLI integration test，從外部 process 讀 JSON、驗證 exit code 與輸出檔案。
- [x] **P07-12** 由獨立 reviewer 在新的工作目錄執行基本範例，不依賴 developer IDE 的 classpath。

**階段出口：** CLI 可由 shell／agent 真實呼叫，機器資料與人類日誌分離；後續 P39 補齊完整命令。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P08 — Workspace model 與 module／sourceSet 模型

**負責：** interop + build  
**依賴：** P04、P07  
**產物：** workspace-model schema、讀寫／驗證器、實際單 module model

- [x] **P08-01** 實作 C2 schema 與版本化 JSON serializer/validator，沒有 Gradle 或 IntelliJ runtime 依賴。
- [x] **P08-02** 讀取單 module 的 main/test Java/Teyru roots、JDK、classpath 與 generated directories。
- [x] **P08-03** 區分 project logical path、file URI、resolved absolute path 與可重定位 cache key。
- [x] **P08-04** 加入 compile/runtime/processor classpath、module path、source JAR、target release 與 trust policy。
- [x] **P08-05** 處理空 source set、不存在目錄、重複 roots、符號連結、大小寫敏感差異並提供定位清楚的診斷。
- [x] **P08-06** 建立 model fingerprint，classpath/JDK/options/config 改變可使分析 cache 失效。
- [x] **P08-07** CLI 可 consume model 並編譯相同範例，不要求 server 自行 eval build.gradle。
- [x] **P08-08** 建立主／測試 module 間依賴與循環 model 診斷；分清 source code 循環參照與 build module 循環。
- [x] **P08-09** 增添 dirty buffer overlay 契約，source map 與 on-disk model 能跟 editor snapshot 分離。
- [x] **P08-10** 保證 checked-in examples 不含開發機的絕對 user home/JDK 路徑。
- [x] **P08-11** 建立 schema backward/forward compatibility 與 stale model refresh 測試。
- [x] **P08-12** 由另一個不依賴 Gradle 的 test client 讀 model，證明格式真正 editor/build-neutral。

**階段出口：** 同一 workspace model 可供 CLI、server 與 Gradle adapter 使用，模型不能硬耦合任何 IDE。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P09 — Gradle 最小可用接入

**負責：** build + interop  
**依賴：** P06、P08  
**產物：** 可從 consumer 專案使用的 Gradle plugin vertical slice

- [ ] **P09-01** 建立真正 binary plugin、extension 與 task types，不用 consumer 手寫 JavaExec 完成全部工作。[S07]
- [ ] **P09-02** 註冊 src/main/teyru 與 src/test/teyru，接上 generateTeyruJava／generateTestTeyruJava。
- [ ] **P09-03** 將生成 Java 正確加入 Java source set，透過 task provider output 建立依賴，不用硬拼檔案順序。
- [ ] **P09-04** 生成 task 的 analysis inputs 包含必要 Java sources，但不能形成 compileJava 反向循環。
- [ ] **P09-05** 透過隔離 compiler process/worker 使用選定 toolchain，避免 compiler classes 污染 Gradle daemon classloader。
- [ ] **P09-06** 提供 main/test workspace model 匯出任務，輸出在 build/teyru，不修改 src。
- [ ] **P09-07** 建立 includeBuild／本地 plugin resolution 的 consumer sample，不假定公共 Plugin Portal 已有 Teyru。
- [ ] **P09-08** 用 Gradle TestKit 跑 build/test，Java 呼叫 Teyru class，Teyru 也呼叫同 module Java class。[S08]
- [ ] **P09-09** 初步宣告 inputs/outputs，連續兩次 build 確認不是每次都全量重跑；失效細節留 P37 完成。
- [ ] **P09-10** 生成語法／型別錯誤時 Gradle build 必須失敗並顯示 .teyru 源位置。
- [ ] **P09-11** 檢查刪除 .teyru 後不留下舊 Java/class 被當成成功；cleanup 僅處理本工具 manifest。
- [ ] **P09-12** 由 reviewer 在全新 consumer 目錄按 docs build，不依賴本庫測試直接調用 internal compiler。

**階段出口：** 使用者可以透過 Gradle 寫真 .teyru 並編譯混合專案；這是第一次產品接入，不是完整 cache 驗收。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P10 — 獨立 LSP 啟動與最小協定閉環

**負責：** lsp  
**依賴：** P05、P07、P08  
**產物：** teyru-lsp process、lifecycle、基本診斷／hover

- [ ] **P10-01** 建立獨立 language-server module 與 stdio launcher，不 import IntelliJ classes。
- [ ] **P10-02** 實作 JSON-RPC framing、initialize/initialized/shutdown/exit、request ID 與錯誤處理。[S05]
- [ ] **P10-03** 處理 didOpen/didChange/didClose，使用 compiler-core parser 對未保存 buffer 建立 snapshot。
- [ ] **P10-04** 初期只 advertise 真正可用的 diagnostics／hover／基礎 completion，不一開始把全部能力設 true。
- [ ] **P10-05** 將 Teyru syntax errors 發回原始 URI/range，修正後清除舊錯誤。
- [ ] **P10-06** workspace model 能提供 Java/JDK 基本 symbols；先驗證簡單 String／本類別 property 的資訊。
- [ ] **P10-07** stdout 僅含協定 frame；啟動日誌、warning 與 stacktrace 寫到 stderr／本地 log。
- [ ] **P10-08** 實作取消與文件 version 檢查，慢的舊診斷不得覆蓋新的 buffer 結果。
- [ ] **P10-09** 加入惡意或不完整 JSON、client 提前斷線、未知 method、shutdown 後請求的測試。
- [ ] **P10-10** 用外部 script client 真正發協定訊息，而不是在 unit test 直接呼叫 handler。
- [ ] **P10-11** 啟動／關閉多次後確認無殘留 process、file handle 或持續 CPU 使用。
- [ ] **P10-12** 由 reviewer 在未安裝 IDEA 的獨立環境跑相同 LSP smoke test，證明 server 可獨立使用。

**階段出口：** server 是可獨立啟動、可被真 client 呼叫的 LSP，不是塞在 IDEA plugin 的語言邏輯。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P11 — IDEA 提前接入與真實 smoke test

**負責：** ide + qa-review  
**依賴：** P09、P10  
**產物：** 可安裝 IDEA plugin ZIP、首次 UI／LSP 端到端證據

- [ ] **P11-01** 建立 .teyru file type、language ID、圖示與基本共用 lexer／text syntax highlighting，不新增語義引擎。
- [ ] **P11-02** 依 P01 選定 IDE/LSP API 實現 client descriptor/provider，不混用不同 SDK 世代類名。[S06]
- [ ] **P11-03** 從設定或受控 bundled distribution 啟動 teyru-lsp；檢查 server version/JDK，不能默默啟動另一個未知執行檔。
- [ ] **P11-04** 打開 P09 的真 Gradle consumer，編輯 User.teyru，確認 server 收到未保存內容。
- [ ] **P11-05** 展示 syntax error、修復清除、hover 與基本 completion；保存/生成 Java 不能作為即時分析的前提。
- [ ] **P11-06** 提供 server 狀態、restart、錯誤輸出入口，server crash 不讓 IDE freeze。
- [ ] **P11-07** buildPlugin 產生可安裝 ZIP，使用指定 IDEA sandbox 實際載入，不能只證明 plugin.xml 語法正確。
- [ ] **P11-08** 驗證 Gradle build 從 IDE 可用，生成 Java 不被默默複製回 src/main/java。
- [ ] **P11-09** 記錄 IDEA build、插件版本、JDK、測試步驟、UI 截圖／自動化證據與缺少的能力。
- [ ] **P11-10** 檢查 plugin 的 dependency graph 沒有 compiler-driver／java-resolver 的嵌入式副本，語義走 server。
- [ ] **P11-11** 將最小 IDEA smoke 納入每個後續重要階段回歸計畫；沒有 GUI runner 時此項保持未驗證，不假裝完成。
- [ ] **P11-12** 獨立 reviewer 重開 project 並修改 property，確認不是一張寫死 UI 截圖或靜態 demo。

**階段出口：** 必須早於大規模 Lombok 功能擴充完成真 IDEA 實測；只有 ZIP 沒有可用 UI 不過關。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P12 — 完整詞法與錯誤恢復

**負責：** compiler  
**依賴：** P03、P05、P11  
**產物：** 完整 lexer、trivia／Unicode pipeline、fuzz seeds

- [ ] **P12-01** 按 Java25 lexical inventory 支援所有數字形式、字元、字串、text blocks、escape、識別符與 contextual tokens。
- [ ] **P12-02** 處理 Unicode escapes 的多階段資格規則、產生換行/分號等情況，維持原始 source map。
- [ ] **P12-03** 完整保留 line/block/Javadoc comments、空白與 newline，供 formatter／migration 不丟內容。
- [ ] **P12-04** 實作 CRLF、LF、BOM、tab、surrogate pair、非 BMP 識別符與非法 UTF-8 的明確政策。
- [ ] **P12-05** 區分 get/set/field/val/var 在不同 grammar context，不全域霸佔合法普通識別符。
- [ ] **P12-06** 正確解析 >>/>>> 與泛型巢狀結尾，token stream 視 parser context 處理而不是破壞移位運算。
- [ ] **P12-07** 處理 unterminated string/comment/text block、多個連續 lexical errors，避免無限迴圈。
- [ ] **P12-08** 明確區分資料分號與語法分號，加入字串內 JSON/SQL/URL 的保留測試。
- [ ] **P12-09** 保存 newline 對行接續的必要 metadata，不在 lexer 直接插入 Java 分號。
- [ ] **P12-10** 建立大量最小正反例與 mutation fuzz；錯誤輸入不能 crash、hang 或超出資源 budget。
- [ ] **P12-11** 驗證 lexer 與 IDE syntax facade 用同一 token 定義，不允許 keyword 清單不同步。
- [ ] **P12-12** 由 reviewer 使用 seed 固定的 fuzz corpus 重現所有發現，修復後保存 regression fixture。

**階段出口：** 詞法覆蓋 Java基線與Teyru新增語法，對不完整編輯與惡意輸入有可重現防護。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P13 — 完整宣告與型別 grammar

**負責：** compiler + spec  
**依賴：** P12  
**產物：** class/interface/record/enum/module 等宣告 parser

- [ ] **P13-01** 實作 package、各種 import／static import 與 Java25 非 preview 的相關 import grammar。
- [ ] **P13-02** 實作 class/interface/enum/record/annotation declaration、sealed/non-sealed/permits 與 modifiers 驗證。
- [ ] **P13-03** 實作 generic declaration、bounds、wildcard、array annotations、varargs、receiver parameter 與 type-use annotation。
- [ ] **P13-04** 實作 field、method、constructor、annotation element、initializer block、nested/local/anonymous class。
- [ ] **P13-05** 保留 Java25 合法的 constructor body 規則及 compact source/instance main 等正式特性，依 JLS inventory 逐項驗證。
- [ ] **P13-06** 實作 enum 的 colon member delimiter、constant arguments、constant-specific class body、空 enum 與尾逗號。
- [ ] **P13-07** 支援 package-info／module-info 等專用 compilation unit，不假定所有來源都是 public class。
- [ ] **P13-08** 處理 property initializer 的 array／anonymous class／lambda body 與後接 accessor block，不能見到 { 就當 accessor。
- [ ] **P13-09** 實作 interface／abstract methods 的無分號終止與相鄰宣告歧義恢復。
- [ ] **P13-10** 對非法 modifiers、重複 constructors、未閉合泛型及混淆 class/record context 給出精確語法診斷。
- [ ] **P13-11** 以正式 Java fixture 經語法感知遷移後比對宣告 AST／ABI，保留所有 annotations與Javadoc。
- [ ] **P13-12** reviewer 對照 Java inventory 找缺口；未覆蓋的基線宣告不可歸成「未來功能」。

**階段出口：** Java25 非 preview 宣告 inventory 有 parser 和測試對應；不是只支援 class + field。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P14 — 完整表達式、控制流程與多行 lambda

**負責：** compiler + spec  
**依賴：** P13  
**產物：** expression/statement parser 與優先序測試

- [ ] **P14-01** 實作所有 Java 運算子優先序、associativity、cast、conditional、assignment、instanceof/pattern grammar。
- [ ] **P14-02** 實作 method invocation、constructor reference、method reference、lambda、顯式泛型呼叫與 chained expression。
- [ ] **P14-03** 實作 if/else、while、do-while、enhanced-for、colon basic-for、break/continue/labels。
- [ ] **P14-04** 實作 return、throw、yield、assert、synchronized、try/catch/finally、multi-catch、resource headers。
- [ ] **P14-05** 實作 switch statement/expression、arrow/colon cases、patterns／guards 與 Java25 合法組合。
- [ ] **P14-06** 保留 multiline lambda 的 block/return/capture，測試 nested lambda、overload candidate 與 method reference context。
- [ ] **P14-07** 處理省略 basic-for 三段、multiple init/update、條件內 ternary 的 colon、nested generic expression。
- [ ] **P14-08** 保留空 block 與無值 return；reject 原本單獨 ;，migration 才轉成 {}。
- [ ] **P14-09** 對 expression continuation 與 statement boundary 加完整 lookahead／recovery，不偷用「每行補 ;」。
- [ ] **P14-10** 支援 partial expressions 的 AST recovery，LSP 可以在 foo. 或不完整 lambda 中工作。
- [ ] **P14-11** 建立 precedence AST tests 及真正執行結果對照，避免兩個錯誤 phase 抵消後只有表面 output 正確。
- [ ] **P14-12** 由 reviewer 加上副作用／throw／short-circuit cases，確保 parser 沒有改變 expression grouping。

**階段出口：** Java 控制流程與lambda語法完整可解析；所有結構分號替代均經混淆案例檢驗。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P15 — 無分號規則與 formatter 共識測試

**負責：** spec + compiler + qa-review  
**依賴：** P12～P14  
**產物：** newline conformance suite、semicolon migration fixtures

- [ ] **P15-01** 將 B2 每條換行規則轉成有 expected AST／diagnostic 的獨立 fixture，包含合法續行與新 statement。
- [ ] **P15-02** 測試 return/throw/yield 後換行、開括號、註解插入、運算子開頭、點鏈接續及空行。
- [ ] **P15-03** 測試 a 換行 +b、a 換行 ++b、postfix 換行、method reference 換行，不允許猜測式重排。
- [ ] **P15-04** 測試 basic-for 空段／ternary／複數 init update、enhanced-for 與 nested label，輸出合法 Java for。
- [ ] **P15-05** 測試多個 resource initializer 跨行、existing resource、部分初始化失敗的正確語法形狀。
- [ ] **P15-06** 測試 enum 空常量／成員、constructors、constant bodies 與冒號邊界，禁止依空白行猜測。
- [ ] **P15-07** 覆蓋 interface/abstract/native method、annotation default、module directives 的無分號終止。
- [ ] **P15-08** 覆蓋 do-while、empty statement→empty block、多 statement 同列以及 class 內多餘分號的診斷。
- [ ] **P15-09** 對字串、char、text block、註解中的分號執行 lossless 驗證，不能被 migration/formatter 刪掉。
- [ ] **P15-10** 建立 parse→format→parse AST 等價與 format idempotence；formatter 不輸出有意義分號。
- [ ] **P15-11** 對同一套 fixture 的 compiler、LSP formatter、CLI formatter、website highlighter做一致性檢查。
- [ ] **P15-12** 由 reviewer 使用未出現在最初範例的新排版攻擊規則；grammar 不明確先修 spec 再改 test。

**階段出口：** 徹底無分號不留 for/enum 偷渡例外；格式化、編譯與遷移行為一致且可解釋。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P16 — Java resolver、型別與符號完整實作

**負責：** interop + compiler  
**依賴：** P13、P14、P08  
**產物：** JDK/JAR/source/module resolver、型別檢查與跨來源 symbols

- [ ] **P16-01** 解析 JDK modules、classpath JAR、source JAR、multi-release JAR 與 package/type/member metadata。
- [ ] **P16-02** 解析當前 module Java sources 與未保存 overlays，保留 source declaration location與Javadoc。
- [ ] **P16-03** 實作 imports、package access、protected access、nested types、static members、shadowing 與 name collisions。
- [ ] **P16-04** 使用公開 Java model/tree APIs 協助泛型、overload、capture、boxing、varargs、lambda target typing，不粗暴回 Object。[S04]
- [ ] **P16-05** 保留 raw types／unchecked warnings、intersection types、covariant returns、bridge methods 等必要 Java 語義。
- [ ] **P16-06** 支援 module path、exports/opens/readability 與 classpath 的不同規則，module-info 不能當普通 class。
- [ ] **P16-07** 解析 Teyru 生成成員／property descriptors，讓編譯前的 getter/builder symbols 可被引用。
- [ ] **P16-08** 建立 external jar／JDK source navigation，不把每個 library symbol 都指到相同假檔案。
- [ ] **P16-09** 建立多 module symbol index、cache key與精確失效，不把另一個 target release的Symbols混用。
- [ ] **P16-10** 執行 JDK／JAR／Java source同名衝突與缺依賴診斷，錯誤需要指出 classpath/model 原因。
- [ ] **P16-11** 與 javac 對照 lambda overload、numeric promotion、generic bounds、access checks 和 invalid programs。
- [ ] **P16-12** reviewer 確認 core 不 import platform internals；內部 javac API 如確需使用，有隔離 ADR與跨JDK測試。

**階段出口：** 類型解析能夠支持真實 Java 泛型與跨源互通，不是僅列類名的索引器。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P17 — 混合編譯、宣告投影與 annotation processing

**負責：** interop + build  
**依賴：** P16、P06  
**產物：** 完整 compiler-driver pipeline、聯合編譯及 processor tests

- [ ] **P17-01** 實作先收集 Teyru declarations／generated member shapes，再與 Java sources 聯合 attribution 的管線。
- [ ] **P17-02** Java A 引用 Teyru B、B 又引用 A 的同 module case 必須一次正常建置，不以先各自編譯造成假循環。
- [ ] **P17-03** 需要 getter／builder/member型別的Java與Teyru code都能看到一致projection，不發布stub class作產品。
- [ ] **P17-04** 對 method bodies 需要型別才能lowering的情況建立受控analysis projections，保留來源映射與diagnostic filter。
- [ ] **P17-05** 為外部 annotation processors 明確區分 analysis、processing、final javac rounds，避免重複副作用。
- [ ] **P17-06** 測試 processor 產生型別再被來源引用，以及產生基於getter/builder的代碼，處理資源／source輸出目錄。
- [ ] **P17-07** 對 truly cyclic build modules、duplicate generated types、processor不終止／崩潰給可操作的錯誤。
- [ ] **P17-08** compiler/Gradle未經授權不在IDE分析執行processor，trust policy由workspace model傳入。
- [ ] **P17-09** 在無Lombok processor的正常pipeline完成Lombok-generated member解析，官方Lombok僅oracle使用。
- [ ] **P17-10** 失敗建置不發布部分class為最新成功產物；成功時輸出manifest/source-map/metadata同步。
- [ ] **P17-11** 測試 javac warnings/errors的多origin mapping、Java檔直接錯誤保持Java位置，不能強行映射成Teyru。
- [ ] **P17-12** reviewer從乾淨consumer重現循環參照與processor cases，確認不是預先殘留class救過建置。

**階段出口：** Java↔Teyru及外部processor在真實清潔構建中工作，無無界round或stub偽產物。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P18 — Property 全語義與副作用 lowering

**負責：** compiler + qa-review  
**依賴：** P16、P17、P15  
**產物：** 完整 property implementation／ABI／negative cases

- [ ] **P18-01** 完成 default/custom、read-only/write-only、visibility overrides、static/final/volatile/transient 與computed properties。
- [ ] **P18-02** 實作field contextual binding，正確處理getter內其他object.property與普通field名為field的情境。
- [ ] **P18-03** 依spec保留initializer直寫storage、constructor的final definite assignment與初始化順序。
- [ ] **P18-04** 實作property read/write、assignment value、chained assignment、compound assignments、prefix/postfix。
- [ ] **P18-05** 保留receiver/getter/RHS/setter求值次數、順序及null/throw時點，不用重複receiver或getter作捷徑。
- [ ] **P18-06** 處理byte/short/char narrowing、boxed null、string +=、overflow以及setter會改寫value的expression結果。
- [ ] **P18-07** 處理short-circuit、conditional、lambda、switch、for-condition/update、labels/continue等需控制流程lowering。
- [ ] **P18-08** 實作interface抽象property、default computed getter、override、generic/covariant與static語義；record不增非法storage。
- [ ] **P18-09** 實作accessor annotation位置／target檢查，與Data/Getter/Accessors共存優先序與衝突診斷。
- [ ] **P18-10** 測試same-class的name/this.name都走property語義，明確getName呼叫與field raw讀寫不會混淆。
- [ ] **P18-11** 以副作用事件序列、reflection、Java consumer與來源映射驗證，不只測setter最後字串。
- [ ] **P18-12** reviewer加入factory().count++、getter throws、final init、setter-only非法讀取等獨立案例並實際執行。

**階段出口：** Property不僅是生成兩個方法：讀寫表達式、構造、繼承與Java API都符合規範。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P19 — 完整 emitter、IR、source map 與跨 JAR metadata

**負責：** compiler + interop  
**依賴：** P17、P18  
**產物：** 可讀 Java 後端、完整 diagnostics mapping、metadata reader/writer

- [ ] **P19-01** 完成全部Java25目標語法的Java AST/emitter，對較低release做準確拒絕或經證明等價的規範lowering。
- [ ] **P19-02** 生成imports避免名稱衝突，必要時全限定類型；保留Javadoc/annotations與合理成員順序。
- [ ] **P19-03** 必要temporaries具穩定可讀命名與scope，不因重跑／線程順序導致輸出不同。
- [ ] **P19-04** source map覆蓋多行lambda、property展開、Lombok成員佔位、synthetic helper與轉換後的控制流程。
- [ ] **P19-05** 區分可定位用戶錯誤、生成器bug與缺外部依賴；無法精確定位時給真實生成位置及相關來源，不捏造range。
- [ ] **P19-06** 發佈versioned property/member metadata到jar資源，讀不兼容版本需診斷或明確降級而非猜getter。
- [ ] **P19-07** 驗證跨JAR Teyru consumer 可使用property，純Java consumer 只需普通getter/setter ABI。
- [ ] **P19-08** 生成source JAR與原始Teyru來源分開且可追溯；generated Java不加入使用者源碼目錄。
- [ ] **P19-09** 更新generated-output ownership與原子清理；刪除/重命名/類名變化不會留下舊生成類。
- [ ] **P19-10** 對兩次清潔構建及不同工作目錄比較決定性輸出，敏感路徑不洩露到發佈包。
- [ ] **P19-11** 編譯與運行構建產物，驗證generics/annotations/exceptions/records/sealed class的reflection與ABI。
- [ ] **P19-12** reviewer逐項檢查不可表達類型、anonymous class、複雜語句lowering的可讀性和source-map完整度。

**階段出口：** 源碼、生成Java、class、metadata有穩定可追蹤關係，並能跨jar保留Teyru property語義。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P20 — Lombok baseline、oracle 與完整 API inventory

**負責：** lombok + qa-review  
**依賴：** P01、P17、P19  
**產物：** compatibility/lombok-*.json、oracle harness、feature registry

- [ ] **P20-01** 下載並校驗P01鎖定的Lombok artifact/source，記錄版本/tag/hash，所有oracle運行固定相同版本。
- [ ] **P20-02** 從官方功能索引、API classes與config工具共同生成清單，不僅列Data/Builder。[S09][S10]
- [ ] **P20-03** 實際運行 `java -jar lombok.jar config -g --verbose`，保存有來源的配置鍵清單與默認值。[S11]
- [ ] **P20-04** 盤點WithBy、nested Include/Exclude、Generated、NullCollectionBehavior或baseline新增API等索引外surface。
- [ ] **P20-05** 建立FQN-bound intrinsic registry，不把用戶自定義@Data或同名import誤當成Lombok。
- [ ] **P20-06** 為每個feature記錄合法位置、參數/默認值、config、generated API、negative cases與組合依賴。
- [ ] **P20-07** 建立Java+Lombok、delombok（適用時）、Teyru原生生成三條測試路徑，oracle不能進入production compiler。[S12]
- [ ] **P20-08** 比較運行結果、reflection/ABI、annotation metadata、異常與框架行為；classfile debug差異可解釋但不掩蓋API變化。
- [ ] **P20-09** 建立未實現feature明確error與追蹤ID，禁止annotation靜默忽略；未實現狀態不能獲得VERIFIED。
- [ ] **P20-10** 審核oracle fixtures／借鑒代碼的許可證，重用MIT代碼保留聲明，不擅自抹去upstream copyright。
- [ ] **P20-11** 獨立reviewer檢驗oracle確實執行官方Lombok且Teyru正常路徑沒有偷偷調用它。
- [ ] **P20-12** 將清單與官網矩陣綁定，新增baseline API會讓coverage檢查失敗，強制補齊任務而不是默認忽略。

**階段出口：** 「完整Lombok」有固定版本、全量清單和可信差分基準，後續family不會漏掉參數與配置。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P21 — Getter／Setter／NonNull／Accessors

**負責：** lombok  
**依賴：** P20、P18  
**產物：** 基礎訪問器family、null checks、Accessors命名與配置

- [ ] **P21-01** 實現class/field級Getter/Setter、全部AccessLevel及NONE，正確處理static/final字段。
- [ ] **P21-02** 匹配primitive boolean與Boolean、is前綴、大寫縮寫、命名衝突與已有方法抑制規則。
- [ ] **P21-03** 實現Accessors的fluent/chain/prefix/makeFinal及baseline支持選項，讀取分層config。
- [ ] **P21-04** 實現NonNull在baseline支持參數/fields/record或其他位置的檢查與異常類型/消息配置。
- [ ] **P21-05** 避免重復已有null check，保留constructor/super調用的限制與檢查時機。
- [ ] **P21-06** 實現Javadoc與copyable annotations傳遞，只有合法target能複製到getter/setter/parameter。
- [ ] **P21-07** 原生property顯式accessor優先，Lombok合成成員不重復；矛盾配置給出位置準確的診斷。
- [ ] **P21-08** 測getter/setter泛型signature、static access、visibility與JavaBean/Fluent兩種consumer API。
- [ ] **P21-09** 測試字段名name/getName/isReady等衝突及用戶定義同名annotation，保證基於符號而非拼寫匹配。
- [ ] **P21-10** 對全部參數、config defaults與negative positions運行oracle差分，不只對默認case。
- [ ] **P21-11** LSP model提前顯示相同合成成員，completion/nav映射回真實field或accessor。
- [ ] **P21-12** 獨立reviewer加入setter chain返回值與NonNull拋錯副作用順序案例並驗證。

**階段出口：** 基礎family的API、命名、null行為及與native property交互全部通過差分。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P22 — Constructors／Data／Value／Equals／ToString

**負責：** lombok  
**依賴：** P21  
**產物：** 構造與值語義family、組合規則與ABI測試

- [ ] **P22-01** 實現NoArgs/RequiredArgs/AllArgsConstructor的access/staticName/force與顯式constructor交互。
- [ ] **P22-02** 保留final與NonNull字段選擇、初始化field是否加入參數、參數順序與null檢查。
- [ ] **P22-03** 實現ToString的Include/Exclude、onlyExplicitlyIncluded、字段/方法成員、rank/name、callSuper與getter策略。
- [ ] **P22-04** 實現EqualsAndHashCode的Include/Exclude、callSuper、canEqual、cacheStrategy、方法include與replacement規則。
- [ ] **P22-05** 比較數組/深數組、primitive/boxed/floating點、null、transient/static字段與繼承行為。
- [ ] **P22-06** 實現Data聚合的成員生成和已有成員抑制，避免一視同仁覆蓋手寫equals/constructor。
- [ ] **P22-07** 實現Value的final/private/constructor策略與NonFinal/PackagePrivate等允許例外。
- [ ] **P22-08** 處理Value+Builder/Data+constructor/property組合所要求的優先級，不按annotation源碼排列順序亂生成。
- [ ] **P22-09** 實現ConstructorProperties、onConstructor入口與copyable annotations的必要metadata。
- [ ] **P22-10** 測試reflection signature、Java子類/consumer、序列化相關結構與多線程下hash cache約定。
- [ ] **P22-11** 逐項運行官方oracle與Teyru差分，錯誤位置/診斷內容與上游約束一致到契約要求的層次。
- [ ] **P22-12** 獨立reviewer使用不相等子類、NaN/-0.0、mutated array、手寫重載constructor等反例。

**階段出口：** 值語義與構造器不是模板複製：所有選擇、繼承與組合優先級得到實際驗證。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P23 — Builder／Default／ObtainVia／Singular

**負責：** lombok  
**依賴：** P22  
**產物：** 完整Builder family與集合快照測試

- [ ] **P23-01** 實現class/constructor/method target，含泛型方法、void返回與指定access。[S16]
- [ ] **P23-02** 實現builderClassName/builderMethodName/buildMethodName/setterPrefix/toBuilder及抑制builder入口選項。
- [ ] **P23-03** 實現已有builder class/method的合併與合法衝突規則，不刪除用戶手寫代碼。
- [ ] **P23-04** 實現Builder.Default初始化與set標誌，區分未設置、顯式null/0及默認表達式副作用。
- [ ] **P23-05** 實現Builder.ObtainVia的field/method/static方式與訪問/類型錯誤診斷。
- [ ] **P23-06** 實現Singular全部baseline支持的JDK集合與Guava目標類型，外部依賴由consumer明確提供。
- [ ] **P23-07** 實現單數推導/顯式名稱、clear、prefix、ignore/null policy或baseline新增參數，不僅支持List。
- [ ] **P23-08** 驗證build後集合不可變與快照隔離，builder後續追加/clear不改變已生成對象。
- [ ] **P23-09** 處理默認constructor/Value/NonNull/property與Builder的組合優先級、初始值警告與null檢查。
- [ ] **P23-10** 對sorted/navigable集合、map重復keys、泛型bounds、空集合、連續build建立實際運行測試。
- [ ] **P23-11** 驗證Java和Teyru consumer均可使用builder API，LSP能看到構造前尚未生成文件的builder符號。
- [ ] **P23-12** 由獨立reviewer逐參數差分，不能僅拿一個Person.builder成功例子通過。

**階段出口：** Builder及所有baseline Singular集合/配置有真實行為和API相容證據。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P24 — SuperBuilder／With／WithBy

**負責：** lombok + interop  
**依賴：** P23  
**產物：** 繼承builder與複製更新family

- [ ] **P24-01** 實現SuperBuilder跨多層繼承的builder類型參數與self類型，不退化為raw/Object。[S17]
- [ ] **P24-02** 處理abstract class、不同可見性constructors、父類字段與subclass字段的初始化順序。
- [ ] **P24-03** 實現toBuilder、Singular、Default、setterPrefix及baseline允許的自定義builder結構。
- [ ] **P24-04** 明確SuperBuilder與普通Builder不兼容組合的準確診斷，不偷偷選擇其中一個。
- [ ] **P24-05** 實現With的全部access、字段/類型應用、constructor依賴、final/static跳過與同值返回約定。
- [ ] **P24-06** 實現baseline存在的WithBy函數更新，選用正確primitive operator或通用函數型別。[S15]
- [ ] **P24-07** 測試NonNull、primitive narrow conversions、null函數/結果、泛型、已有withX方法衝突。
- [ ] **P24-08** 覆蓋父子Java↔Teyru混合繼承、library jar父類與跨module consumer。
- [ ] **P24-09** 檢查generated methods/constructors/generic signature/必要bridge行為，不只測試一次build值。
- [ ] **P24-10** 處理property和不可變類型的複製更新，不把copy update降級成原地setter。
- [ ] **P24-11** 建立多層繼承及builder手工擴展的oracle差分與negative fixture。
- [ ] **P24-12** reviewer從Java編譯一個使用具體subclass builder的方法，確保IDE與javac都看到正確返回型別。

**階段出口：** 繼承泛型builder與不可變更新保持正確類型/API，並通過跨語言消費測試。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P25 — Lazy／Cleanup／SneakyThrows／Locks

**負責：** lombok + qa-review  
**依賴：** P22、P19  
**產物：** 控制流程與併發相關family、必要runtime策略

- [ ] **P25-01** 實現Getter(lazy=true)的合法字段限制、thread safety、null sentinel、泛型/primitive返回。[S14]
- [ ] **P25-02** 測試併發首次訪問、初始化拋錯後的baseline行為、可重入訪問和懶字段被其他Lombok功能引用。
- [ ] **P25-03** 實現Cleanup默認/自定義cleanup方法、scope、null處理、reverse close和異常優先級，不直接等價替換為TWR。
- [ ] **P25-04** 實現SneakyThrows指定/默認異常的保持原throwable行為，不包裝成RuntimeException。[S13]
- [ ] **P25-05** 處理SneakyThrows在constructor/body/空方法的合法位置和baseline限制，Java25新constructor規則須專門測試。
- [ ] **P25-06** 若需要helper，建立明示且最小的runtime ABI/依賴/許可證；不得殘留未解析Lombok.sneakyThrow。
- [ ] **P25-07** 實現Synchronized的instance/static/custom lock字段、缺鎖/類型錯誤與持鎖範圍。
- [ ] **P25-08** 實現Locked及Read/Write、custom name、ReentrantLock/ReadWriteLock對應行為與異常安全解鎖。
- [ ] **P25-09** 以事件順序/throwable identity/lock狀態測試控制流程，不靠生成字符串包含try/finally判斷成功。
- [ ] **P25-10** 對多個cleanup/locks/sneaky與return/break/continue/throw的組合執行回歸。
- [ ] **P25-11** 運行有上界併發壓力測試與線程洩漏檢查，失敗不得改成sleep更久後假裝通過。
- [ ] **P25-12** 獨立reviewer審核runtime許可與依賴透明度，以及initializer/cleanup exception的差分結果。

**階段出口：** 控制流程和併發語義與baseline一致；沒有以簡化包裝改變例外或偷偷引入runtime。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P26 — Logging／FieldDefaults／UtilityClass

**負責：** lombok  
**依賴：** P20、P22  
**產物：** 所有logger變體、默認修飾符與utility處理

- [ ] **P26-01** 實現官方baseline全部logging annotations，不只Slf4j/Log4j2；盤點CommonsLog/JBossLog/Flogger/CustomLog等。[S22]
- [ ] **P26-02** 實現topic/category、fieldName、static/final設置、custom declaration與factory參數規則。
- [ ] **P26-03** 外部logging API從consumer classpath解析；不自動安裝日誌實現或改變應用logging binding。
- [ ] **P26-04** 測試logger字段名與用戶字段衝突、annotation非法位置、嵌套/泛型類與static環境。
- [ ] **P26-05** 實現FieldDefaults的level/makeFinal與全局config，明確顯式modifier優先規則。
- [ ] **P26-06** 實現NonFinal/PackagePrivate等marker對Value/FieldDefaults的影響，不能當unknown annotation丟掉。
- [ ] **P26-07** 實現UtilityClass的類/成員static、private throwing constructor、嵌套類與非法constructor診斷。
- [ ] **P26-08** 驗證UtilityClass使用現有Java靜態導入與調用形態，baseline限制/差異必須有測試和文檔。
- [ ] **P26-09** 處理property與utility/static defaults組合，不生成非法instance backing storage。
- [ ] **P26-10** 為每種logging API建立最小真實consumer依賴fixture與Java調用測試，不能用自己假造logger類替代全部驗證。
- [ ] **P26-11** 運行config與默認值的差分，檢查生成字段類型/modifiers及factory調用結果。
- [ ] **P26-12** reviewer檢查依賴樹和runtime jar，沒有因一個logger annotation引入全部日誌框架。

**階段出口：** baseline logging全覆蓋且依賴明確，default modifiers/utility行為與Java約束一致。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P27 — Delegate／ExtensionMethod／Helper／Metadata families

**負責：** lombok + interop  
**依賴：** P16、P22、P26  
**產物：** 高級解析型intrinsics與metadata helper功能

- [ ] **P27-01** 實現Delegate包含/排除接口、泛型替換、繼承方法與顯式成員衝突規則，避免重復bridge簽名。
- [ ] **P27-02** 實現ExtensionMethod的resolution優先級、static helper選擇、泛型/varargs/null receiver與正常成員優先策略。
- [ ] **P27-03** 保持extension調用求值順序與checked exception，不按方法名字進行全局字符串替換。
- [ ] **P27-04** 實現Helper的local class/object支持及scope，避免跨方法洩露生成helper變量。
- [ ] **P27-05** 實現FieldNameConstants的String/enum、innerTypeName、access、uppercase、onlyExplicitlyIncluded與Include/Exclude。
- [ ] **P27-06** 實現Tolerate對生成成員衝突判定的影響，不把被標注手寫方法刪除。
- [ ] **P27-07** 實現StandardException全部baseline constructors、cause/message關係與相關選項。
- [ ] **P27-08** 盤點舊別名/experimental路徑的baseline支持，遷移工具明確處理合法別名與已刪除API。
- [ ] **P27-09** 測試delegate遞歸/循環、泛型erasure衝突、extension overload ambiguity和metadata命名衝突的負面案例。
- [ ] **P27-10** 驗證generated constants可在Java consumer/annotation arguments中正確使用，字段rename關聯metadata有說明。
- [ ] **P27-11** 逐family執行oracle與ABI差分，記錄涉及上游已知限制的準確版本，不替難題隨便標N/A。
- [ ] **P27-12** reviewer用獨立JAR接口/Java源helper構造跨語言測試，確認不是只能調用同文件demo。

**階段出口：** 高級feature依賴真實符號/類型解析，全部baseline metadata功能可消費且有反例。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P28 — onX／Jacksonized／Annotation metadata

**負責：** lombok + interop  
**依賴：** P23、P24、P27  
**產物：** annotation傳播、嚴格metadata profile與framework兼容

- [ ] **P28-01** 實現onMethod/onParam/onConstructor及baseline支持拼法，按目標Java版本解析合法annotation數組語法。
- [ ] **P28-02** 嚴格執行annotation target/retention/repeatable規則，避免FIELD-only注解被複製到METHOD。
- [ ] **P28-03** 處理copyableAnnotations、nullness annotation設置、Generated/ConstructorProperties及nested generated成員上的metadata。
- [ ] **P28-04** 實現Jacksonized對Builder/SuperBuilder/Accessors的baseline行為，檢查已存在JsonDeserialize/JsonPOJOBuilder衝突。
- [ ] **P28-05** 按baseline支持的Jackson版本/配置建立consumer，生成annotation FQN與依賴版本不得混用。
- [ ] **P28-06** 實現嚴格metadata profile所需compile-only definitions與artifact邊界，不把完整Lombok processor帶進正常編譯。
- [ ] **P28-07** 若使用官方MIT annotation source/compiled artifact，保留許可與來源；避免與用戶classpath重復lombok.*定義。
- [ ] **P28-08** native profile與strict profile的不同點由機器矩陣表示，不能靜默丟棄retained annotations還聲稱完全一致。
- [ ] **P28-09** 運行Java annotation processor讀生成getter/builder/parameter metadata的測試，驗證source/CLASS/RUNTIME不同效果。
- [ ] **P28-10** 運行真實Jackson serialize/deserialize roundtrip，包括Builder.Default、Singular、generic與null cases。
- [ ] **P28-11** 驗證Java/Teyru兩端的annotation navigation/hover/source-map，錯誤映射到原始onX位置。
- [ ] **P28-12** 獨立reviewer審核編譯/運行依賴樹、retained annotation reflection與framework差分證據。

**階段出口：** annotation不只是文字搬運：目標、保留、FQN和真實框架/processor消費全部驗證。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P29 — Lombok 設定與全量相容驗收

**負責：** lombok + qa-review + integration  
**依賴：** P21～P28  
**產物：** 完整lombok.config、feature/option/config組合覆蓋報告

- [ ] **P29-01** 實現全部baseline config keys的類型/默認值/生效位置，不支持的鍵仍屬未完成而非靜默忽略。
- [ ] **P29-02** 實現父目錄繼承、stopBubbling、clear、+=/-=/=、import與源文件effective config解釋。[S11]
- [ ] **P29-03** 對config import路徑/循環/越界/未知鍵給出安全且可定位的診斷，避免跨workspace讀取敏感內容。
- [ ] **P29-04** config變化使相應編譯/LSP/Gradle cache失效，不能沿用舊getter命名或舊builder策略。
- [ ] **P29-05** 實現flagUsage warning/error與experimental family政策，官網解釋這是用戶配置不是缺功能。
- [ ] **P29-06** 為全部參數/default/config keys建立自動覆蓋檢查，遺漏的baseline API讓驗收失敗。
- [ ] **P29-07** 對所有family運行單feature、pairwise以及Data/Builder/Value/Accessors/Jacksonized/lazy等重點高階組合。
- [ ] **P29-08** 使用未參與實作的reviewer提供額外Lombok項目fixture，禁止只對自建happy paths通過。
- [ ] **P29-09** 驗證普通Teyru compile不啓動Lombok processor，oracle jar只在測試/明確遷移輔助路徑出現。
- [ ] **P29-10** 盤點lombok.Lombok utility與legacy import遷移，明確API依賴/替代；不能破壞實際用戶源碼。
- [ ] **P29-11** 生成feature/option/config/ABI/runtime/metadata/IDE coverage報告，有known difference必須有需求ID和未完成狀態。
- [ ] **P29-12** 所有baseline必要項VERIFIED後方可標P29完成；不得將剩餘難項寫成後續路線圖以通過本次full scope。

**階段出口：** 完整Lombok由全量registry和差分測試證明；只有默認Data/Builder通過不能結案。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P30 — 共用 Language Tooling 與增量索引

**負責：** lsp + interop  
**依賴：** P08、P16、P17、P19  
**產物：** editor-neutral analysis service、snapshot、project index

- [ ] **P30-01** 建立language-tooling公開查詢/編輯API，LSP與IDE投影共用同一compiler語義與SymbolId。
- [ ] **P30-02** 將document overlay、workspace model、classpath與generated metadata組成不可變analysis snapshot。
- [ ] **P30-03** 支持open/close/change/version的增量失效，編輯method body不無謂重建全部JDK/JAR索引。
- [ ] **P30-04** 對聲明/ABI變化、刪檔/rename、config/toolchain/classpath變化實施正確跨文件失效。
- [ ] **P30-05** 建立JDK/JAR/source index緩存，schema升級/損壞可重建，不能把錯誤緩存當正確結果。
- [ ] **P30-06** 實現background分析的取消/去重/優先級，但所有process生命週期受server管理，不無限排隊。
- [ ] **P30-07** 合成getter/builder/property成員保留origin關係，文檔/符號/調用層次使用同一ID。
- [ ] **P30-08** 跨Java/Teyru/current buffer/外部source JAR導航使用統一URI和位置模型。
- [ ] **P30-09** 未信任workspace禁跑Gradle/processor，能夠以已提供model或有限只讀模式服務並明確狀態。
- [ ] **P30-10** 添加高頻編輯/舊請求/併發query/close後請求的競態測試，不以共享mutable AST偷懶。
- [ ] **P30-11** 實現可觀察的cache/analysis統計和本地diagnostic工具，默認不外傳源碼與遙測。
- [ ] **P30-12** reviewer驗證language-tooling可在無LSP、無IDEA的純JVM測試環境查詢真實代碼。

**階段出口：** 語義服務獨立且增量一致，為所有客戶端提供同一份語言事實。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P31 — Completion／Navigation／Hover／Signature Help

**負責：** lsp  
**依賴：** P30、P29  
**產物：** 核心語言智能handlers與Java互通

- [ ] **P31-01** 實現lexical scope、members、imports、types、constructors、keywords的上下文completion，而非全文件詞彙表。
- [ ] **P31-02** Java String/ArrayList及外部JAR generic methods能顯示正確參數/返回類型與文檔。
- [ ] **P31-03** 在不完整expression、property accessor、builder chains、lambda target位置給相關且排序合理的候選。
- [ ] **P31-04** 實現completion resolve、textEdit/additionalTextEdits/snippets與client capability gating。
- [ ] **P31-05** 實現hover：聲明、文檔、property accessor與推斷型別，普通field與property差異清楚。
- [ ] **P31-06** 實現signatureHelp的重載、active parameter、varargs、nested calls與generic substitution。
- [ ] **P31-07** 實現definition/declaration/typeDefinition/implementation，來源優先原始Teyru/Java/source JAR而非generated Java。
- [ ] **P31-08** 生成getter/setter/builder的導航回field/annotation/property origin，不能全部跳到class第一行。
- [ ] **P31-09** 實現document/workspace symbols，涵蓋native與generated API，穩定排序並限制大workspace結果。
- [ ] **P31-10** 測試Java↔Teyru/跨module/跨JAR/未保存buffer以及private/protected不可見成員過濾。
- [ ] **P31-11** 對位置編碼、completion resolve過期snapshot與cancelled requests建立black-box tests。
- [ ] **P31-12** reviewer用一個真實mixed project進行補全/跳轉腳本驗證，假集合或null結果不能計為支持。

**階段出口：** 補全、提示、簽名與導航能處理真實Java泛型和生成API，不只支持Teyru自己的簡單名字。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P32 — Diagnostics／Quick Fix／Formatting

**負責：** lsp + compiler  
**依賴：** P31、P15  
**產物：** 診斷、格式化、code actions與修復pipeline

- [ ] **P32-01** 實現push/pull diagnostics及workspace diagnostics，按client能力選擇並避免重復上報。
- [ ] **P32-02** 診斷帶stable code、related information、doc link與準確Teyru位置；修復後清除stale errors。
- [ ] **P32-03** 實現真正可應用的semicolon移除、顯式型別提示、missing import、可見性/缺getter-setter等已定義quick fixes。
- [ ] **P32-04** 對var=null不憑空猜String；提供填寫明確型別的安全操作或有語義證據的建議。
- [ ] **P32-05** 實現organize imports，保留注釋、static import、同名類型、Lombok virtual imports與必要annotations。
- [ ] **P32-06** 實作全文件/range/on-type formatter，遵守B2並保持parse AST等價和冪等。
- [ ] **P32-07** Range edits不能破壞周圍語句邊界；on-type不在每個按鍵全量重新格式化整個project。
- [ ] **P32-08** codeAction resolve使用當前snapshot/版本校驗，過期編輯需重算或拒絕，不覆寫用戶新輸入。
- [ ] **P32-09** 對未知錯誤code/unsupported action明確返回協議允許結果，而不是advertise固定空handler。
- [ ] **P32-10** 為format→check與quickfix→recompile建立端到端驗證，防止推薦修復生成不可編譯代碼。
- [ ] **P32-11** 確保LSP/CLI使用同一formatter/fix engine，沒有不同客戶端風格漂移。
- [ ] **P32-12** reviewer在中文/emoji/CRLF/多行lambda/property/for/enum上應用編輯並比較結果。

**階段出口：** 診斷可定位、修復能生效、格式化不改語義；並有真實客戶端應用編輯的證據。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P33 — References／Rename／Workspace File Operations

**負責：** lsp + interop  
**依賴：** P31、P32  
**產物：** 語義引用與跨語言重命名、文件操作支持

- [ ] **P33-01** 實現symbol-based references與documentHighlight，區分同名局部變量、重載、shadowing與字符串內容。
- [ ] **P33-02** prepareRename驗證目標可重命名、權限、origin與generated/read-only文件，不對任意詞語提供rename。
- [ ] **P33-03** property rename同時處理Teyru訪問與Java生成getter/setter調用的對應關係，按實際命名規則生成編輯。
- [ ] **P33-04** getter/setter顯式命名與fluent/Accessors cases有清晰策略，不把用戶手寫獨立方法錯誤捆綁。
- [ ] **P33-05** 跨Java/Teyru/multi-module/unsaved buffers搜索完整引用，jar-only外部使用不能偽造源碼編輯。
- [ ] **P33-06** 實現class/file/package rename及workspace will/did create/rename/delete適用流程，維護model與index。
- [ ] **P33-07** 檢測名稱/overload/accessor collision，產生preview與拒絕診斷，不在編譯失敗後才發現。
- [ ] **P33-08** 只編輯原始來源和允許的配置/metadata，不直接修改build/generated Java作為長期修復。
- [ ] **P33-09** workspace edits帶document versions；併發編輯/文件搬移導致版本衝突時安全拒絕或重新計算。
- [ ] **P33-10** 提供鏈接編輯的真實適用場景或規範N/A理由，不能把普通rename全部誤當linked editing。
- [ ] **P33-11** 應用rename後真實Gradle build/test通過，同時保留不應改變的comments/strings反例。
- [ ] **P33-12** reviewer構造getName重載、兩個不同User類、序列化字段名等容易誤改情境進行驗收。

**階段出口：** 重命名是可編譯、可預覽、跨語言的語義操作，不是grep替換。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P34 — Semantic Tokens／Hints／Hierarchy 等完整能力

**負責：** lsp  
**依賴：** P31、P33  
**產物：** 高級LSP handlers與全method矩陣

- [ ] **P34-01** 實現semantic tokens full/delta/range，穩定legend與resultId，delta基線失效時正確回full。
- [ ] **P34-02** 區分class/typeParameter/method/property/field/local/readonly/static/deprecated，token不越界且不重疊違規。
- [ ] **P34-03** 實現inlayHint/resolve，val/var推斷類型等提示基於真實type model，用戶可配置密度。
- [ ] **P34-04** 實現codeLens/resolve的真實引用/運行測試等適用動作，命令不得無allowlist執行任意shell。
- [ ] **P34-05** 實現foldingRange、selectionRange與documentLink/resolve，適配不完整buffer和Javadoc/source links。
- [ ] **P34-06** 實現callHierarchy incoming/outgoing與typeHierarchy super/subtypes，涵蓋Java繼承和generated accessor origin。
- [ ] **P34-07** 實現動態注冊/refresh requests與client capability兼容，舊客戶端不接收它無法處理的必需payload。
- [ ] **P34-08** 遍歷LSP3.18 method inventory，記錄支持、明確不適用、尚未實現，並關聯協議測試。[S05]
- [ ] **P34-09** 對color/notebook/inline-value等非核心family給具體適用性審查，不為充數生成假內容。
- [ ] **P34-10** 處理大結果progress/partial response、cancel與分頁/限制，不因一個workspace symbols請求耗盡RAM。
- [ ] **P34-11** 驗證所有advertised capability都有真實行為和至少一個positive/negative測試，空handler不能變綠。
- [ ] **P34-12** reviewer以客戶端能力最小/完整兩種profiles運行，確認沒有錯誤假定某IDE實現全部方法。

**階段出口：** 核心及進階LSP能力完成，整個method inventory可審核，所有N/A都有合理理由。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P35 — LSP 協定韌性、安全與壓力測試

**負責：** qa-review + lsp  
**依賴：** P30～P34  
**產物：** protocol conformance、race/fault/restart tests

- [ ] **P35-01** 建立獨立JSON-RPC black-box harness，測試拆分frame、多個frame、UTF-8長度、malformed headers和invalid JSON。
- [ ] **P35-02** 測試initialize順序、重復初始化、unknown methods、shutdown/exit、client斷線與server崩潰恢復。
- [ ] **P35-03** 檢查stdout沒有日誌污染、stderr不輸出token/secret/整份私有源代碼。
- [ ] **P35-04** 測試request cancellation、document version競態、delta token stale result、pull diagnostics previousResultId。
- [ ] **P35-05** 多root workspace、symlink、URI編碼、空格/中文文件名、Windows drive與路徑大小寫正確處理。
- [ ] **P35-06** 施加source尺寸/遞歸深度/輸出大小上限，惡意輸入不能無限分配或死循環。
- [ ] **P35-07** 測試untrusted workspace打開時不會跑Gradle、processor、下載腳本或用戶應用。
- [ ] **P35-08** 模擬classpath/JAR/config被更新或刪除，server能失效重建並給出正確狀態。
- [ ] **P35-09** 持續編輯/關閉/重開大量文檔，測memory/file-handle/thread是否有無界增長。
- [ ] **P35-10** 測試client不支持特定capability時的降級與message shape，不用發送假標準擴充字段。
- [ ] **P35-11** 記錄可重現seed、環境、響應/heap基線與失敗日誌；壓力失敗不能靠刪除負載用例解決。
- [ ] **P35-12** reviewer獨立運行protocol suite且重放至少一個歷史失敗樣例，確認修復真正有效。

**階段出口：** server能承受真實編輯、取消、故障與不可信輸入，協議和資源行為有證據。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P36 — 獨立客戶端與跨編輯器接入

**負責：** lsp + docs-web + qa-review  
**依賴：** P35  
**產物：** 非IDEA接入樣例、客戶端矩陣、獨立server安裝說明

- [ ] **P36-01** 提供獨立teyru-lsp發行目錄與啓動文檔，不要求安裝Teyru IDEA plugin。
- [ ] **P36-02** 製作至少一個非IDEA客戶端的可執行接入示例，優先已有LSP client庫/實際editor避免另造協議。
- [ ] **P36-03** 提供VS Code/Neovim/Zed等常見客戶端配置說明，具體選項從對應版本官方文檔驗證後記錄。
- [ ] **P36-04** 區分「提供配置示例」「自動化驗證」「人工驗證」狀態，不把未跑過的編輯器列為完整支持。
- [ ] **P36-05** 同一server binary在IDEA與獨立client的diagnostic/symbol/rename結果一致。
- [ ] **P36-06** 驗證工作目錄、classpath/model發現、JDK選擇、multi-root與stdio日誌位置。
- [ ] **P36-07** 客戶端缺能力時保留server能力但說明UI限制，不能誤判語言功能沒實現。
- [ ] **P36-08** 測試斷開重連、server版本不匹配、無JDK與model缺失的用戶可讀提示。
- [ ] **P36-09** 通過外部腳本client演示check/hover/rename並真實應用WorkspaceEdit後build，不只是發initialize成功。
- [ ] **P36-10** 提供無GUI/headless CI使用方式，供Codex等工具消費結構化語言信息。
- [ ] **P36-11** 核心接入說明與已執行fixture聯動，代碼塊複製後不含不存在的命令或未發佈版本。
- [ ] **P36-12** reviewer在乾淨安裝位置運行非IDEA路徑，確保server沒有偷偷依賴IDE安裝目錄。

**階段出口：** 獨立server可以被其他客戶端實際使用，跨編輯器能力狀態誠實且可復現。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P37 — Gradle 完整 sourceSet、cache 與增量

**負責：** build + interop  
**依賴：** P09、P19、P29  
**產物：** 生產級Gradle task模型、緩存與失效測試

- [ ] **P37-01** 完善main/test/custom source sets、test fixtures、多module以及對需要的composite builds支持。
- [ ] **P37-02** 通過Provider/Property/DirectoryProperty與lazy registration建模，不在configuration階段跑compiler。
- [ ] **P37-03** 聲明所有影響輸出的inputs：Java/Teyru source、Lombok config、classpath、module path、JDK/target、compiler version、options。
- [ ] **P37-04** configuration cache連續兩次執行確實reused，task action不得讀取不允許的Project狀態。
- [ ] **P37-05** build cache驗證FROM-CACHE、跨路徑relocation與正確key；不能只檢查cache目錄存在。
- [ ] **P37-06** 增量策略至少正確實現dependency-aware或保守重編；改public signature與body-only變化分別驗證。
- [ ] **P37-07** 測試增加/刪除/重命名source、修改config/annotations、替換JAR/JDK、刪除生成目錄與損壞metadata。
- [ ] **P37-08** 生成與javac task不存在cycle；分析需要的Java sources可作為inputs而不是依賴已編譯classes。
- [ ] **P37-09** sourceSet並行構建不共享可變output目錄，統一重型compiler併發預算且無文件鎖死。
- [ ] **P37-10** 錯誤mapping在Gradle控制台和IDE build窗口一致，javac失敗正確傳播非零exit。
- [ ] **P37-11** TestKit覆蓋乾淨/熱構建、離線已緩存依賴、路徑含空格、Windows兼容與至少兩種支持Gradle版本。[S08]
- [ ] **P37-12** reviewer審查真正任務outcomes與緩存報告，UP-TO-DATE/FROM-CACHE不可憑文字偽造。

**階段出口：** 插件不僅能build，還能正確緩存、失效、刪檔、多sourceSet與並行運行。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P38 — Build 生態、Java 框架與真實 consumer

**負責：** build + interop + qa-review  
**依賴：** P37、P28  
**產物：** 混合項目與外部框架整合fixtures

- [ ] **P38-01** 集成Java application/library plugins、toolchains、JPMS、source/Javadoc JAR與必要annotation processing配置。
- [ ] **P38-02** 建立多module Java→Teyru→Java調用和跨jar property metadata消費測試。
- [ ] **P38-03** 建立Spring基礎組件/JavaBean綁定sample，按實際框架要求設置constructors/access，不宣稱有getter就全兼容。
- [ ] **P38-04** 建立Jackson常規bean與Jacksonized builder樣例，覆蓋retained annotations和泛型。
- [ ] **P38-05** 建立JPA/Hibernate基礎實體訪問策略fixture，區分field access與property access，驗證不意外改變持久化行為。
- [ ] **P38-06** 建立至少一個真實annotation-processing消費者（如MapStruct）；若選擇Jimmer必須按其接口/生成模型規範測試而不是假設普通bean適配。
- [ ] **P38-07** 驗證第三方logger API與Guava Singular依賴按consumer配置進入正確classpath，runtime不重復注入。
- [ ] **P38-08** 混合項目中原Java使用Lombok的情況明確支持策略：用戶可保留Java自身processor，Teyru仍走原生路徑，防止double-generation。
- [ ] **P38-09** 測試maven-publish生成POM/module metadata、consumer runtime/compileOnly依賴以及optional helper模式。
- [ ] **P38-10** 測試Java doc generation/source attachment不會暴露臨時路徑或丟失Teyru原始source mapping。
- [ ] **P38-11** 所有樣例從公開可獲取依賴、確定版本和本地plugin/artifacts起步，離線緩存模式另做測試。
- [ ] **P38-12** reviewer按每個框架的真實結果驗收，未支持的特定框架能力不能擴稱全部生態兼容。

**階段出口：** 真實Java生態消費得到驗證，接口/注解/處理器與運行依賴都正確而非口頭承諾。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P39 — CLI 完整命令、安裝與 Agent UX

**負責：** build + compiler + docs-web  
**依賴：** P07、P37、P19、P43  
**產物：** 完整CLI、distributions、診斷/檢查/格式化/inspection

- [ ] **P39-01** 完成C3全部命令並統一--help/usage/error形式；未實現命令不得以空success返回。
- [ ] **P39-02** check與compile共用語義，check不意外運行應用；emit-java僅輸出明示目錄與source map。
- [ ] **P39-03** 實現format --check、穩定exit code、stdin/stdout必要工作流與UTF-8/CRLF政策。
- [ ] **P39-04** 實現inspect AST/symbols/source-map JSON，默認不輸出巨大結構，支持文件/範圍選擇與上限。
- [ ] **P39-05** 實現explain穩定錯誤碼與docs路徑；診斷含原因/相關位置/最小建議而不猜不存在的類型。
- [ ] **P39-06** 提供JSON與SARIF診斷出口，驗證schema與source URI，human output不作為機器API。
- [ ] **P39-07** 實現doctor對JDK/plugin/server/workspace version mismatch的診斷，不自動修改用戶全局環境。
- [ ] **P39-08** 建立Linux/macOS/Windows launcher/distribution，說明JDK要求與checksum，不能假稱native binary已存在。
- [ ] **P39-09** 處理退出、信號、timeout、路徑引用與併發輸出，避免損壞manifest或留下僵屍process。
- [ ] **P39-10** consumer模式支持本地構建與正式artifact配置切換，未發佈時docs不能聲稱公網命令已可下載。
- [ ] **P39-11** 從安裝包而非IDE classpath執行CLI integration suite，並檢驗嚴格metadata/helper profile依賴。
- [ ] **P39-12** reviewer按AI工作流check→JSON→修復→test執行示例，確認沒有依賴模型解析彩色日誌。

**階段出口：** CLI是可安裝、可自動化、錯誤行為穩定的產品入口，不是一個僅能跑main的內部工具。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P40 — IDEA 客戶端產品化與生命週期

**負責：** ide + build  
**依賴：** P11、P36、P38  
**產物：** 生產級IDEA plugin、設置、版本/權限/進程管理

- [ ] **P40-01** 完成支持IDE build矩陣、since/until兼容聲明、plugin dependencies與Plugin Verifier檢查。[S06]
- [ ] **P40-02** server選擇支持bundled/指定路徑/項目版本策略，下載若需要須用戶允許且校驗簽名/hash。
- [ ] **P40-03** JDK/server版本不匹配提供明確修復UI，禁止默默切換用戶項目target release。
- [ ] **P40-04** 處理project open/close/reimport、多module、多個IDE窗口、server重啓與異常退出。
- [ ] **P40-05** server啓動不阻塞UI thread，取消/關閉項目後釋放進程、文件監聽和listeners。
- [ ] **P40-06** 整合Gradle導入的workspace model adapter，刷新classpath不需要重啓整個IDE。
- [ ] **P40-07** 設置界面可控制format/hints/diagnostics/server logs/trust，不複製語言語義選項為另一套默認。
- [ ] **P40-08** 統一LSP與IDE本身重復診斷/生成源碼索引策略，避免兩個error提示互相覆蓋。
- [ ] **P40-09** 對不受支持的IDE發行物安全拒絕安裝/加載，並在官網矩陣說明，不偽造Community全面支持。
- [ ] **P40-10** 完整run/test configuration、錯誤console hyperlinks與source map，執行consumer的真實Gradle任務。
- [ ] **P40-11** 使用IDE sandbox運行安裝/啓停/項目切換自動化或可重現UI測試，證據包含準確build。
- [ ] **P40-12** reviewer檢測多個窗口/快速關開項目/語言服務器崩潰後的恢復和UI響應。

**階段出口：** IDEA插件具有真實生命週期、配置與兼容性，language-server仍保持完全獨立。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P41 — IDEA Java 深度互通與原始來源導航

**負責：** ide + interop  
**依賴：** P40、P31、P33  
**產物：** Java PSI投影/導航、混合語言refactoring與生成API可見性

- [ ] **P41-01** 選擇server導出signature/metadata→IDE PSI light projection或等價adapter，ADR解釋為甚麼不重復語義分析。
- [ ] **P41-02** Java編輯器在未build時可補全Teyru classes、constructors、getters/setters、Builder/SuperBuilder成員。
- [ ] **P41-03** Java getName()/builder調用的Ctrl+B回Teyru field/property/annotation，不能只跳generated Java。
- [ ] **P41-04** Teyru編輯器導航到Java/source JAR也正確，Java/Teyru交叉reference結果一致。
- [ ] **P41-05** 處理generated Java與light elements的索引去重、緩存失效、declaring class identity與overload。
- [ ] **P41-06** IDE rename可觸發共用server重命名並應用Java/Teyru edits，collision/preview/version檢查保留。
- [ ] **P41-07** Java源未保存更改能影響Teyru completion，Teyru未保存成員更改能更新Java投影。
- [ ] **P41-08** 處理package-private/protected、nested/generic types、records/sealed、static imports與跨module依賴。
- [ ] **P41-09** 展示property區別與訪問器結構，generated helper不應淹沒Structure/Completion。
- [ ] **P41-10** 針對IDE原生API確實缺能力的版本，提供adapter或提高支持下限並準確記錄，不把LSP支持等同UI支持。
- [ ] **P41-11** 做真實IDE UI測試：從Java補全新生成builder、跨語言rename、重建後沒有重復符號或殘留紅線。
- [ ] **P41-12** reviewer審查plugin依賴與實現，確保沒有偷偷把compiler/resolver複製到IDEA內再跑一遍。

**階段出口：** Java用戶在IDEA中能自然消費Teyru與生成API，導航和重構回到原始源碼。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P42 — Teyru 除錯、斷點與執行來源映射

**負責：** ide + interop + qa-review  
**依賴：** P41、P19  
**產物：** 真實Java debugger集成／必要adapter、debug證據

- [ ] **P42-01** 建立debug ADR：採用IDE Java debugger position mapping/JDWP或獨立DAP策略；明確這不是LSP功能。
- [ ] **P42-02** 生成Java保留正確調試信息和origin map，必要元資料處理不得變成另一個JVM instruction compiler。
- [ ] **P42-03** 在Teyru原始文件設置斷點，映射到正確generated Java/class位置並能實際命中。
- [ ] **P42-04** 支持普通method、constructor、多行lambda、property getter/setter、Lombok-generated業務API的可解釋停靠。
- [ ] **P42-05** 驗證step into/over/out、locals、參數、this、exception breakpoints，不只測試run成功。
- [ ] **P42-06** synthetic helper/展開代碼的多個生成行映射到源節點時有一致策略，不能跳到隨機相鄰行。
- [ ] **P42-07** 處理source與class版本不一致，提示rebuild或拒絕誤導性斷點，不靜默用舊map。
- [ ] **P42-08** console/stacktrace提供Teyru導航同時保留真實raw frame信息，不承諾任意第三方debugger自動支持。
- [ ] **P42-09** 跨Java/Teyru調用可來回step，外部JAR仍正常使用Java調試機制。
- [ ] **P42-10** 測試僅返回異常的程序與優化/無debug信息構建，限制須準確說明。
- [ ] **P42-11** 保存可重現debug fixture、IDE/JDK版本、自動化或人工測試記錄，沒有GUI環境不能標verified。
- [ ] **P42-12** reviewer親自命中property和lambda斷點並檢查locals，證明不是僅將diagnostic行號轉換成功。

**階段出口：** 原始Teyru斷點與單步真實可用；source-map、LSP和debug邊界沒有混淆。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P43 — Java／Lombok 遷移器

**負責：** compiler + lombok + docs-web  
**依賴：** P15、P19、P29、P38、P07  
**產物：** migration命令、diff/報告、可回滾語法感知轉換

- [ ] **P43-01** 實現Java source parser驅動的遷移，不用全局replace(';','')破壞字符串/for/enum。
- [ ] **P43-02** 刪除語句尾分號；basic-for/enum結構分號改成規範冒號；TWR轉換為resource換行；empty statement轉{}。
- [ ] **P43-03** 保留comments/Javadoc/annotations、字面量/text blocks、格式敏感資料與Java語義。
- [ ] **P43-04** 保留Lombok imports/annotations的原生兼容寫法，按baseline處理val/var與舊別名，不強制一次全轉native property。
- [ ] **P43-05** 提供可選顯式getter/setter→property轉換，只有證明形狀和訪問語義安全時自動執行，複雜情況報告不改。
- [ ] **P43-06** 支持dry-run、統一diff、JSON report、--apply、輸出到新目錄及顯式覆蓋政策，不默認破壞原.java。
- [ ] **P43-07** 保留包結構與文件名，轉換後更新指定source roots/build配置但不擅自修改無關module。
- [ ] **P43-08** 轉換遇到不支持語法/衝突/processor依賴時報告具體位置與動作，不產出看似成功的不可編譯文件。
- [ ] **P43-09** 遷移後使用原有Java tests和Teyru consumer真實build，驗證行為/ABI而不只比較轉換文本。
- [ ] **P43-10** 針對多line lambda、anonymous classes、records/sealed、module-info、Unicode escape和empty enum建立fixtures。
- [ ] **P43-11** 實現冪等/重復執行/部分失敗/撤銷資料策略，確保不會重復添加colon或丟注釋。
- [ ] **P43-12** 獨立reviewer遷移一個混合Lombok小項目，從diff到build/test走完整流程，保留未自動轉換項。

**階段出口：** 遷移是語法感知、可預覽、保留語義的工具，而不是改副檔名加刪分號腳本。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P44 — 可執行 Examples 與文件程式碼

**負責：** docs-web + qa-review  
**依賴：** P38、P42、P43、P39  
**產物：** examples目錄、docs fixture提取器、學習路徑

- [ ] **P44-01** 建立hello-world、普通field/property、val/var/null、multiline lambda、for/try/enum差異的最小可執行例子。
- [ ] **P44-02** 建立Java↔Teyru mixed-module、多module/library property metadata、Lombok Builder/SuperBuilder組合樣例。
- [ ] **P44-03** 建立Jackson/processor/logging及受測Java框架樣例，依賴版本與main/test命令完整。
- [ ] **P44-04** 建立CLI診斷/JSON、LSP/headless、IDEA run/debug、migration before/after示例。
- [ ] **P44-05** 每個例子包括README、預期行為、執行命令、toolchain/dependencies與可用狀態，不能只放源碼無build。
- [ ] **P44-06** 從實際fixture抽取官網代碼塊，禁止官網與測試維護兩份會漂移的代碼。
- [ ] **P44-07** Java生成示例由編譯器生成並比對，顯示正常Java分號，不把Java例子也誤格式化成Teyru。
- [ ] **P44-08** 錯誤示例有expected diagnostic/exit code，不能混入普通build並用skip掩蓋失敗。
- [ ] **P44-09** 樣例默認不需要秘密/API key/雲賬戶；需要外部服務的示範必須有本地可運行替代fixture。
- [ ] **P44-10** 樣例JAR/source/docs按授權manifest清楚標示，用戶複製代碼的條件不可含糊。
- [ ] **P44-11** 全例子在乾淨工作目錄運行並進入verifyAll，CI分平台測試可明確矩陣。
- [ ] **P44-12** reviewer僅憑examples README執行，修正文檔漏寫依賴/命令/未發佈artifact問題。

**階段出口：** 所有公開示例都能對應真實編譯/運行結果，文檔不是脫離實現的偽代碼宣傳。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P45 — 正式文件管線與規範網站結構

**負責：** docs-web + spec  
**依賴：** P03、P11；可提前搭建，完整內容驗收依賴 P44  
**產物：** docs內容源、spec發佈、版本/翻譯/代碼提取管線

- [ ] **P45-01** 建立Markdown-first單一內容源，spec規範與docs教程清晰區分，網站不複製另一套語言定義。
- [ ] **P45-02** 完成C6所列所有路由的內容清單、owner、需求ID與完成狀態；草稿與已驗證文檔分離。
- [ ] **P45-03** 設立英文與繁體中文核心文檔目錄、語言切換和明確fallback，不生成偽翻譯佔位頁。
- [ ] **P45-04** 每頁有標題/摘要/語言版本/最後驗證版本/相關需求/源文檔路徑等必要metadata。
- [ ] **P45-05** 接入可執行代碼塊提取與鏈接檢查；錯誤例子標明預期失敗，正常例子必須真實build。
- [ ] **P45-06** 將Java語法差異和for/enum冒號、property visibility/null規則放入快速入門，不埋在附錄。
- [ ] **P45-07** 將Lombok/Java/LSP/IDEA/Gradle兼容矩陣由機器清單生成，未驗證不顯示成功徽章。
- [ ] **P45-08** 編寫安裝/離線/Gradle/IDEA/LSP/錯誤排查/Debug/遷移/許可證/貢獻/安全文檔。
- [ ] **P45-09** 錯誤碼頁面由compiler registry生成索引，每個診斷有原因、原始例子與正確修復，不猜用戶業務。
- [ ] **P45-10** 建立版本切換、stable與development內容隔離，舊版URL不跳到語義不同的新頁。
- [ ] **P45-11** 提供docs lint、frontmatter validation、dead links、孤立頁面與重復slug檢查。
- [ ] **P45-12** reviewer檢查每項硬性需求能在規範/教程中找到，內容與實際實現狀態相符。

**階段出口：** 正式規範與入門/參考文檔可發佈且覆蓋全部產品面，不以README代替官網文件。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P46 — 完整官網實現與 UI 驗收

**負責：** docs-web + qa-review  
**依賴：** P45  
**產物：** 可build/deploy的Astro+Starlight站點、完整頁面與UI測試

- [ ] **P46-01** 按鎖定版本建立Astro+Starlight/TypeScript站點與lockfile，真正生成靜態HTML。[S19]
- [ ] **P46-02** 實現品牌首頁：Teyru名稱、主張、真實Teyru/Java對照、明確安裝入口與當前完成狀態。
- [ ] **P46-03** 實現全部文檔路由、側邊導航、目錄、站內搜尋、代碼複製、anchors、404與版本選擇。
- [ ] **P46-04** 實現淺/深主題、可讀字級/行距/代碼區、鍵盤導航與focus狀態，避免過多營銷卡片/動效。
- [ ] **P46-05** 在手機/平板/桌面測試導航、表格、長代碼、中文換行、暗色語法高亮，沒有橫向溢出破版。
- [ ] **P46-06** 代碼對照頁由真實生成Java支持，若展示交互轉譯只使用可信預構建fixture，不偷偷建公網代碼執行器。
- [ ] **P46-07** 處理HTML/Markdown代碼注入、外部鏈接、CSP/資產策略與隱私；默認無不必要追蹤腳本。
- [ ] **P46-08** 設置可配置site URL/base path，未擁有域名時使用本地/測試地址，不聲稱已有正式官網部署。
- [ ] **P46-09** 建立sitemap/robots/canonical/meta/OpenGraph/feeds，preview和production來源不能混淆。
- [ ] **P46-10** 用瀏覽器自動化測試搜索、複製、語言/版本切換、深鏈接、移動菜單和broken routes。
- [ ] **P46-11** 保存實際截圖/可訪問性檢查/構建輸出，manual未執行項清楚標記而非全部passed。
- [ ] **P46-12** reviewer從新用戶視角完成下載→Gradle→IDEA→語法→診斷查閱路線，移除不存在鏈接與虛構數字。

**階段出口：** 這是完整可運行文檔官網，不是一張landing截圖或空側邊欄；發佈狀態誠實。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P47 — llms.txt、AI 文件包與機器可讀介面

**負責：** docs-web + lsp + qa-review  
**依賴：** P39、P45、P46  
**產物：** llms.txt/full、逐頁Markdown、AI context及schemas

- [ ] **P47-01** 由規範/文檔同一內容源生成根llms.txt，包含版本/定位/重要約束與主題Markdown鏈接。[S18]
- [ ] **P47-02** 生成llms-full.txt並標版本/來源，不手寫另一份可能漂移的完整規格。
- [ ] **P47-03** 為每個公開文檔頁提供乾淨Markdown URL，內容不含導航垃圾/HTML腳本/重復footer。
- [ ] **P47-04** 設置rel=alternate text/markdown與describedby鏈接；按當前提案驗證，不宣傳為強制行業標準。
- [ ] **P47-05** 提供按topic/version拆分的AI資料包、短ai-context.md與離線下載，避免要求主session一次讀全部full文件。
- [ ] **P47-06** 資料包突出Java-like不是Kotlin、無分號、局部var/val、property可見性、真實命令與測試路徑。
- [ ] **P47-07** 發佈diagnostics/workspace model/compatibility/release manifest JSON schemas，schemaVersion可機器驗證。
- [ ] **P47-08** 提供agent工作流程check→JSON診斷→最小修改→tests→emit-java檢查，不默認把源碼上傳到外部服務。
- [ ] **P47-09** AGENTS/project rules與網頁內容邊界明確，文檔內不包含要求忽略用戶規則的prompt injection。
- [ ] **P47-10** 驗證llms鏈接、Markdown/HTML語義一致、版本正確、生成決定性與目錄覆蓋率。
- [ ] **P47-11** 用獨立headless消費者只讀取短索引與相關頁完成例子，記錄缺失信息並修正。
- [ ] **P47-12** reviewer檢查沒有「添加llms.txt所有模型就保證會讀」等無證據宣傳，以及生成文件不洩露本地路徑/秘密。

**階段出口：** AI資料與人類文檔同源、可分塊、可離線、可機器驗證；不是只放一個空llms.txt。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P48 — Changelog、版本協調與相容資訊發布

**負責：** build + docs-web  
**依賴：** P29、P36、P38、P45  
**產物：** release notes管線、version manifest、migration notes/feeds

- [ ] **P48-01** 建立統一產品version source，compiler/CLI/server/Gradle/IDEA與docs準確對齊。
- [ ] **P48-02** 實現CHANGELOG.md與變更片段聚合，分類Added/Changed/Fixed/Deprecated/Removed/Security/Compatibility。
- [ ] **P48-03** 每項用戶可見改變關聯需求/issue/ADR與實際版本，未發佈功能留Unreleased不假稱已上線。
- [ ] **P48-04** 語言grammar/ABI/source-map/schema breaking changes提供明確migration recipe與兼容profile說明。
- [ ] **P48-05** 生成官網changelog、changelog.json與RSS/Atom，不手動維護三份不同內容。
- [ ] **P48-06** 官網顯示Java/Lombok/Gradle/IDEA/LSP支持矩陣的證據版本和未驗證狀態。
- [ ] **P48-07** server/client/compiler不兼容時給可操作診斷，不能只靠同版本號假設完全兼容。
- [ ] **P48-08** 處理pre-release/穩定版選擇和文檔URL，舊release的下載/說明保持不可變。
- [ ] **P48-09** 為公開release manifest定義產物名稱/hash/platform/JDK/license與source reference。
- [ ] **P48-10** 沒有外部發佈權限時用本地release candidate，不寫「Plugin Portal已發佈」或製造下載計數。
- [ ] **P48-11** 以測試驗證版本字符串、artifact/POM/plugin.xml/CLI --version/網站頁面一致。
- [ ] **P48-12** reviewer檢查changelog無誇大完成度、無缺失breaking change且可由manifest重建。

**階段出口：** 發佈信息與真實版本/能力一致，具備人類與機器可消費的changelog。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P49 — 全域相容、Fuzz 與回歸測試加固

**負責：** qa-review + integration  
**依賴：** 所有核心語言/Lombok/LSP/Gradle/IDE階段已實現  
**產物：** 完整test suites、mutation驗證、requirements coverage

- [ ] **P49-01** 對Java25非preview inventory執行語法/語義/ABI/運行差分，Java21 profile錯誤也符合契約。
- [ ] **P49-02** 對所有Lombok baseline feature/option/config進行覆蓋計算，未測試項不能自動映射為支持。
- [ ] **P49-03** 建立lexer/parser/formatter/migration的property-based與mutation fuzz，固定seed並保存最小失敗用例。
- [ ] **P49-04** 進行parse-format-parse、Java→Teyru→Java語義測試，確認轉換不會損壞字面量/注釋。
- [ ] **P49-05** 對property receiver/getter/RHS/setter副作用、exceptions、labels、lambda做系統性事件序列測試。
- [ ] **P49-06** 對LSP取消/競態/position encodings/rename edits應用和IDE projection刷新做跨層回歸。
- [ ] **P49-07** 對Gradle clean/hot/cache/relocation/classpath/config/JDK失效與混合編譯做完整TestKit矩陣。
- [ ] **P49-08** 引入獨立Java consumer/annotation processor/framework fixtures，不能所有expected都由同一實現產出。
- [ ] **P49-09** 抽樣故意破壞getter、source-map、rename或cache key，驗證對應測試確實失敗再恢復。
- [ ] **P49-10** 掃描必經路徑TODO/not implemented/fake data/empty handlers、測試onlyIf/skip與未覆蓋需求，列出每項原因。
- [ ] **P49-11** 所有found bugs新增regression test再修復，不用更改golden掩蓋行為漂移。
- [ ] **P49-12** 獨立審查組而非原實作者簽署證據，準確報告pass/fail/skip和阻斷缺陷。

**階段出口：** 測試能發現真實錯誤，coverage與產品承諾逐項相連，不是高數字但無效斷言。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P50 — 效能、資源與增量正確性

**負責：** qa-review + compiler + lsp + build  
**依賴：** P49  
**產物：** benchmark corpus、基線/回歸預算、洩漏與增量報告

- [ ] **P50-01** 定義可復現的小/中/大項目corpus、硬件/JDK/heap/CPU條件與冷/熱緩存區分。
- [ ] **P50-02** 測compiler parse/typecheck/lowering/emit/javac各phase時間與峰值內存，不把javac耗時隱藏掉。
- [ ] **P50-03** 測LSP啓動/首次index/熱completion/hover/rename的分位延遲，記錄負載與取消比例。
- [ ] **P50-04** 測body-only、signature、config、JAR、toolchain變化的實際增量範圍，確保性能提升不靠漏失效。
- [ ] **P50-05** 先保存基線並由reviewer設定回歸預算，再執行優化；不能測試後任意放寬門檻。
- [ ] **P50-06** 持續大量edit/open/close/reimport循環觀察heap/thread/file handle，檢查無界增長。
- [ ] **P50-07** profile慢的符號解析/全項目重掃/重復JDK索引，優先修真實熱點而非推測優化。
- [ ] **P50-08** 評估多個agent或Gradle worker同時build的RAM佔用，默認限制不超出記錄的環境預算。
- [ ] **P50-09** 對cache損壞/schema變化/跨路徑/跨JDK驗證可恢復性與正確結果，不把cache當唯一真相。
- [ ] **P50-10** 性能測試超時/內存超額要真實fail且保留日誌，不無限提高heap或timeout當修復。
- [ ] **P50-11** 編寫性能文檔說明測量方法、已知限制與比較範圍，不製造「比Java快X倍」宣傳。
- [ ] **P50-12** reviewer在固定corpus上重跑至少兩次，確認測量可復現且semantic suites仍全過。

**階段出口：** 性能與內存可測、可回歸，增量優化沒有以錯誤結果或不完整索引換速度。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P51 — 安全、授權、SBOM 與供應鏈

**負責：** qa-review + build  
**依賴：** P49、P50  
**產物：** license audit、SBOM、security review、release blockers

- [ ] **P51-01** 為全部分發自有代碼添加正確SPDX和完整GPLv2/Classpath文本，確保適用範圍明確。[S20][S21]
- [ ] **P51-02** 列出所有第三方binary/source/assets/fixtures/licenses，保留copyright與必要NOTICE。
- [ ] **P51-03** 審查Lombok oracle/compat-annotations與runtime/helper邊界，確認正常compiler未攜帶意外processor。
- [ ] **P51-04** 審查生成Java是否複製受保護工具代碼，tiny runtime與嚴格metadata profile的依賴/許可證有清楚說明。
- [ ] **P51-05** 檢查IDEA/Gradle/LSP libraries與當前bundling的許可證相容性，法律不明確項作為外部發佈blocker。
- [ ] **P51-06** 生成可機器讀取SBOM與依賴鎖定/校驗資料，掃描已知漏洞並記錄版本、影響、修復或合理例外。
- [ ] **P51-07** 測試workspace trust、compiler/processor execution策略、命令allowlist、path traversal/archive extraction與symlink邊界。
- [ ] **P51-08** 檢查web/doc/example/diagnostics沒有秘密、本地絕對敏感路徑、用戶源碼外傳或未經允許的遙測。
- [ ] **P51-09** 對依賴下載與server更新驗證hash/signature，網絡失敗不悄悄回退到未知binary。
- [ ] **P51-10** 建立SECURITY.md、vulnerability reporting與release簽名密鑰使用流程，不生成假郵箱/組織身份。
- [ ] **P51-11** 明確Java商標/Oracle背書與代碼許可證不同，不在網站使用未經授權的官方認證標記。
- [ ] **P51-12** 獨立reviewer輸出審查範圍與未解決事項；不以一句「使用開源所以沒問題」放行。

**階段出口：** 實際依賴、分發物、輸出與執行邊界均審核；未知法律/安全風險不能假裝已排除。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P52 — CI 矩陣與可重現乾淨建置

**負責：** build + qa-review  
**依賴：** P49～P51  
**產物：** 完整CI workflows、verifyAll/releaseCheck、跨平台證據

- [ ] **P52-01** 完成verifyQuick/verifyAll/releaseCheck聚合，每個必要suite真被依賴且失敗正確傳播。
- [ ] **P52-02** 在乾淨checkout運行完整build，不依賴開發機預生成Java/classes、IDE cache或未提交腳本。
- [ ] **P52-03** 建立Linux/macOS/Windows適用矩陣及JDK/Gradle組合，平台缺runner項保持unverified而非跳過成功。
- [ ] **P52-04** 為IDE UI/debug測試配置實際可用runner或明確本地可重現驗收，不能只用Plugin Verifier替代功能測試。
- [ ] **P52-05** website build/lint/routes/links、docs code fixtures、llms生成與schemas進入CI門檻。
- [ ] **P52-06** Lombok oracle/cache資料固定hash，CI不能偷偷下載latest導致對比基準變化。
- [ ] **P52-07** 驗證網絡冷啓動與依賴已緩存離線模式，離線不代表首次無任何依賴就能憑空build。
- [ ] **P52-08** 比較兩次清潔構建產物的決定性內容，識別timestamp/signature等需要另處理的部分。
- [ ] **P52-09** CI權限最小化，pull request不暴露發佈secrets；外部發佈workflow手動授權/受保護觸發。
- [ ] **P52-10** 保存JUnit/coverage/compatibility/SBOM/benchmark摘要產物，完整log限制大小並清除敏感信息。
- [ ] **P52-11** CI狀態與本地報告區分，不能未提交/未運行workflow就稱GitHub Actions全綠。
- [ ] **P52-12** reviewer檢查任何onlyIf/continue-on-error/ignoreFailures/排除測試規則，確保沒有繞過失敗。

**階段出口：** 完整驗證入口在真實幹淨環境運行，矩陣缺口誠實記錄，無法靠跳過測試顯示全綠。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P53 — 發行包、接入與本地 Release Candidate

**負責：** build + docs-web + integration  
**依賴：** P52、P48  
**產物：** 完整本地RC、plugin artifacts、install smoke與發佈runbook

- [ ] **P53-01** 構建compiler/CLI/server distributions、Gradle plugin/marker、IDEA ZIP、source/Javadoc JAR與網站靜態目錄。
- [ ] **P53-02** 生成checksums、SBOM、license notices、release manifest、changelog與支持版本矩陣。
- [ ] **P53-03** 驗證普通property/Data/Builder程序無不必要Teyru runtime，必要helper/profile依賴準確傳遞。
- [ ] **P53-04** 使用本地Maven/Plugin repository或composite build驗證Gradle plugins{}接入，不假裝公網namespace已擁有。
- [ ] **P53-05** 從發行包安裝CLI/server/IDEA，不使用repo內部classes冒充安裝成功。
- [ ] **P53-06** 檢驗ZIP路徑、啓動腳本權限、Windows quoting、JDK選擇、包內容與重復classes。
- [ ] **P53-07** 生成完整發佈runbook：Maven/Plugin Portal/Marketplace/website各步驟、憑證位置、驗證/回滾，不在repo放secret。[S23]
- [ ] **P53-08** 可做安全dry-run/staging本地檢查；沒有明確授權不push/tag/publish/deploy。
- [ ] **P53-09** 如果正式簽名key不可用，保留unsigned RC並標明，不能生成假簽名或稱verified release。
- [ ] **P53-10** 發佈文檔在正式地址未確認前使用local-install路徑和顯式待配項，不向用戶提供不存在下載鏈接。
- [ ] **P53-11** 獨立consumer從RC開始build/test/debug並讀AI docs，檢查版本一致與source navigation。
- [ ] **P53-12** reviewer給出LOCAL_RC_READY或具體blockers，區分本地可發佈狀態與真的PUBLISHED。

**階段出口：** 全部可發佈產物和安裝路徑可驗證；外部發佈是獨立授權操作，不得偽造。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P54 — 從零使用者驗收與反向審查

**負責：** 全新 qa-review 子代理 + integration  
**依賴：** P53及全部必要功能階段  
**產物：** 不依賴實現者口頭信息的端到端驗收報告

- [ ] **P54-01** 安排未負責實現的reviewer，只讀公開README/官網/安裝說明，從新目錄創建consumer。
- [ ] **P54-02** 安裝本地RC的Gradle/CLI/server/IDEA，按正式文檔運行，不允許實現者私下補未寫出的classpath步驟。
- [ ] **P54-03** 完成附錄F的全部用戶場景，包括property/val-var/Java互通/Lombok/無分號/生成Java/錯誤映射。
- [ ] **P54-04** 完成IDEA Java補全/導航/跨語言rename/run/debug和非IDEA LSP接入，保留準確環境與證據。
- [ ] **P54-05** 遷移真實Java+Lombok小項目，運行原tests並對照行為/API/注解，沒有無聲丟失功能。
- [ ] **P54-06** 驗證incremental/configuration cache/build cache與更改Lombok config後正確失效。
- [ ] **P54-07** 從官網/llms短索引定位功能與錯誤碼，檢查所有鏈接/Markdown/版本內容相符。
- [ ] **P54-08** 刻意觸發缺JDK/依賴/錯誤type/不可信workspace/舊metadata，系統需給可恢復提示而非crash。
- [ ] **P54-09** 審查公開文案中的「支持」「完整」「無依賴」每處是否被真實證據支撐，修正誇大項。
- [ ] **P54-10** 發現缺陷創建REOPENED工作包交給實作者修復，再獨立復驗，不能審查者無記錄自己補完後簽字。
- [ ] **P54-11** 所有必要需求必須有測試+review evidence，skipped/manual-not-run不能自動轉accepted。
- [ ] **P54-12** 形成獨立最終評分/缺陷清單與accept/reject決定，阻斷缺陷未清零不進入完成聲明。

**階段出口：** 新用戶僅憑交付文檔即可完整使用；反向審查驗證產品而不是實現者演示。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

## P55 — 完整交付核對、提交與可恢復終態

**負責：** 主協調者 + integration + qa-review  
**依賴：** P54；全部P00～P54驗收  
**產物：** 最終交付報告、完整狀態、已驗證提交／待授權發佈清單

- [ ] **P55-01** 逐項核對requirements manifest與全部checkbox，每項ACCEPTED必須指向實作、測試和獨立review證據。
- [ ] **P55-02** 復核B/C規範與最終行為一致，任何殘餘差異/NOT_IMPLEMENTED/PARTIAL不得隱藏在路線圖。
- [ ] **P55-03** 在最終tree運行verifyAll與releaseCheck，記錄真實命令、環境、exit code、pass/fail/skip，不沿用舊commit報告。
- [ ] **P55-04** 確認無必經TODO/空LSP handler/未implemented feature/假數據/刪除測試/平台隔離違規。
- [ ] **P55-05** 確認AGENTS強制subagent協作實際執行過，agent/thread registry與ownership關閉狀態完整，不偽造隔離記錄。
- [ ] **P55-06** 匯總版本/兼容/性能/安全/許可證/網站/UI/debug/AI文檔各驗收結果與可重復命令。
- [ ] **P55-07** 更新CHANGELOG、STATE、TASKS、NEXT_SESSION與release manifest，所有報告綁定最終revision/fingerprint。
- [ ] **P55-08** 保留使用者dirty work，確認無無關文件/憑證/日誌入庫；只有授權integration agent進行本地階段提交。
- [ ] **P55-09** 若Git identity缺失則保留diff並報告，不能捏造身份；沒有明確授權不push/建公開tag/部署。
- [ ] **P55-10** 輸出本地完成與外部發佈狀態：localStatus = IN_PROGRESS / COMPLETE_LOCAL / BLOCKED_LOCAL；publicationStatus = NOT_AUTHORIZED / READY_FOR_AUTHORIZATION / PUBLISHED，並分別給證據。
- [ ] **P55-11** 如果因quota/context/環境中斷，明確尚未完成需求、下一可執行包與恢復提示，不稱全部實現或承諾後台繼續。
- [ ] **P55-12** 最終答復提供真正產物位置、build/test/install/debug方法、commit與未決事項，不能只說「已按計劃完成」。

**階段出口：** 只有全範圍實現與驗收皆通過才可COMPLETE_LOCAL；公開發佈未獲授權保持單獨待辦，不偽造成功。 所有項目另須通過根 `AGENTS.md` 的共同 DoD。

---

# F. 最終使用者場景：逐項 Acceptance Checklist

以下不是前面階段測試的替代，而是 P54 必須由獨立 reviewer 從使用者角度再走一次的驗收。每項都記錄 sample、命令／操作、環境、結果與證據；需要 UI 的項目沒有執行就保持未驗證。

- [ ] **UAT-01** 從空白目錄按文件建立 Gradle 專案，以本地 RC／已授權發布的確切版本接入，不依賴 compiler 原始碼的 IDE 設定。
- [ ] **UAT-02** `src/main/teyru/Main.teyru` 編譯並執行；生成 Java 可直接閱讀，class 是正常 JVM 產物。
- [ ] **UAT-03** 原始碼的普通 `private String raw = ""` 不生成 getter/setter，不改變既有 Java field 語義。
- [ ] **UAT-04** 原生 property 的 `public get`／`public set(value)` 正確生成 Java API；trim setter 行為可由 Java consumer 驗證。
- [ ] **UAT-05** `private String name { get set }` 未顯式公開時不洩漏 public API；property visibility 與 setter 可見性有正反例。
- [ ] **UAT-06** `var`／`val` 局部推斷正確；val 禁止重新賦值但允許改變所指 mutable object；field inference 按規範拒絕。
- [ ] **UAT-07** `String x = null` 與 `String x = ""` 明確不同；`var x = null` 錯誤指向原始變數且不憑空猜型別。
- [ ] **UAT-08** 多行 lambda、method reference、泛型 stream chain 可用，overload／capture／checked exception 保留 Java 規則。
- [ ] **UAT-09** colon basic-for、enhanced-for、多 resource try、enum 成員邊界、interface method、module directive 都不含語法分號且可編譯。
- [ ] **UAT-10** 字串／字元／註解／text block 中的分號原樣保留；真正的分號 token 與 Unicode 產生的分號按規範診斷。
- [ ] **UAT-11** `factory().count++`、compound assignment、short-circuit／lambda／for-update 的 property 副作用順序符合事件序列期望。
- [ ] **UAT-12** final／computed／read-only／write-only／static／generic／interface property 的合法與非法使用都正確。
- [ ] **UAT-13** 同一 module Java 呼叫 Teyru、Teyru 呼叫 Java，互相參照的類型在 clean build 成功。
- [ ] **UAT-14** Teyru library 打成 JAR 後，另一個 Teyru consumer 透過 metadata 使用 property，Java consumer 正常使用 accessor。
- [ ] **UAT-15** Java25 非 preview inventory 與 Java21 profile 的代表性 positive／negative cases 正確，不偷偷啟用 preview。
- [ ] **UAT-16** Lombok baseline **每個 feature/option/config requirement** 都有差分證據；尤其 lazy、SneakyThrows、SuperBuilder、Delegate、ExtensionMethod、onX 等難項不可缺席。
- [ ] **UAT-17** `@Data`、`@Value`、Builder／Default／Singular／SuperBuilder 的真實 Java/Teyru consumer 與組合都可用。
- [ ] **UAT-18** `@Jacksonized`／retained annotations／logger／strict metadata profile 的外部依賴與正常 Java 工具看到的資訊正確。
- [ ] **UAT-19** 新增／刪除／修改來源、Lombok config、classpath、JDK／target 後，Gradle 輸出與 LSP 索引正確更新，不殘留舊 class。
- [ ] **UAT-20** configuration cache、build cache、up-to-date、relocation 有真實任務 outcome 證據，不只 clean build 成功。
- [ ] **UAT-21** `teyru check` 的 exit code 與 JSON diagnostics 可由程式讀取；`explain`、`emit-java`、`inspect`、`format --check` 可用。
- [ ] **UAT-22** `teyru-lsp --stdio` 在無 IDEA 安裝環境可啟動，stdout 無雜訊，取消／重啟／未保存 buffer 正確。
- [ ] **UAT-23** 非 IDEA 客戶端實際 completion／hover／definition／diagnostics／rename 可用，所有 advertised capability 有測試。
- [ ] **UAT-24** IDEA 正確安裝與啟動 server；版本、JDK、Gradle model／project trust 設定可理解、可恢復。
- [ ] **UAT-25** 在 Java 編輯器輸入尚未 build 的 Teyru class／builder，IDEA 能補全；Ctrl+B 回原始 `.teyru`。
- [ ] **UAT-26** Java ↔ Teyru references／rename 準確，重命名後 build/test 通過，無重複 symbols 或誤改同名字串。
- [ ] **UAT-27** 在原始 Teyru 設斷點，普通方法、property 與多行 lambda 能命中並單步、看 locals，跨 Java/Teyru 正常。
- [ ] **UAT-28** Java+Lombok 專案可 dry-run 遷移，diff 可讀，apply 後原有 tests 通過；不安全自動轉換有具體報告。
- [ ] **UAT-29** 官網所有必備頁面、搜尋、版本／語言切換、淺深色、手機導航、程式碼複製與 404 真正可用。
- [ ] **UAT-30** 官網程式碼由可執行 fixtures 生成；Java 展開範例、版本、安裝方式與真實產物一致，無虛構發布連結。
- [ ] **UAT-31** llms.txt、llms-full.txt、逐頁 Markdown、AI 分包、診斷／相容／release JSON 全部生成且可驗證，不洩漏私有資料。
- [ ] **UAT-32** changelog、release manifests、CLI／server／plugins／docs 的版本一致，breaking changes 有遷移說明。
- [ ] **UAT-33** runtime/helper／compile-only annotations／third-party licenses／SBOM 與實際分發物吻合，沒有「宣稱零依賴但執行缺 class」。
- [ ] **UAT-34** 不受信任 workspace 不會自動執行 build script／processor／使用者程式；惡意 URI／檔案／協定輸入有防護。
- [ ] **UAT-35** 從最終 RC 分發包開始完成安裝、build、test、debug與docs使用，不依賴本機 repo 的未發布 classes。
- [ ] **UAT-36** 全部需求與證據對應、獨立 reviewer 簽署、未驗證／待外部授權分開；未經授權沒有 push／publish／deploy。

---

# G. 給協調者的工作包、審查與恢復模板

## G1. 真正 subagent 的最小委派提示

將下列模板填上真實 ID、路徑與需求，交給目前環境真正的 subagent 工具；不是在主 session 中重複念出角色名。

```text
你是 Teyru 的 <role> subagent，工作包 <Pxx-Wyy>。
先讀根 AGENTS.md，以及實作規範的 <章節/需求ID>；不要讀取與本包無關的整個專案。
你的目標：<一個可驗收結果>。
允許修改：<owned_paths>。唯讀參考：<read_only_paths>。
依賴／基準：<accepted task IDs、commit或tree fingerprint、contract version>。
必須滿足：<正例、反例、邊界、整合與命令>。
不要覆蓋其他agent或使用者的變更，不要擅自改公共契約、全域設定或切Git分支。
共享worktree不自行commit；獨立worktree也只能按協調者授權提交。
詳細報告寫到 .agent/reports/<task>.md；原始日誌到 .agent/logs/<task>/。
回主thread只交付AGENTS規定的短摘要、真實測試exit/evidence、風險與下一依賴。
無法完成就具體標明BLOCKED/FAILED，不准用TODO/空handler冒充完成。
實作包不能自核准；交回後由另一個reviewer驗收。
```

## G2. 獨立驗收提示

```text
你是 Teyru 獨立 reviewer，不是本包原實作者。
閱讀 AGENTS.md、需求 <IDs>、工作包報告與相關diff；不要僅相信「測試通過」摘要。
重新執行指定驗收，並新增至少一個針對語義/邊界的獨立反例。
先報阻斷或重大缺陷；定位檔案/符號/需求，附重現命令與實際結果。
不得刪測試、減弱斷言或直接不留記錄地修完實作再批准。
輸出 ACCEPT / REJECT / BLOCKED 與 evidence，列出未執行測試及原因。
完整資料落檔，回主thread只有最小結論與證據路徑。
```

## G3. 任務記錄 schema 範例

這是需要建立的記錄格式範例，`null` 與 `TODO` 都是真實初始狀態，不要把示例 ID 複製成假執行證據：

```json
{
  "schemaVersion": 1,
  "id": "P18-W01",
  "requirementIds": ["PROP-EVAL-001"],
  "status": "TODO",
  "dependsOn": ["P17"],
  "agentId": null,
  "reviewerAgentId": null,
  "baseRevision": null,
  "ownedPaths": ["compiler-core/src/main/java/dev/teyru/lowering/properties/"],
  "acceptance": ["property receiver is evaluated exactly once"],
  "evidence": [],
  "blockers": []
}
```

實際程式 package/path 可依已凍結 build layout 寫入；不能因範例 path 存在就假定該實作已建立。

## G4. Codex 專案配置：只提供經版本驗證的範本

截至本文件建立時，官方 subagent 文件提供專案 `.codex/config.toml` 與自訂 agent TOML 的配置方式。執行前仍先確認使用者實際版本，不能只貼設定就聲稱功能已開啟。[S02]

在適用版本中，可將下列內容作為**專案級配置候選**，由 P00/P02 驗證後合併；不要覆寫使用者原有設定、不更改模型或 sandbox：

```toml
[agents]
enabled = true
max_concurrent_threads_per_session = 4
```

自訂 `.codex/agents/teyru-reviewer.toml` 的最小候選範本：

```toml
name = "teyru-reviewer"
description = "Review Teyru work packages against the specification and real test evidence."
developer_instructions = """
Read the applicable AGENTS.md and assigned requirement sections.
Verify behavior independently. Do not weaken tests or approve your own implementation.
Keep raw logs in the assigned report directory and return a concise evidence-based summary.
Do not change global settings, publish artifacts, or modify files outside your assigned ownership.
"""
```

不在此硬編模型名稱、權限放寬或超高並行值。若工具沒有暴露實際 spawn 能力，配置檔與提示詞不能憑空創造它。若修改設定需要重開 session，儲存狀態後準確說明，不能在舊 session 假裝已生效。

## G5. 中斷後的恢復契約

`.agent/NEXT_SESSION.md` 必須足以讓下一個協調者不重讀全部聊天就接續：

```text
Project: Teyru
Normative files: AGENTS.md + prompts/TEYRU_IMPLEMENTATION_PLAN.md
Last verified revision/tree:
Accepted phases/work packages:
In-progress work and true agent IDs:
Open ownership locks and whether writers are still active:
Exact blockers / permissions / environment limits:
Next READY work package and required inputs:
Minimal reproduce/build/test commands:
Evidence paths:
Uncommitted user changes to preserve:
Publication status: not attempted / authorized / published with evidence
```

恢復時先驗證 lock 的 owner 是否仍存在，不與仍在寫入的 agent 同時改同一檔案。所有恢復操作使用當前有效工具，不承諾之前的背景工作會自行繼續。

---

# H. 最終輸出與停止條件

## H1. 正常完成應交付

- 可使用的 repository：compiler／CLI／Gradle／LSP／IDEA／migration／spec／docs／website 與 tests。
- 本地 RC 產物、安裝方式、支援矩陣、source maps、runtime／annotation profile 依賴與 licenses。
- 真正執行的 `verifyAll`／`releaseCheck` 報告，以及 compiler／Lombok／LSP／Gradle／IDEA UI／debug／website 的證據。
- 使用者可直接複製的 build/test/run/debug／LSP 接入命令，以及 docs／llms 檔案位置。
- 所有變更與已驗收本地 commit；未提交的使用者變更照原樣保留。
- 獨立列出尚未取得授權的公共發布、網域／Maven namespace／Plugin Portal／Marketplace 帳戶設定與簽名事項。

## H2. 不能稱為完整交付的情況

僅有目錄、接口、README、官網殼、Hello World、少數 Lombok annotations、僅 IDEA syntax highlighting、只跑 compiler unit test、空 LSP handler、未測 source map/debug、未執行的「綠色」矩陣、全部測試被 skip，以上都不能宣稱本案完成。

被實際限制中斷時可交付真實部分成果，但必須用 `BLOCKED_LOCAL` 或明確的進度狀態，附未完成項與下一包。不能因專案大就主動縮成 MVP，也不能無視真實 session／工具／額度上限承諾「一條訊息保證全部跑完」。

## H3. 最後回報模板

```text
Teyru delivery status:
- 本地實作：COMPLETE_LOCAL / BLOCKED_LOCAL
- 公開發布：NOT_AUTHORIZED / READY_FOR_AUTHORIZATION / PUBLISHED

已驗收範圍與版本：
實際 build/test 命令、exit code、pass/fail/skip：
IDEA UI/debug 與非IDEA LSP 證據：
CLI/Gradle/plugin/server/website 產物位置：
相容性與授權／依賴摘要：
已完成的本地 commit / 保留的未提交變更：
未完成 requirement IDs／待外部授權：
下一個 READY 工作包與恢復檔：
```

---

# I. 官方資料索引與重新驗證要求

下列為本規範建立時核對的官方／一手資料。本文大部分內容是 **Teyru 本身的設計與驗收要求**，不是聲稱上游工具已替我們實現。版本、API、功能狀態與授權依賴在 P01 再次核對並釘選。對正式法律判斷不確定之處，保留發布阻塞與審查需求，不捏造律師結論。

| ID | 一手來源 | 在本案的用途 |
|---|---|---|
| S01 | OpenAI — [Custom instructions with AGENTS.md](https://developers.openai.com/codex/guides/agents-md/) | 指示發現、override、長度限制；不能假定巨型根檔自動完整讀入 |
| S02 | OpenAI — [Subagents](https://developers.openai.com/codex/subagents/) | 真實分工、獨立上下文、配置與平行寫入風險 |
| S03 | Oracle — [Java SE 25 Language Specification](https://docs.oracle.com/javase/specs/jls/se25/html/index.html) | Java 語言基線；Teyru 差異以本規範明列 |
| S04 | Oracle — [jdk.compiler module](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/module-summary.html) | 公開 compiler/tree/model API 與使用限制 |
| S05 | Microsoft — [LSP 3.18 specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/) | 協定 method inventory、capabilities 與 client/server 邊界 |
| S06 | JetBrains — [Language Server Protocol integration](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html) | IDEA LSP API、版本／發行物限制、原生 API 的額外整合能力 |
| S07 | Gradle — [Binary Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_binary.html) | 正式 plugin、extension、task 與惰性設定 |
| S08 | Gradle — [TestKit](https://docs.gradle.org/current/userguide/test_kit.html)；[Incremental build](https://docs.gradle.org/current/userguide/incremental_build.html) | 真實 consumer 驗證、inputs/outputs 與增量行為 |
| S09 | Project Lombok — [Features](https://projectlombok.org/features/) | 穩定功能起始清單，不等於所有公開 API inventory |
| S10 | Project Lombok — [Experimental features](https://projectlombok.org/features/experimental/) | 實驗能力與版本限制；不能當作可以省略的功能 |
| S11 | Project Lombok — [Configuration system](https://projectlombok.org/features/configuration) | 完整設定鍵匯出與階層／list 操作 |
| S12 | Project Lombok — [Delombok](https://projectlombok.org/features/delombok) | 受控 oracle／遷移比較；不是正常 compiler 後門 |
| S13 | Project Lombok — [SneakyThrows](https://projectlombok.org/features/SneakyThrows) | 原 throwable 與 checked exception 的實際行為 |
| S14 | Project Lombok — [Getter lazy](https://projectlombok.org/features/GetterLazy) | lazy storage、null cache、locking 等邊界 |
| S15 | Project Lombok — [WithBy API](https://projectlombok.org/api/lombok/experimental/WithBy) | 避免只看首頁漏掉公開 API |
| S16 | Project Lombok — [Builder](https://projectlombok.org/features/Builder) | Default、ObtainVia、Singular、集合與組合選項 |
| S17 | Project Lombok — [SuperBuilder](https://projectlombok.org/features/SuperBuilder)；[Jacksonized](https://projectlombok.org/features/experimental/Jacksonized) | 泛型繼承與框架 annotation 整合 |
| S18 | llmstxt.org — [The /llms.txt file proposal](https://llmstxt.org/) | 提案、Markdown 入口與 discoverability；不保證所有 AI 遵循 |
| S19 | Astro/Starlight — [Getting Started](https://starlight.astro.build/getting-started/) | 可實作的文件站基礎，不等於現成 Teyru 官網 |
| S20 | OpenJDK — [LICENSE](https://raw.githubusercontent.com/openjdk/jdk/master/LICENSE) | GPLv2／Classpath Exception 原文與適用範圍 |
| S21 | SPDX — [Classpath exception 2.0](https://spdx.org/licenses/Classpath-exception-2.0.html) | 例外識別與文本，非任意複製所有 GPL 程式碼的許可 |
| S22 | Project Lombok — [Log and friends](https://projectlombok.org/features/log)；[Lombok LICENSE](https://github.com/projectlombok/lombok/blob/master/LICENSE) | logging 全 family 與上游授權 |
| S23 | Gradle Plugin Portal — [Publishing plugins](https://plugins.gradle.org/docs/publish-plugin) | 正式接入／marker／發布程序；真正發布仍需授權 |

**開始執行：先 P00，真正開 subagent，完成工作包與獨立驗收，再沿依賴圖持續推進。不要只重新輸出一份路線圖。**
