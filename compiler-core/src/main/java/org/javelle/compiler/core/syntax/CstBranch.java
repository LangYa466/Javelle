package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record CstBranch(
    NodeId id, TextRange range, List<CstNode> children, Optional<NodeId> parentId)
    implements CstNode {
  public CstBranch {
    children = List.copyOf(children);
    parentId = Objects.requireNonNull(parentId);
  }
}
