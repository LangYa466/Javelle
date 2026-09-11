package org.javelle.compiler.core.lowering;

import java.util.Objects;
import org.javelle.compiler.core.symbol.TypeRef;
import org.javelle.compiler.core.syntax.NodeId;

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
