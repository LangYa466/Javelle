package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.diagnostic.Diagnostic;
import dev.teyru.compiler.core.syntax.*;

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
