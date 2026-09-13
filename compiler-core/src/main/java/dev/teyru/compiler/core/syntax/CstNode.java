package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public sealed interface CstNode permits CstBranch, CstToken, CstError {
  NodeId id();

  TextRange range();

  List<CstNode> children();

  Optional<NodeId> parentId();
}
