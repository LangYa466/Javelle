package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.diagnostic.Diagnostic;
import dev.teyru.compiler.core.syntax.Token;

public record LexResult(List<Token> tokens, List<Diagnostic> diagnostics) {
  public LexResult {
    tokens = List.copyOf(tokens);
    diagnostics = List.copyOf(diagnostics);
  }
}
