package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;
import dev.teyru.compiler.core.syntax.NodeId;

public record AccessorNode(
    NodeId id,
    Optional<NodeId> parentId,
    String kind,
    String name,
    String visibility,
    TextRange range,
    List<FrontendNode> children)
    implements FrontendNode {
  public AccessorNode {
    if (!kind.equals("GetterDeclaration") && !kind.equals("SetterDeclaration"))
      throw new IllegalArgumentException("accessor kind");
    parentId = Objects.requireNonNull(parentId);
    name = Objects.requireNonNull(name);
    visibility = Objects.requireNonNull(visibility);
    children = List.copyOf(children);
  }
}
