package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record UnsupportedSyntaxNode(
    dev.teyru.compiler.core.syntax.NodeId id,
    Optional<dev.teyru.compiler.core.syntax.NodeId> parentId,
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
