package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public sealed interface CstNode permits CstBranch, CstToken, CstError {
  NodeId id();

  TextRange range();

  List<CstNode> children();

  Optional<NodeId> parentId();
}
