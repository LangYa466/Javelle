package dev.teyru.compiler.core.symbol;

public record PrimitiveType(String displayName) implements TypeRef {
  public PrimitiveType {
    if (displayName.isBlank()) throw new IllegalArgumentException();
  }
}
