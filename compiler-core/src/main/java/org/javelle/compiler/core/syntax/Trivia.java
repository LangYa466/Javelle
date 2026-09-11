package org.javelle.compiler.core.syntax;

import java.util.Objects;
import org.javelle.compiler.core.source.*;

public record Trivia(
    TriviaKind kind, TextRange rawRange, TextRange translatedRange, String rawText) {
  public Trivia {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(rawRange);
    Objects.requireNonNull(translatedRange);
    Objects.requireNonNull(rawText);
  }
}
