package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.diagnostic.Diagnostic;
import org.javelle.compiler.core.syntax.Token;

public record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {
  public LexResult {
    tokens = List.copyOf(tokens);
    diagnostics = List.copyOf(diagnostics);
  }
}
