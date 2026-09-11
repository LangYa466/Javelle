package org.javelle.compiler.core.emitter;

import java.util.*;
import org.javelle.compiler.core.diagnostic.Diagnostic;
import org.javelle.compiler.core.lowering.GeneratedFile;

public record EmitResult(List<GeneratedFile> files, List<Diagnostic> diagnostics) {
  public EmitResult {
    files = List.copyOf(files);
    diagnostics = List.copyOf(diagnostics);
  }
}
