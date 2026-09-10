package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public sealed interface FrontendNode permits SyntaxNode, UnsupportedSyntaxNode, AccessorNode {
  org.javelle.compiler.core.syntax.NodeId id();

  Optional<org.javelle.compiler.core.syntax.NodeId> parentId();

  String kind();

  String name();

  TextRange range();

  List<FrontendNode> children();
}
