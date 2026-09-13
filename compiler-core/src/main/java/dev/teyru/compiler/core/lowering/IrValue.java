package dev.teyru.compiler.core.lowering;

import dev.teyru.compiler.core.symbol.TypeRef;
import dev.teyru.compiler.core.syntax.NodeId;

public record IrValue(NodeId id, TypeRef type, SourceOrigin origin, String value)
    implements IrNode {
  public IrValue {
    java.util.Objects.requireNonNull(id);
    java.util.Objects.requireNonNull(type);
    java.util.Objects.requireNonNull(origin);
    java.util.Objects.requireNonNull(value);
  }
}
