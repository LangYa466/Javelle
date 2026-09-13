public class bench_string {
  public static void main(String[] args) {
    String base = "teyru";
    long total = 0;
    for (int i = 0; i < 200000; i++) { String s = base + "-" + i; total += s.length(); }
    System.out.println(total);
  }
}
