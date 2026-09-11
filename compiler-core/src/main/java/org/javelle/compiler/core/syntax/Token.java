package org.javelle.compiler.core.syntax;

import java.util.*;
import org.javelle.compiler.core.source.TextRange;

public record Token(
    TokenKind kind,
    TextRange rawRange,
    TextRange translatedRange,
    String rawText,
    String value,
    List<Trivia> leadingTrivia,
    List<Trivia> trailingTrivia,
    boolean missing) {
  public Token {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(rawRange);
    Objects.requireNonNull(translatedRange);
    Objects.requireNonNull(rawText);
    Objects.requireNonNull(value);
    leadingTrivia = List.copyOf(leadingTrivia);
    trailingTrivia = List.copyOf(trailingTrivia);
    if (missing && (rawRange.length() != 0 || translatedRange.length() != 0))
      throw new IllegalArgumentException("missing token range");
  }
}
