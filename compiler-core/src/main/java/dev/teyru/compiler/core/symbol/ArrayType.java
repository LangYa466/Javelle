package dev.teyru.compiler.core.symbol;

public record ArrayType(TypeRef component) implements TypeRef {
  public String displayName() {
    return component.displayName() + "[]";
  }
}
