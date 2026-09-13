package dev.teyru.compiler.core.symbol;

public record NullType() implements TypeRef {
  public String displayName() {
    return "<null>";
  }
}
