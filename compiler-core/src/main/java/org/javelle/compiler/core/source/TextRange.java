package org.javelle.compiler.core.source;

import java.util.Objects;

public record TextRange(OffsetUnit unit, int startOffset, int endOffset) {
  public TextRange {
    Objects.requireNonNull(unit);
    if (startOffset < 0 || endOffset < startOffset)
      throw new IllegalArgumentException("invalid range");
  }

  public int length() {
    return Math.subtractExact(endOffset, startOffset);
  }

  public boolean contains(int offset) {
    return offset >= startOffset && offset < endOffset;
  }

  public boolean intersects(TextRange other) {
    requireUnit(other);
    return startOffset < other.endOffset && other.startOffset < endOffset;
  }

  public TextRange intersection(TextRange other) {
    requireUnit(other);
    int start = Math.max(startOffset, other.startOffset),
        end = Math.min(endOffset, other.endOffset);
    return start <= end ? new TextRange(unit, start, end) : null;
  }

  private void requireUnit(TextRange other) {
    Objects.requireNonNull(other);
    if (unit != other.unit) throw new IllegalArgumentException("offset unit mismatch");
  }
}
