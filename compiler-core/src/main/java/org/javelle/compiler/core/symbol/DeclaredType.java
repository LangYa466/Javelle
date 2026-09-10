package org.javelle.compiler.core.symbol;

import java.util.*;

public record DeclaredType(String displayName, List<TypeRef> arguments) implements TypeRef {
  public DeclaredType {
    if (displayName.isBlank()) throw new IllegalArgumentException();
    arguments = List.copyOf(arguments);
  }
}
