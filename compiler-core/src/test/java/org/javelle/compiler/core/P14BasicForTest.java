/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 6: Javelle's colon-separated basic for. */
class P14BasicForTest {
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
  void classicThreePartForLoop() {
    var result =
        parse("class C {\n void m() {\n for (int i = 0 : i < 10 : i++) {\n use(i)\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("BasicForStatement"));
    assertTrue(all.contains("ForVariableDeclaration:i"));
  }

  @Test
  void ternaryInsideConditionDoesNotConfuseColonCounting() {
    var result =
        parse(
            "class C {\n void m() {\n for (int i = 0 : flag ? i < 5 : i < 10 : i++) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("BasicForStatement"));
  }

  @Test
  void omittedInitConditionAndUpdate() {
    var result = parse("class C {\n void m() {\n for ( : : ) {\n break\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ForInit"));
    assertTrue(all.contains("ForCondition"));
    assertTrue(all.contains("ForUpdate"));
  }

  @Test
  void multipleUpdateExpressions() {
    var result =
        parse("class C {\n void m() {\n for (int i = 0 : i < 10 : i++, j--) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("BasicForStatement"));
  }

  @Test
  void multiplePlainExpressionInits() {
    var result = parse("class C {\n void m() {\n for (i = 0, j = 10 : i < j : i++) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("BasicForStatement"));
  }

  @Test
  void enhancedForStillTakesPriorityOverBasicFor() {
    var result = parse("class C {\n void m() {\n for (var x : items) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnhancedForStatement"));
    assertFalse(anchors(result.ast()).contains("BasicForStatement"));
  }
}
