package dev.teyru.compiler.core.source;

public record LinePosition(int line, int utf16Character) {
  public LinePosition {
    if (line < 0 || utf16Character < 0) throw new IllegalArgumentException("negative position");
  }
}
