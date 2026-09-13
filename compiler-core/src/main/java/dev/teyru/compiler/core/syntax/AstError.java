package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record AstError(
    NodeId id,
    TextRange range,
    String recoveryReason,
    List<TokenKind> expected,
    Optional<NodeId> parentId)
    implements AstNode {
  public AstError {
    if (recoveryReason.isBlank()) throw new IllegalArgumentException();
    expected = List.copyOf(expected);
    parentId = Objects.requireNonNull(parentId);
  }

  public List<AstNode> children() {
    return List.of();
  }
}
