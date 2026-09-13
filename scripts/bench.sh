#!/bin/sh
# Compiles and times the benchmark programs, optionally against a JVM baseline.
#
#   sh scripts/bench.sh            # all benchmarks, 3 runs each, best time
#   JAVA=0 sh scripts/bench.sh     # skip the JVM comparison
#   RUNS=1 sh scripts/bench.sh     # single run
set -e
cd "$(dirname "$0")/.."
BIN=${BIN:-./teyru}
[ -x "$BIN" ] || go build -o "$BIN" ./cmd/teyru
RUNS=${RUNS:-3}
JAVA=${JAVA:-1}

# best: runs the command $RUNS times and prints the smallest wall time
best() {
  i=0
  out=""
  while [ "$i" -lt "$RUNS" ]; do
    start=$(date +%s.%N)
    "$@" >/dev/null 2>&1 || true
    end=$(date +%s.%N)
    out="$out$(awk -v a="$start" -v b="$end" 'BEGIN{printf "%.4f\n", b-a}')
"
    i=$((i+1))
  done
  printf '%s' "$out" | sort -g | head -1
}

printf '%-20s %10s %10s %10s\n' program teyru java teyru-java
for f in examples/bench_*.teyru; do
  [ -f "$f" ] || continue
  name=$(basename "$f" .teyru)
  exe=/tmp/$name.teyru.exe
  $BIN build -O2 -o "$exe" "$f" >/dev/null
  t=$(best "$exe")
  j=""
  if [ "$JAVA" = "1" ] && command -v java >/dev/null 2>&1 && [ -f "examples/$name.java" ]; then
    javac -d /tmp "examples/$name.java" 2>/dev/null || true
    [ -f "/tmp/$name.class" ] && j=$(best java -cp /tmp "$name")
  fi
  if [ -n "$j" ]; then
    ratio=$(awk -v a="$j" -v b="$t" 'BEGIN{ if (b>0) printf "%.2fx", a/b; else print "-" }')
  else
    ratio="-"
  fi
  printf '%-20s %9ss %9ss %10s\n' "$name" "$t" "${j:--}" "$ratio"
done
