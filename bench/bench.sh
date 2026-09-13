#!/bin/sh
# Compares the native Teyru backend against a JVM baseline on the same source.
set -e
cd "$(dirname "$0")/.."
echo "== building Teyru program"
go run ./cmd/teyru build -O2 -o /tmp/bench-teyru tests/programs/bench_loop.teyru >/dev/null
echo "== timing native"
time /tmp/bench-teyru
if command -v java >/dev/null 2>&1; then
  cat > /tmp/BenchLoop.java <<'JAVA'
public class BenchLoop {
  public static void main(String[] args) {
    long total = 0;
    for (int i = 0; i < 50000000; i++) { total += i % 7; }
    System.out.println(total);
  }
}
JAVA
  javac -d /tmp /tmp/BenchLoop.java
  echo "== timing JVM (java 21+)"
  time java -cp /tmp BenchLoop
fi
echo "== binary size"
ls -l /tmp/bench-teyru
