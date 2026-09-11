package org.javelle.compiler.core.lowering;

import org.javelle.compiler.core.symbol.TypeRef;
import org.javelle.compiler.core.syntax.NodeId;

public sealed interface IrNode
    permits IrValue, IrTemporary, IrRead, IrConvert, IrWrite, IrBranch, IrSequence {
  NodeId id();

  TypeRef type();

  SourceOrigin origin();
}
