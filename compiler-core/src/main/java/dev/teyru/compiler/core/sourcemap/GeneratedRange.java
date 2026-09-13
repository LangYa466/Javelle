package dev.teyru.compiler.core.sourcemap;

import dev.teyru.compiler.core.source.*;

public record GeneratedRange(String generatedRelativeUri, TextRange range) {
  public GeneratedRange {
    new SourceId(generatedRelativeUri, "0".repeat(64));
    if (range.unit() != OffsetUnit.UNICODE_CODE_POINT)
      throw new IllegalArgumentException("generated source-map ranges require UNICODE_CODE_POINT");
  }
}
