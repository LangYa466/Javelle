package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public sealed interface FrontendNode permits SyntaxNode, UnsupportedSyntaxNode, AccessorNode {
  dev.teyru.compiler.core.syntax.NodeId id();

  Optional<dev.teyru.compiler.core.syntax.NodeId> parentId();

  String kind();

  String name();

  TextRange range();

  List<FrontendNode> children();
}
