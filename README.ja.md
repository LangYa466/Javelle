# Teyru

[繁體中文](README.md) · [简体中文](README.zh-CN.md) · [English](README.en.md) · **日本語**

**Teyru は独立実装のプログラミング言語です。コンパイラは完全に Go で書かれており、ネイティブ実行ファイルを直接生成します——JVM も javac も bytecode も使いません。**

文法は Java 開発者にとって見慣れたものです（クラス、インターフェース、ジェネリクス、
ラムダ、例外、record、enum、annotation）。一方でセミコロンを廃止し、ネイティブ
プロパティを追加し、**ネイティブ機械語**として動作します。コンパイラはプログラム全体を
C に落とし、clang/LLVM（または gcc）が実行ファイルにします。ランタイムは約二千行の C で、
保守的マークアンドスイープ GC、文字列、配列、例外を自前で実装しており、仮想マシンは
一切ありません。

```
Teyru ソース (.teyru)
      │  Go 製コンパイラ：lexer → parser → 意味解析 → C 生成器
      ▼
  生成された C  ──clang（Clang フロントエンド + LLVM 中/後段）──▶  LLVM IR  ──▶  ネイティブ実行ファイル
                                                                      （JVM なし、bytecode なし）
```

バックエンドは **LLVM** です。`./teyru emit-llvm` で IR モジュールを出力できるので、
そのまま `opt` や `llc`、独自パスに渡せます。C を見たいときは `./teyru emit` です。

**ドキュメント：**[言語リファレンス](docs/language.md) · [診断コード一覧](docs/diagnostics.md) · [コンパイラ構成](docs/architecture.md) · [開発ルール](AGENTS.md)

---

## 目次

- [なぜ JVM より速いのか](#なぜ-jvm-より速いのか)
- [クイックスタート](#クイックスタート)
- [言語ツアー](#言語ツアー)
- [対応している言語機能](#対応している言語機能)
- [標準ライブラリ](#標準ライブラリ)
- [プロジェクト構成](#プロジェクト構成)
- [ランタイムモデル](#ランタイムモデル)
- [Java との違い](#java-との違い)
- [コマンドライン](#コマンドライン)
- [開発](#開発)
- [ライセンス](#ライセンス)

---

## なぜ JVM より速いのか

同じ `Hello` プログラムを同一マシンで実測（Linux x86-64、clang 22、OpenJDK 21、各 100 回）：

| 指標 | Teyru（ネイティブ） | Java（HotSpot） | 差 |
|---|---|---|---|
| 起動 100 回の合計 | **0.075 s**（1 回 0.75 ms） | 2.00 s（1 回 20 ms） | **約 27 倍速い** |
| 実行ファイル／ランタイム | **56 KB** | JDK ランタイム約 200 MB | 約 3600 倍小さい |
| ピークメモリ | **4.4 MB** | 50.9 MB | **約 11 倍少ない** |
| `fib(32)` 再帰 | **6 ms** | 27 ms | 約 4.5 倍速い |
| コンパイル時間 | 数十ミリ秒 | javac はより遅く JIT のウォームアップも必要 | — |

**速い理由：**

1. **JVM の起動コストがない。** クラスロードも JIT のウォームアップも GC スレッドの
   起動もありません。CLI ツール、短命プロセス、コンテナ起動、サーバーレスに向きます。
2. **コンパイル時に終わる処理を実行時に残さない。** ジェネリクスは消去され、呼び出しは
   アドレスに束縛され、文字列リテラルは静的オブジェクト、`static final` 定数は畳み込まれ、
   vtable とインターフェース表はコンパイラが埋めます。
3. **bytecode の解釈段階がない。** clang/LLVM がプログラム全体を一度に最適化します
   （メソッド間インライン、定数伝播、ループのベクトル化）。JIT がホットスポットを
   見つけるのを待つ必要はありません。
4. **予測可能な性能。** 脱最適化もウォームアップ曲線も GC チューニングもなく、
   最初の実行が最速です。

**正直な限界。** 短命オブジェクトを大量に作るマイクロベンチマークでは、HotSpot の
エスケープ解析がオブジェクトを消してしまい（scalar replacement）、JVM の方が速くなります
（2000 万回の確保で実測：Teyru 0.13 s 対 JVM 0.03 s）。Teyru の GC は保守的マークアンド
スイープであり、世代別コピーではありません。これは今後の最適化対象であり、
「あらゆる場面で速い」とは主張しない理由でもあります。数値は `sh bench/bench.sh`
または `tests/programs/bench_*.teyru` で再現できます。

---

## クイックスタート

**Go 1.24+** と **clang**（または gcc）が必要です。

```sh
# コンパイラをビルド
go build -o teyru ./cmd/teyru

# コンパイルして実行
./teyru run hello.teyru

# 実行ファイルを生成
./teyru build -O2 -o hello hello.teyru
./hello

# 生成された C を見る
./teyru emit hello.teyru

# LLVM に渡される IR を見る（opt/llc にそのまま渡せる）
./teyru emit-llvm hello.teyru

# バージョン
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

注意：**Teyru にセミコロンはありません。** 文は改行で終わり、`for` のヘッダは
三つの部分をコロン二つで区切ります。

---

## 言語ツアー

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
  public double radius {      // ネイティブプロパティ
    get {
      return field            // field = 実体の格納領域
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

interface Fn<R> {
  R apply(int v)
}

class Main {
  static int twice(int v) {
    return v * 2
  }

  public static void main(String[] args) {
    Shape s = new Rect(3, 4)
    System.out.println(s.describe())

    // for ( 初期化 : 条件 : 更新 )
    for (int i = 0 : i < 3 : i++) {
      System.out.println(i)
    }

    int[] xs = {1, 2, 3}
    for (int x : xs) {
      System.out.print(x)
    }
    System.out.println()

    // ラムダとメソッド参照
    Fn<Integer> f = (v) -> v + 1
    Fn<Integer> g = Main::twice
    System.out.println(f.apply(41))
    System.out.println(g.apply(21))

    // switch 式と型パターン
    Color c = Color.GREEN
    String name = switch (c) {
      case RED -> "red"
      case GREEN -> "green"
      default -> "other"
    }
    System.out.println(name)
    System.out.println(describe(c))

    // 例外
    try {
      System.out.println(10 / 0)
    } catch (ArithmeticException e) {
      System.out.println("ゼロ除算")
    } finally {
      System.out.println("cleanup")
    }
  }

  static String describe(Object o) {
    return switch (o) {
      case String s -> "string of length " + s.length()
      case Integer i when i.intValue() > 10 -> "big int"
      case Integer i -> "small int"
      default -> "other"
    }
  }
}
```

### 対応している言語機能

| 区分 | 内容 |
|---|---|
| 型 | プリミティブ、クラス、インターフェース、enum、record、annotation 型、ジェネリクス（境界・ワイルドカード・ダイヤモンド・ジェネリックメソッド）、多次元配列 |
| メンバ | フィールド、メソッド、コンストラクタ、可変長引数、静的／インスタンス初期化ブロック、ネスト／内部／ローカル／無名クラス、`sealed`／`permits` |
| 文 | `if`、`while`、`do-while`、基本 `for`（コロンヘッダ）、拡張 `for`、`switch`（文と式、アローとコロン、複数ラベル、enum、文字列、型パターン + `when` ガード）、`try`／`catch`／`finally`、try-with-resources、複数型 catch、`throw`、`yield`、`assert`、`synchronized`、ラベル付き `break`／`continue` |
| 式 | 演算子と優先順位の全体、条件演算子、キャスト、`instanceof`（パターン含む）、ラムダ、メソッド参照（静的・束縛・非束縛・コンストラクタ）、無名クラス、配列初期化子、文字列連結、自動 boxing／unboxing |
| 独自拡張 | セミコロンなしの文法、`val`（型推論される再代入不可のローカル変数）、`var`、ネイティブプロパティ（`get`／`set`／`field`）、`for` のコロンヘッダ、try-with-resources の改行区切り |

文法と意味の全体は **[docs/language.md](docs/language.md)** にあります。

---

## 標準ライブラリ

標準ライブラリは **Teyru 自身**で書かれています（`internal/prelude/prelude.go`）。
コンパイルのたびにユーザープログラムと一緒に型検査されます。

`Object`、`String`、`StringBuilder`、`Math`、`System`、`PrintStream`、
`Iterable`／`Iterator`、`Comparable`、`AutoCloseable`、`Cloneable`、`Enum`、`Record`、
八つのプリミティブラッパー（`Byte`／`Short`／`Integer`／`Long`／`Float`／`Double`／
`Character`／`Boolean`）、そして `Throwable` ファミリ（`Exception`、`RuntimeException`、
`NullPointerException`、`ArrayIndexOutOfBoundsException`、`ArithmeticException`、
`ClassCastException`、`IllegalArgumentException`、`IllegalStateException`、
`NoSuchElementException`、`NegativeArraySizeException`、`AssertionError`、
`UnsupportedOperationException`）。

`java.util` のコレクション、`printf`、ファイル I/O はありません。これらは意図的な
スコープ制限です。

---

## プロジェクト構成

| パス | 役割 |
|---|---|
| `cmd/teyru` | CLI エントリ（`build`／`run`／`emit`／`emit-llvm`／`version`） |
| `internal/source` | ファイル、位置変換、診断 |
| `internal/lexer` | 字句解析。改行はトークンにせず「直前に改行があるか」を各トークンに記録 |
| `internal/parser` | 再帰下降。改行の有意性と前置の完結性で文の終わりを決める |
| `internal/ast` | 構文木、シンボル（クラス／メソッド／フィールド／変数）、型 |
| `internal/sema` | 名前解決、型検査、ジェネリクスの消去と推論、オーバーロード解決、vtable／セレクタ配置、プロパティ降下 |
| `internal/codegen` | C 生成：クラス→struct、仮想呼び出し→vtable、インターフェース呼び出し→itable、switch 降下、GC ルート情報 |
| `internal/util` | 前後段で共有する補助：名前修飾、型記述子、C レイアウト |
| `internal/runtime/src` | C ランタイム：GC、文字列、配列、例外、boxing、Math／System／StringBuilder |
| `internal/prelude` | Teyru で書かれた標準ライブラリ |
| `tests/programs` | エンドツーエンドのテストプログラムと期待出力（`go test` が逐一比較） |
| `bench` | JVM との比較スクリプト |
| `docs` | 言語リファレンス、診断コード、アーキテクチャ |

---

## ランタイムモデル

- **オブジェクト**は C の struct で、先頭が `tyobj { tyclass* cls }`。各クラスは
  `tyclass` を持ち、親クラス、インターフェース、vtable、インターフェース表、
  GC が辿る参照フィールドのオフセットを記録します。
- **仮想呼び出し**は `obj->cls->vtable[slot]`、**インターフェース呼び出し**は
  `ty_itab(obj, selector)`。各インターフェースメソッドはグローバルに一意なセレクタを
  持ち、各クラスのインターフェース表はコンパイル時に埋められます。
- **ジェネリクス**はコンパイル時に消去され、実行時には型引数の情報がありません
  （Java と同じ）。
- **例外**は `setjmp`／`longjmp` によるハンドラチェーン。`finally` は入れ子の
  ハンドラで実装され、catch の中で再送出した場合も含め必ず実行されます。
- **GC** は保守的マークアンドスイープ。ルートはネイティブスタック（保守的に走査）、
  静的フィールドのアドレス登録表、`setjmp` で退避したレジスタです。オブジェクトは
  移動しないため、C 側の一時ポインタは常に有効です。
- **文字列**は UTF-8 の `tystr { tyobj obj; int64 len; char* data }`。リテラルは
  静的オブジェクトで、ヒープに入りません。
- **配列**は `tyarr { tyobj; len; data; esize; refs }` で、要素はオブジェクトの
  直後に埋め込まれます。

---

## Java との違い

Teyru は Java のサブセットではなく、Java 開発者にとってすぐ理解できる独立した言語です。
主な違い：

1. **セミコロンがない。** セミコロンはコンパイラに拒否されます（`TY-SYN-0001`）。
2. **`for` ヘッダはコロン**：`for (int i = 0 : i < n : i++)`。
3. **try-with-resources は改行区切り**で、セミコロンは使いません。
4. **enum の定数領域とメンバ領域はコロン一つ**で区切ります（メンバがなければ省略）。
5. **ネイティブプロパティ**：フィールドの後に accessor ブロックを書くとプロパティに
   なります。`field` は実体の格納領域を指します。accessor ブロックのないフィールドは
   普通の Java フィールドです。
6. **`val`** は型推論される再代入不可のローカル変数です（深い不変性ではありません）。
7. **checked exception の検査はありません**。`throws` は解析されますが強制されません。
8. **`System.out.printf`、実行時リフレクション、annotation processor はありません。**
9. **bytecode プラットフォームではありません**：`.class` も `java.lang` も JNI もなく、
   既存の Java ライブラリとの相互運用もできません。これは意図的な割り切りです。

全体の一覧は [docs/language.md](docs/language.md) にあります。

---

## コマンドライン

```
teyru build [flags] <files...>                 ネイティブ実行ファイルにコンパイル
teyru run   [flags] <files...> [-- args...]    コンパイルして実行
teyru emit  [flags] <files...>                 生成された C を出力
teyru emit-llvm [flags] <files...>             LLVM IR を出力
teyru version                                  バージョン
teyru help                                     使い方
```

| フラグ | 意味 |
|---|---|
| `-o <path>` | 出力パス（既定 `a.out`） |
| `-c <path>` | 生成された C を指定パスに残す |
| `--cc <name>` | 使用する C コンパイラ（既定は `clang`、`gcc`、`cc` の順に探索） |
| `-O0`…`-O3` | 最適化レベル（既定 `-O2`） |
| `--llvm-ir <path>` | LLVM IR モジュールも出力 |
| `-v` | 実行されるコンパイルコマンドを表示 |

---

## 開発

```sh
go build ./...          # ビルド
go test ./...           # エンドツーエンド（tests/programs の各プログラムをコンパイルして比較）
go vet ./...
sh bench/bench.sh       # JVM との比較（java がある場合のみ JVM 側も実行）
```

テストを追加するには `tests/programs/` に `xxx.teyru` と `xxx.expected` を置きます。
コマンドライン引数が必要なら `xxx.args`（1 行に 1 引数）も追加してください。
`go test` が残りを処理します。

コントリビュートの前に [AGENTS.md](AGENTS.md) を読んでください。

---

## ライセンス

[LICENSE](LICENSE) と [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) を参照してください。
