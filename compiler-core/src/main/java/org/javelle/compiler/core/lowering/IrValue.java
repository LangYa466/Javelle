package org.javelle.compiler.core.lowering;

import org.javelle.compiler.core.symbol.TypeRef;
import org.javelle.compiler.core.syntax.NodeId;

public record IrValue(NodeId id, TypeRef type, SourceOrigin origin, String value)
    implements IrNode {
  public IrValue {
    java.util.Objects.requireNonNull(id);
    java.util.Objects.requireNonNull(type);
    java.util.Objects.requireNonNull(origin);
    java.util.Objects.requireNonNull(value);
  }
}
