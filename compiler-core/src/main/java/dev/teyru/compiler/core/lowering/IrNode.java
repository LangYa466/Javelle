package dev.teyru.compiler.core.lowering;

import dev.teyru.compiler.core.symbol.TypeRef;
import dev.teyru.compiler.core.syntax.NodeId;

public sealed interface IrNode
    permits IrValue, IrTemporary, IrRead, IrConvert, IrWrite, IrBranch, IrSequence {
  NodeId id();

  TypeRef type();

  SourceOrigin origin();
}
