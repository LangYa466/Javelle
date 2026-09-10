package org.javelle.compiler.core.sourcemap;

import org.javelle.compiler.core.source.*;

public record GeneratedRange(String generatedRelativeUri, TextRange range) {
  public GeneratedRange {
    new SourceId(generatedRelativeUri, "0".repeat(64));
    if (range.unit() != OffsetUnit.UNICODE_CODE_POINT)
      throw new IllegalArgumentException("generated source-map ranges require UNICODE_CODE_POINT");
  }
}
