package org.javelle.compiler.core.source;

public interface LineMap {
  LinePosition positionOf(int rawUtf16Offset);

  int offsetOf(LinePosition position);

  TextRange lineRange(int zeroBasedLine);
}
