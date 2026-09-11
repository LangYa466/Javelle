package org.javelle.compiler.core.symbol;

import java.util.*;

public record AnnotationRef(String resolvedFqn, Map<String, String> values) {
  public AnnotationRef {
    if (resolvedFqn == null || resolvedFqn.isBlank() || !resolvedFqn.contains("."))
      throw new IllegalArgumentException("resolved annotation FQN required");
    values = Collections.unmodifiableMap(new TreeMap<>(values));
  }
}
