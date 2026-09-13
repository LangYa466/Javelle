package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record AstExpression(
    NodeId id, TextRange range, List<AstNode> children, Optional<NodeId> parentId)
    implements AstNode {
  public AstExpression {
    children = List.copyOf(children);
    parentId = Objects.requireNonNull(parentId);
  }
}
