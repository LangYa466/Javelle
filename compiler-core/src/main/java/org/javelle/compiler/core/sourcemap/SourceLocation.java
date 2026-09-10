package org.javelle.compiler.core.sourcemap;

import org.javelle.compiler.core.source.*;

public record SourceLocation(SourceId source, TextRange range) {
  public SourceLocation {
    if (range.unit() != OffsetUnit.UNICODE_CODE_POINT)
      throw new IllegalArgumentException("source-map locations require UNICODE_CODE_POINT");
  }
}
