/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/**
 * P14 round 1: full Java operator precedence table (bitwise/shift ops, compound assignment,
 * instanceof, prefix/postfix ++/--).
 */
class P14PrecedenceTest {
  private static ParseResult parse(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("case.javelle", bytes),
            bytes,
            new ResourceBudget(1_000_000, 10000, 10000, 100, 100, Long.MAX_VALUE));
    return new JavelleFrontend()
        .parse(
            source,
            new FrontendOptions(1, 10),
            new ResourceBudget(1_000_000, 10000, 10000, 100, 100, Long.MAX_VALUE),
            CancellationToken.none());
  }

  private static FrontendNode find(FrontendNode root, String kind) {
    var todo = new ArrayDeque<FrontendNode>();
    todo.add(root);
    while (!todo.isEmpty()) {
      var n = todo.removeFirst();
      if (n.kind().equals(kind)) return n;
      todo.addAll(n.children());
    }
    throw new AssertionError("no " + kind + " node found");
  }

  private static List<String> anchors(FrontendNode root) {
    var out = new ArrayList<String>();
    var todo = new ArrayDeque<FrontendNode>();
    todo.add(root);
    while (!todo.isEmpty()) {
      var n = todo.removeFirst();
      out.add(n.kind() + (n.name().isBlank() ? "" : ":" + n.name()));
      todo.addAll(n.children());
    }
    return out;
  }

  @Test
  void bitwiseAndShiftOperatorsParse() {
    var result = parse("class C {\n void m() {\n int x = a & b | c ^ d << e >> f >>> g\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:&")));
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:|")));
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:^")));
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:<<")));
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:>>")));
    assertTrue(all.stream().anyMatch(a -> a.equals("BinaryExpression:>>>")));
  }

  @Test
  void bitwiseAndBindsTighterThanBitwiseOr() {
    var result = parse("class C {\n void m() {\n int x = a | b & c\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var top = find(result.ast(), "LocalVariableDeclaration").children().getFirst();
    assertEquals("BinaryExpression", top.kind());
    assertEquals("|", top.name());
    var right = top.children().get(1);
    assertEquals("BinaryExpression", right.kind());
    assertEquals("&", right.name());
  }

  @Test
  void compoundAssignmentOperatorsParse() {
    for (String op :
        List.of("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=")) {
      var result = parse("class C {\n void m() {\n x " + op + " 1\n }\n}\n");
      assertTrue(result.diagnostics().isEmpty(), op + " -> " + result.diagnostics());
      assertTrue(anchors(result.ast()).contains("AssignmentExpression:" + op), op);
    }
  }

  @Test
  void assignmentIsRightAssociative() {
    var result = parse("class C {\n void m() {\n x = y = 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var top = find(result.ast(), "AssignmentExpression");
    assertEquals("x", top.children().getFirst().name());
    var right = top.children().get(1);
    assertEquals("AssignmentExpression", right.kind());
    assertEquals("y", right.children().getFirst().name());
  }

  @Test
  void instanceOfWithoutBinding() {
    var result = parse("class C {\n void m() {\n boolean b = x instanceof String\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var instanceOf = find(result.ast(), "InstanceOfExpression");
    assertEquals("", instanceOf.name());
  }

  @Test
  void instanceOfWithPatternBinding() {
    var result = parse("class C {\n void m() {\n boolean b = x instanceof String s\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var instanceOf = find(result.ast(), "InstanceOfExpression");
    assertEquals("s", instanceOf.name());
  }

  @Test
  void prefixAndPostfixIncrementDecrement() {
    var pre = parse("class C {\n void m() {\n x = ++y\n }\n}\n");
    assertTrue(pre.diagnostics().isEmpty(), pre.diagnostics().toString());
    assertTrue(anchors(pre.ast()).contains("PrefixExpression:++"));

    var post = parse("class C {\n void m() {\n x = y++\n }\n}\n");
    assertTrue(post.diagnostics().isEmpty(), post.diagnostics().toString());
    assertTrue(anchors(post.ast()).contains("PostfixExpression:++"));

    var dec = parse("class C {\n void m() {\n x = y--\n }\n}\n");
    assertTrue(dec.diagnostics().isEmpty(), dec.diagnostics().toString());
    assertTrue(anchors(dec.ast()).contains("PostfixExpression:--"));
  }

  @Test
  void bitwiseComplementUnaryOperator() {
    var result = parse("class C {\n void m() {\n x = ~y\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("UnaryExpression:~"));
  }

  @Test
  void relationalAndInstanceofShareRelationalTierBelowShift() {
    var result =
        parse("class C {\n void m() {\n boolean b = x instanceof String && y < 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var and = find(result.ast(), "BinaryExpression");
    assertEquals("&&", and.name());
    assertEquals("InstanceOfExpression", and.children().getFirst().kind());
  }
}
