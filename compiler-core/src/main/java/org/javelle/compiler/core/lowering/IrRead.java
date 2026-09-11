package org.javelle.compiler.core.lowering;

import java.util.Objects;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.NodeId;

public record IrRead(NodeId id, TypeRef type, SourceOrigin origin, SymbolId symbol, IrNode receiver)
    implements IrNode {
  public IrRead {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(symbol);
  }
}
