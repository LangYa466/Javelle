package dev.teyru.compiler.core.lowering;

import java.util.*;
import dev.teyru.compiler.core.semantic.BoundCompilationUnit.*;
import dev.teyru.compiler.core.sourcemap.*;

public final class EarlyLowerer {
  public IrSequence lower(
      List<BoundStatement> statements,
      dev.teyru.compiler.core.syntax.NodeId owner,
      dev.teyru.compiler.core.symbol.TypeRef resultType) {
    var operations = new ArrayList<IrNode>();
    for (var statement : statements)
      if (statement instanceof BoundExpressionStatement e)
        operations.add(expression(e.expression()));
      else if (statement instanceof BoundLocal l) {
        IrNode value = expression(l.initializer());
        operations.add(new IrTemporary(value.id(), value.type(), value.origin(), l.name(), value));
      } else if (statement instanceof BoundIf i) {
        IrNode condition = expression(i.condition());
        operations.add(
            new IrBranch(
                condition.id(),
                resultType,
                condition.origin(),
                condition,
                lower(i.whenTrue(), owner, resultType),
                lower(i.whenFalse(), owner, resultType)));
      }
    return new IrSequence(owner, resultType, origin(owner), operations);
  }

  private IrNode expression(BoundExpression e) {
    if (e instanceof Literal x)
      return new IrValue(x.node(), x.type(), origin(x.node()), x.javaText());
    if (e instanceof Name x)
      return new IrRead(x.node(), x.type(), origin(x.node()), x.symbol().orElseThrow(), null);
    if (e instanceof Assignment x)
      return new IrWrite(
          x.node(), x.type(), origin(x.node()), expression(x.target()), expression(x.value()));
    if (e instanceof Cast x)
      return new IrConvert(
          x.node(),
          x.type(),
          origin(x.node()),
          expression(x.value()),
          IrConvert.ConversionKind.REFERENCE_CAST);
    throw new IllegalArgumentException(
        "TY-DEV-0001 unsupported early lowering expression: " + e.getClass().getSimpleName());
  }

  private SourceOrigin origin(dev.teyru.compiler.core.syntax.NodeId n) {
    return new SourceOrigin(
        MappingKind.DIRECT,
        List.of(
            new SourceLocation(
                n.source(),
                new dev.teyru.compiler.core.source.TextRange(
                    dev.teyru.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT,
                    n.rawRange().startOffset(),
                    n.rawRange().endOffset()))),
        "");
  }
}
