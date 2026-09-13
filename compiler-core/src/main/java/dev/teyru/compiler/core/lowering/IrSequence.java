package dev.teyru.compiler.core.lowering;

import java.util.*;
import dev.teyru.compiler.core.symbol.TypeRef;
import dev.teyru.compiler.core.syntax.NodeId;

public record IrSequence(NodeId id, TypeRef type, SourceOrigin origin, List<IrNode> operations)
    implements IrNode {
  public IrSequence {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    operations = List.copyOf(operations);
    if (operations.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("null operation");
  }

  public IrNode operation(int evaluationIndex) {
    return operations.get(evaluationIndex);
  }
}
