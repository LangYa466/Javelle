package dev.teyru.compiler.core.diagnostic;

import java.util.*;
import dev.teyru.compiler.core.source.*;

public record Diagnostic(
    int schemaVersion,
    DiagnosticCode code,
    Severity severity,
    SourceId source,
    TextRange range,
    String message,
    List<RelatedInformation> related,
    List<Fix> fixes,
    Map<String, String> data) {
  public Diagnostic {
    if (schemaVersion != 1) throw new IllegalArgumentException("TY-SCHEMA-UNSUPPORTED");
    Objects.requireNonNull(code);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(source);
    Objects.requireNonNull(range);
    Objects.requireNonNull(message);
    related = List.copyOf(related);
    fixes = List.copyOf(fixes);
    data = Collections.unmodifiableMap(new TreeMap<>(data));
  }

  public Diagnostic(
      int schemaVersion,
      DiagnosticCode code,
      Severity severity,
      SourceId source,
      TextRange range,
      String message,
      List<Fix> fixes,
      Map<String, String> data) {
    this(schemaVersion, code, severity, source, range, message, List.of(), fixes, data);
  }

  public String toCanonicalJson() {
    return "{\"schemaVersion\":1,\"code\":\""
        + code.value()
        + "\",\"severity\":\""
        + severity
        + "\",\"source\":\""
        + source.workspaceRelativeUri()
        + "\",\"start\":"
        + range.startOffset()
        + ",\"end\":"
        + range.endOffset()
        + "}";
  }
}
