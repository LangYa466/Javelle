package org.javelle.compiler.core.symbol;

import org.javelle.compiler.core.source.SourceId;
import org.javelle.compiler.core.syntax.NodeId;

public record GeneratedMemberOrigin(
    SourceId source, NodeId declaration, OriginKind kind, String featureId) {
  public GeneratedMemberOrigin {
    if (featureId.isBlank()) throw new IllegalArgumentException();
  }

  public enum OriginKind {
    SOURCE,
    PROPERTY_EXPANSION,
    LOMBOK_INTRINSIC,
    COMPILER_SYNTHETIC
  }
}
