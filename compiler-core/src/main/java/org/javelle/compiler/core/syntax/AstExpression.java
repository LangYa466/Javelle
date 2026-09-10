package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record AstExpression(
    NodeId id, TextRange range, List<AstNode> children, Optional<NodeId> parentId)
    implements AstNode {
  public AstExpression {
    children = List.copyOf(children);
    parentId = Objects.requireNonNull(parentId);
  }
}
