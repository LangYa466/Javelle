package org.javelle.compiler.core.lowering;

import java.util.*;
import org.javelle.compiler.core.sourcemap.*;

public record SourceOrigin(MappingKind kind, List<SourceLocation> locations, String reason) {
  public SourceOrigin {
    locations = List.copyOf(locations);
    if (kind == MappingKind.SYNTHETIC && reason.isBlank()
        || kind != MappingKind.SYNTHETIC && locations.isEmpty())
      throw new IllegalArgumentException("invalid origin");
  }
}
