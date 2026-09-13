package dev.teyru.compiler.core.emitter;

import java.util.*;
import dev.teyru.compiler.core.diagnostic.Diagnostic;
import dev.teyru.compiler.core.lowering.GeneratedFile;

public record EmitResult(List<GeneratedFile> files, List<Diagnostic> diagnostics) {
  public EmitResult {
    files = List.copyOf(files);
    diagnostics = List.copyOf(diagnostics);
  }
}
