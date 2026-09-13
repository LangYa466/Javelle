package dev.teyru.compiler.core.symbol;

import java.util.*;

public record AnonymousType(String stableId, TypeRef superType, List<TypeRef> interfaces)
    implements TypeRef {
  public AnonymousType {
    if (stableId == null || stableId.isBlank()) throw new IllegalArgumentException("stable ID");
    Objects.requireNonNull(superType);
    interfaces = List.copyOf(interfaces);
  }

  public String displayName() {
    return "<anonymous:" + stableId + ">";
  }
}
