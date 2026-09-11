package org.javelle.compiler.core.lowering;

import java.util.Objects;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.NodeId;

public record IrBranch(
    NodeId id,
    TypeRef type,
    SourceOrigin origin,
    IrNode condition,
    IrSequence whenTrue,
    IrSequence whenFalse)
    implements IrNode {
  public IrBranch {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(condition);
    Objects.requireNonNull(whenTrue);
    Objects.requireNonNull(whenFalse);
    if (!(condition.type() instanceof PrimitiveType p) || !p.displayName().equals("boolean"))
      throw new IllegalArgumentException("branch condition must be boolean");
  }
}
