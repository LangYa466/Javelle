package dev.teyru.compiler.core.lowering;

import dev.teyru.compiler.core.symbol.TypeRef;
import dev.teyru.compiler.core.syntax.NodeId;

public record IrWrite(NodeId id, TypeRef type, SourceOrigin origin, IrNode target, IrNode value)
    implements IrNode {
  public IrWrite {
    java.util.Objects.requireNonNull(id);
    java.util.Objects.requireNonNull(type);
    java.util.Objects.requireNonNull(origin);
    java.util.Objects.requireNonNull(target);
    java.util.Objects.requireNonNull(value);
    if (!type.equals(value.type())) throw new IllegalArgumentException("write type mismatch");
  }
}
