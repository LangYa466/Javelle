package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.ResourceBudget;
import org.javelle.compiler.core.lowering.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.sourcemap.*;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.NodeId;
import org.junit.jupiter.api.Test;

class FinalRepairTest {
  private static ResourceBudget budget() {
    return new ResourceBudget(1000, 100, 100, 100, 100, Long.MAX_VALUE);
  }

  @Test
  void sourceIdentityMustEqualExactBytesIncludingBomAndEncoding() {
    byte[] plain = "x".getBytes(StandardCharsets.UTF_8);
    var correct = SourceId.forContent("x.javelle", plain);
    assertEquals(
        correct.contentSha256(),
        org.javelle.compiler.core.source.SourceFile.decode(correct, plain, budget())
            .fingerprint()
            .hex());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            org.javelle.compiler.core.source.SourceFile.decode(
                new SourceId("x.javelle", "0".repeat(64)), plain, budget()));
    byte[] bom = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'x'};
    assertNotEquals(SourceId.forContent("x.javelle", plain), SourceId.forContent("x.javelle", bom));
  }

  @Test
  void sourceMapQueriesRejectEveryWrongUnit() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedRange("C.java", new TextRange(OffsetUnit.RAW_UTF16, 0, 1)));
    var source = SourceId.forContent("a.javelle", new byte[] {'x'});
    assertThrows(
        IllegalArgumentException.class,
        () -> new SourceLocation(source, new TextRange(OffsetUnit.RAW_UTF16, 0, 1)));
  }

  @Test
  void typedIrPreservesExplicitEvaluationOrderTypesAndOrigins() {
    var source = SourceId.forContent("a.javelle", new byte[] {'x'});
    var node = new NodeId(source, "Expr", new TextRange(OffsetUnit.RAW_UTF16, 0, 1), 0);
    var location = new SourceLocation(source, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 1));
    var origin = new SourceOrigin(MappingKind.DIRECT, List.of(location), "");
    TypeRef integer = new PrimitiveType("int"), bool = new PrimitiveType("boolean");
    var one = new IrValue(node, integer, origin, "1");
    var temp = new IrTemporary(node, integer, origin, "$javelle$0", one);
    var read =
        new IrRead(
            node,
            integer,
            origin,
            new SymbolId("m", "C", SymbolKind.LOCAL, "$javelle$0", "I", 0),
            null);
    var convert = new IrConvert(node, integer, origin, read, IrConvert.ConversionKind.IDENTITY);
    var write = new IrWrite(node, integer, origin, read, convert);
    var condition = new IrValue(node, bool, origin, "true");
    var body = new IrSequence(node, integer, origin, List.of(temp, read, convert, write));
    var branch =
        new IrBranch(
            node,
            integer,
            origin,
            condition,
            body,
            new IrSequence(node, integer, origin, List.of(one)));
    var sequence =
        new IrSequence(node, integer, origin, List.of(temp, read, convert, write, branch));
    assertSame(temp, sequence.operation(0));
    assertSame(write, sequence.operation(3));
    assertEquals(integer, branch.type());
    assertThrows(
        IllegalArgumentException.class, () -> new IrTemporary(node, bool, origin, "bad", one));
    assertThrows(
        IllegalArgumentException.class, () -> new IrBranch(node, integer, origin, one, body, body));
    assertThrows(NullPointerException.class, () -> new IrValue(node, integer, null, "1"));
  }
}
