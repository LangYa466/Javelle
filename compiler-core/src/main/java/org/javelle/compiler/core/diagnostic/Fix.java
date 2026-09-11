package org.javelle.compiler.core.diagnostic;

import java.util.*;

public record Fix(String id, String title, FixKind kind, List<TextEdit> edits) {
  public Fix {
    if (id == null || id.isBlank() || title == null || title.isBlank())
      throw new IllegalArgumentException();
    Objects.requireNonNull(kind);
    var normalized = new ArrayList<>(edits);
    normalized.sort(
        Comparator.comparing((TextEdit e) -> e.source().workspaceRelativeUri())
            .thenComparing(e -> e.source().contentSha256())
            .thenComparing(e -> e.range().unit())
            .thenComparingInt(e -> e.range().startOffset())
            .thenComparingInt(e -> e.range().endOffset())
            .thenComparing(TextEdit::replacement));
    edits = List.copyOf(normalized);
    for (int i = 1; i < edits.size(); i++) {
      var a = edits.get(i - 1);
      var b = edits.get(i);
      if (a.source().equals(b.source())
          && a.range().unit() == b.range().unit()
          && a.range().endOffset() > b.range().startOffset())
        throw new IllegalArgumentException("overlapping edits");
    }
  }

  public Fix(String id, String title, List<TextEdit> edits) {
    this(id, title, FixKind.QUICK_FIX, edits);
  }

  public void validateBounds(Map<org.javelle.compiler.core.source.SourceId, Integer> lengths) {
    for (var edit : edits) {
      Integer length = lengths.get(edit.source());
      if (length == null
          || edit.range().unit() != org.javelle.compiler.core.source.OffsetUnit.RAW_UTF16
          || edit.range().endOffset() > length)
        throw new IllegalArgumentException("edit outside fingerprint-bound source");
    }
  }
}
