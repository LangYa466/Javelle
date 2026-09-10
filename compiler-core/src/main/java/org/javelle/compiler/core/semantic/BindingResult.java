package org.javelle.compiler.core.semantic;

import java.util.*;
import org.javelle.compiler.core.diagnostic.Diagnostic;

public record BindingResult(Optional<BoundCompilationUnit> unit, List<Diagnostic> diagnostics) {
  public BindingResult {
    unit = Objects.requireNonNull(unit);
    diagnostics = List.copyOf(diagnostics);
  }
}
