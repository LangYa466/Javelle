package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.diagnostic.Diagnostic;
import org.javelle.compiler.core.syntax.*;

public record ParseResult(
    FrontendNode ast,
    CstNode cst,
    NodeIndex nodes,
    List<Diagnostic> diagnostics,
    boolean recovered) {
  public ParseResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
