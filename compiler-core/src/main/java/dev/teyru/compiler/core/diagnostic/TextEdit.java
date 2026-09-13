package dev.teyru.compiler.core.diagnostic;

import java.util.Objects;
import dev.teyru.compiler.core.source.*;

public record TextEdit(SourceId source, TextRange range, String replacement) {
  public TextEdit {
    Objects.requireNonNull(source);
    Objects.requireNonNull(range);
    Objects.requireNonNull(replacement);
  }
}
