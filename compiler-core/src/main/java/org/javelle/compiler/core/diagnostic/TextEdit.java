package org.javelle.compiler.core.diagnostic;

import java.util.Objects;
import org.javelle.compiler.core.source.*;

public record TextEdit(SourceId source, TextRange range, String replacement) {
  public TextEdit {
    Objects.requireNonNull(source);
    Objects.requireNonNull(range);
    Objects.requireNonNull(replacement);
  }
}
