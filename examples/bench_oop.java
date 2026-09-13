public class bench_oop {
  interface Shape { long area(); }
  static class Rect implements Shape {
    private long w, h;
    Rect(long w, long h) { this.w = w; this.h = h; }
    public long area() { return w * h; }
  }
  static class Square extends Rect { Square(long side) { super(side, side); } }
  public static void main(String[] args) {
    Shape[] shapes = new Shape[1000];
    for (int i = 0; i < 1000; i++) shapes[i] = i % 2 == 0 ? new Rect(i, i+1) : new Square(i);
    long total = 0;
    for (int round = 0; round < 2000; round++)
      for (int i = 0; i < 1000; i++) total += shapes[i].area();
    System.out.println(total);
  }
}
