package dev.teyru.compiler.core.symbol;

import java.util.*;

public record PropertyDescriptor(
    SymbolId property,
    TypeRef type,
    PropertyKind kind,
    Optional<SymbolId> storage,
    Optional<SymbolId> getter,
    Optional<SymbolId> setter,
    Set<String> modifiers,
    GeneratedMemberOrigin origin,
    int metadataVersion) {
  public enum PropertyKind {
    STORED,
    COMPUTED
  }

  public PropertyDescriptor {
    storage = Objects.requireNonNull(storage);
    getter = Objects.requireNonNull(getter);
    setter = Objects.requireNonNull(setter);
    modifiers = Set.copyOf(modifiers);
    if (metadataVersion != 1
        || kind == PropertyKind.STORED && storage.isEmpty()
        || kind == PropertyKind.COMPUTED && storage.isPresent())
      throw new IllegalArgumentException("invalid property");
  }
}
