package org.javelle.compiler.core.symbol;

import java.util.*;

public record AnnotatedType(TypeRef underlying, List<AnnotationRef> annotations)
    implements TypeRef {
  public AnnotatedType {
    Objects.requireNonNull(underlying);
    annotations = List.copyOf(annotations);
    if (annotations.isEmpty()) throw new IllegalArgumentException("annotations");
  }

  public String displayName() {
    return annotations.stream()
            .map(a -> "@" + a.resolvedFqn())
            .collect(java.util.stream.Collectors.joining(" "))
        + " "
        + underlying.displayName();
  }
}
