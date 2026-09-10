package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record SyntaxNode(
    org.javelle.compiler.core.syntax.NodeId id,
    Optional<org.javelle.compiler.core.syntax.NodeId> parentId,
    String kind,
    String name,
    TextRange range,
    List<FrontendNode> children)
    implements FrontendNode {
  public SyntaxNode {
    if (kind.isBlank()) throw new IllegalArgumentException();
    name = Objects.requireNonNull(name);
    Objects.requireNonNull(id);
    parentId = Objects.requireNonNull(parentId);
    children = List.copyOf(children);
  }
}
