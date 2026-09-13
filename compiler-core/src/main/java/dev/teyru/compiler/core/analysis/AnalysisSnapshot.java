package dev.teyru.compiler.core.analysis;

import java.util.*;
import dev.teyru.compiler.core.diagnostic.Diagnostic;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.syntax.*;

public record AnalysisSnapshot(
    long schemaVersion,
    InputFingerprint input,
    Map<SourceId, SourceFile> files,
    NodeIndex nodes,
    List<Diagnostic> diagnostics) {
  public AnalysisSnapshot {
    if (schemaVersion != 1) throw new IllegalArgumentException("TY-SCHEMA-UNSUPPORTED");
    files = Map.copyOf(files);
    diagnostics = List.copyOf(diagnostics);
  }
}
