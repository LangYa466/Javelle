package org.javelle.compiler.core.lowering;

import java.util.Objects;
import org.javelle.compiler.core.symbol.TypeRef;
import org.javelle.compiler.core.syntax.NodeId;

public record IrConvert(
    NodeId id, TypeRef type, SourceOrigin origin, IrNode input, ConversionKind conversion)
    implements IrNode {
  public enum ConversionKind {
    IDENTITY,
    PRIMITIVE_WIDENING,
    PRIMITIVE_NARROWING,
    BOXING,
    UNBOXING,
    REFERENCE_CAST
  }

  public IrConvert {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(input);
    Objects.requireNonNull(conversion);
  }
}
