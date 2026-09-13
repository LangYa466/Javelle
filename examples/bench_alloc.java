public class bench_alloc {
  static class Cell {
    private long value;
    Cell(long v) { value = v; }
    long value() { return value; }
  }
  public static void main(String[] args) {
    long sum = 0;
    for (int i = 0; i < 20000000; i++) { Cell c = new Cell(i); sum += c.value(); }
    System.out.println(sum);
  }
}
