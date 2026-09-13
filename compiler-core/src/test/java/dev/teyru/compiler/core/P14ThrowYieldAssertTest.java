/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 4: throw, yield, assert statements. */
class P14ThrowYieldAssertTest {
  private static ParseResult parse(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("case.teyru", bytes),
            bytes,
            new ResourceBudget(1_000_000, 10000, 10000, 100, 100, Long.MAX_VALUE));
    return new TeyruFrontend()
        .parse(
            source,
            new FrontendOptions(1, 10),
            new ResourceBudget(1_000_000, 10000, 10000, 100, 100, Long.MAX_VALUE),
            CancellationToken.none());
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
  void throwStatement() {
    var result = parse("class C {\n void m() {\n throw e\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ThrowStatement"));
  }

  @Test
  void missingThrowExpressionIsADiagnosedError() {
    var result = parse("class C {\n void m() {\n throw\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }

  @Test
  void yieldStatement() {
    var result = parse("class C {\n void m() {\n yield 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("YieldStatement"));
  }

  @Test
  void yieldAsAPlainVariableNameIsUnaffected() {
    var result = parse("class C {\n void m() {\n yield.foo()\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertFalse(anchors(result.ast()).contains("YieldStatement"));

    var assignResult = parse("class C {\n void m() {\n yield = 1\n }\n}\n");
    assertTrue(assignResult.diagnostics().isEmpty(), assignResult.diagnostics().toString());
    assertFalse(anchors(assignResult.ast()).contains("YieldStatement"));
  }

  @Test
  void assertWithoutMessage() {
    var result = parse("class C {\n void m() {\n assert x > 0\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("AssertStatement"));
  }

  @Test
  void assertWithMessage() {
    var result = parse("class C {\n void m() {\n assert x > 0 : \"must be positive\"\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var assertNode =
        anchors(result.ast()).stream().filter(a -> a.equals("AssertStatement")).findFirst();
    assertTrue(assertNode.isPresent());
  }

  @Test
  void missingAssertConditionIsADiagnosedError() {
    var result = parse("class C {\n void m() {\n assert\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }
}
