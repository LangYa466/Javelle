package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public sealed interface AstNode permits AstDeclaration, AstStatement, AstExpression, AstError {
  NodeId id();

  TextRange range();

  List<AstNode> children();

  Optional<NodeId> parentId();
}
