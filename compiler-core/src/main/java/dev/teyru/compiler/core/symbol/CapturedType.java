package dev.teyru.compiler.core.symbol;

import java.util.*;

public record CapturedType(
    String captureId, TypeRef wildcard, List<TypeRef> upperBounds, Optional<TypeRef> lowerBound)
    implements TypeRef {
  public CapturedType {
    if (captureId == null || captureId.isBlank() || !(wildcard instanceof WildcardType))
      throw new IllegalArgumentException("capture");
    upperBounds = List.copyOf(upperBounds);
    lowerBound = Objects.requireNonNull(lowerBound);
  }

  public String displayName() {
    return "capture#" + captureId + " of " + wildcard.displayName();
  }
}
