package dev.teyru.compiler.core.diagnostic;

import java.util.Objects;
import dev.teyru.compiler.core.source.*;

public record RelatedInformation(SourceId source, TextRange range, String message) {
  public RelatedInformation {
    Objects.requireNonNull(source);
    Objects.requireNonNull(range);
    Objects.requireNonNull(message);
  }
}
