package dev.teyru.compiler.core.syntax;

import java.util.*;
import dev.teyru.compiler.core.source.TextRange;

public sealed interface AstNode permits AstDeclaration, AstStatement, AstExpression, AstError {
  NodeId id();

  TextRange range();

  List<AstNode> children();

  Optional<NodeId> parentId();
}
