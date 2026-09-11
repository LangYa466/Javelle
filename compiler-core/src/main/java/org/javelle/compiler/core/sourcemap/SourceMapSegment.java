package org.javelle.compiler.core.sourcemap;

import java.util.*;
import org.javelle.compiler.core.symbol.GeneratedMemberOrigin;
import org.javelle.compiler.core.syntax.NodeId;

public record SourceMapSegment(
    GeneratedRange generated,
    List<SourceLocation> originals,
    MappingKind kind,
    NodeId node,
    Optional<GeneratedMemberOrigin> memberOrigin,
    int priority,
    String reason) {
  public SourceMapSegment {
    originals = List.copyOf(originals);
    memberOrigin = Objects.requireNonNull(memberOrigin);
    reason = Objects.requireNonNull(reason);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(node);
    Objects.requireNonNull(generated);
    if (generated.range().unit() != org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT
        || originals.stream()
            .anyMatch(
                origin ->
                    origin.range().unit()
                        != org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT))
      throw new IllegalArgumentException("source-map ranges must use Unicode code points");
    if ((kind == MappingKind.EXPANDED || kind == MappingKind.SYNTHETIC) && memberOrigin.isEmpty())
      throw new IllegalArgumentException("generated member origin required");
    if (kind == MappingKind.SYNTHETIC && reason.isBlank())
      throw new IllegalArgumentException("synthetic reason required");
    if (kind != MappingKind.SYNTHETIC && originals.isEmpty())
      throw new IllegalArgumentException("origin required");
  }
}
