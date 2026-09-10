package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record UnsupportedSyntaxNode(
    org.javelle.compiler.core.syntax.NodeId id,
    Optional<org.javelle.compiler.core.syntax.NodeId> parentId,
    String name,
    TextRange range,
    List<FrontendNode> children)
    implements FrontendNode {
  public String kind() {
    return "UnsupportedSyntaxNode";
  }

  public UnsupportedSyntaxNode {
    name = Objects.requireNonNull(name);
    Objects.requireNonNull(id);
    parentId = Objects.requireNonNull(parentId);
    children = List.copyOf(children);
  }
}
