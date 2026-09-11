package org.javelle.compiler.core.analysis;

import java.util.*;
import org.javelle.compiler.core.diagnostic.Diagnostic;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.syntax.*;

public record AnalysisSnapshot(
    long schemaVersion,
    InputFingerprint input,
    Map<SourceId, SourceFile> files,
    NodeIndex nodes,
    List<Diagnostic> diagnostics) {
  public AnalysisSnapshot {
    if (schemaVersion != 1) throw new IllegalArgumentException("JV-SCHEMA-UNSUPPORTED");
    files = Map.copyOf(files);
    diagnostics = List.copyOf(diagnostics);
  }
}
