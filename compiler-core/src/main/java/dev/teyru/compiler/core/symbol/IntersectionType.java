package dev.teyru.compiler.core.symbol;

import java.util.*;

public record IntersectionType(List<TypeRef> bounds) implements TypeRef {
  public IntersectionType {
    bounds = List.copyOf(bounds);
    if (bounds.size() < 2) throw new IllegalArgumentException();
  }

  public String displayName() {
    return bounds.stream()
        .map(TypeRef::displayName)
        .collect(java.util.stream.Collectors.joining(" & "));
  }
}
