package org.javelle.compiler.core.syntax;

import java.util.Objects;
import org.javelle.compiler.core.source.*;

public record NodeId(SourceId source, String grammarKind, TextRange rawRange, int ordinal) {
  public NodeId {
    Objects.requireNonNull(source);
    Objects.requireNonNull(grammarKind);
    Objects.requireNonNull(rawRange);
    if (rawRange.unit() != OffsetUnit.RAW_UTF16 || grammarKind.isBlank() || ordinal < 0)
      throw new IllegalArgumentException("invalid node ID");
  }
}
