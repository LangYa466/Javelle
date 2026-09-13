package dev.teyru.compiler.core.lowering;

import java.util.Objects;
import dev.teyru.compiler.core.symbol.TypeRef;
import dev.teyru.compiler.core.syntax.NodeId;

public record IrTemporary(
    NodeId id, TypeRef type, SourceOrigin origin, String stableName, IrNode initializer)
    implements IrNode {
  public IrTemporary {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(initializer);
    if (stableName == null || stableName.isBlank() || !type.equals(initializer.type()))
      throw new IllegalArgumentException("invalid temporary");
  }
}
