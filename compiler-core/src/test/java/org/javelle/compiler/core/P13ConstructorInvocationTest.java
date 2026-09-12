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
 * P13 round 13: this()/super() explicit constructor invocation placement rules (JLS 8.8.7.1), and
 * this/super now producing real ThisExpression/SuperExpression nodes instead of an empty name.
 */
class P13ConstructorInvocationTest {
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
  void thisCallAsFirstStatementIsAccepted() {
    var result = parse("class C {\n C() {\n }\n C(int x) {\n this()\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ThisExpression"));
  }

  @Test
  void superCallAsFirstStatementIsAccepted() {
    var result = parse("class C {\n C() {\n super()\n int x = 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("SuperExpression"));
  }

  @Test
  void thisCallNotFirstStatementIsRejected() {
    var result = parse("class C {\n C() {\n }\n C(int x) {\n int y = 1\n this()\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void thisCallInRegularMethodIsRejected() {
    var result = parse("class C {\n C() {\n }\n void m() {\n this()\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void thisFieldAccessIsUnaffectedByThePlacementRule() {
    var result = parse("class C {\n int x\n C(int x) {\n this.x = x\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ThisExpression"));
  }
}
