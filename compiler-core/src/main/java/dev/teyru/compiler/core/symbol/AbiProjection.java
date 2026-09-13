package dev.teyru.compiler.core.symbol;

import java.util.*;

public record AbiProjection(
    List<SymbolId> types, List<SymbolId> members, List<PropertyDescriptor> properties) {
  public AbiProjection {
    Comparator<SymbolId> order =
        Comparator.comparing(SymbolId::ownerBinaryName)
            .thenComparingInt(SymbolId::declarationOrdinal)
            .thenComparing(x -> x.kind().name())
            .thenComparing(SymbolId::erasedDescriptor);
    types = types.stream().sorted(order).toList();
    members = members.stream().sorted(order).toList();
    properties =
        properties.stream()
            .sorted(Comparator.comparing(PropertyDescriptor::property, order))
            .toList();
  }
}
