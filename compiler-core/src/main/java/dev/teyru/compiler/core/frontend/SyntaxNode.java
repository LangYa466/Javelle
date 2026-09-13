package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public record SyntaxNode(
    dev.teyru.compiler.core.syntax.NodeId id,
    Optional<dev.teyru.compiler.core.syntax.NodeId> parentId,
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
