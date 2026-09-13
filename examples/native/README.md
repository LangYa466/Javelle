# 原生互通範例

Teyru 宣告、C 實作，兩者一起編譯成同一個執行檔。

```sh
# 1. 讓編譯器產生要實作的宣告
go run ./cmd/teyru build --native-header native.h examples/native/main.teyru -o demo

# 2. 實作（見 impl.c），然後一起編譯
go run ./cmd/teyru run --native examples/native/impl.c examples/native/main.teyru
```

輸出：

```
5
hello, world
15
```

`impl.c` 裡的每個符號都是 `--native-header` 產生的名字。完整說明見
[docs/native.md](../../docs/native.md)。
