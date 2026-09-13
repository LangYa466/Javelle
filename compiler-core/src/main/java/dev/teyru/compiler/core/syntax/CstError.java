package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record CstError(
    NodeId id,
    TextRange range,
    String recoveryReason,
    List<CstNode> children,
    Optional<NodeId> parentId)
    implements CstNode {
  public CstError {
    if (recoveryReason.isBlank()) throw new IllegalArgumentException("reason");
    children = List.copyOf(children);
    parentId = Objects.requireNonNull(parentId);
  }
}
