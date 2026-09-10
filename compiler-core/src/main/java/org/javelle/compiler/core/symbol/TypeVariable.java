package org.javelle.compiler.core.symbol;

import java.util.*;

public record TypeVariable(String displayName, List<TypeRef> bounds) implements TypeRef {
  public TypeVariable {
    if (displayName.isBlank()) throw new IllegalArgumentException();
    bounds = List.copyOf(bounds);
  }
}
