package org.javelle.compiler.core.symbol;

import java.util.*;

public record WildcardType(Optional<TypeRef> upper, Optional<TypeRef> lower) implements TypeRef {
  public WildcardType {
    upper = Objects.requireNonNull(upper);
    lower = Objects.requireNonNull(lower);
    if (upper.isPresent() && lower.isPresent()) throw new IllegalArgumentException();
  }

  public String displayName() {
    return lower
        .map(x -> "? super " + x.displayName())
        .orElseGet(() -> upper.map(x -> "? extends " + x.displayName()).orElse("?"));
  }
}
