package org.javelle.compiler.core.diagnostic;

import java.util.Objects;
import org.javelle.compiler.core.source.*;

public record RelatedInformation(SourceId source, TextRange range, String message) {
  public RelatedInformation {
    Objects.requireNonNull(source);
    Objects.requireNonNull(range);
    Objects.requireNonNull(message);
  }
}
