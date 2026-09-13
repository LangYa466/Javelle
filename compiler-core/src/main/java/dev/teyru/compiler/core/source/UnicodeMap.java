package dev.teyru.compiler.core.source;

public interface UnicodeMap {
  TextRange rawRangeForTranslated(TextRange translated);

  TextRange translatedRangeForRaw(TextRange raw);

  int rawBoundary(int translatedBoundary, Bias bias);

  int translatedBoundary(int rawBoundary, Bias bias);
}
