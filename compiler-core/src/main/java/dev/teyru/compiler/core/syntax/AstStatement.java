package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record AstStatement(
    NodeId id, TextRange range, List<AstNode> children, Optional<NodeId> parentId)
    implements AstNode {
  public AstStatement {
    children = List.copyOf(children);
    parentId = Objects.requireNonNull(parentId);
  }
}
