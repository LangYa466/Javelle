package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record CstToken(NodeId id, TextRange range, Token token, Optional<NodeId> parentId)
    implements CstNode {
  public List<CstNode> children() {
    return List.of();
  }

  public CstToken {
    parentId = Objects.requireNonNull(parentId);
  }
}
