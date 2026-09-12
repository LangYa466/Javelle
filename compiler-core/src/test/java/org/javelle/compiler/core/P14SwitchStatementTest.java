/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 7: switch statement, arrow and colon case forms. Switch expressions are separate. */
class P14SwitchStatementTest {
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
  void arrowCasesWithExpressionBodies() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " switch (x) {\n"
                + " case 1 -> log(\"one\")\n"
                + " case 2, 3 -> log(\"two or three\")\n"
                + " default -> log(\"other\")\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("SwitchStatement"));
    assertTrue(all.contains("SwitchCase:arrow"));
    assertTrue(all.contains("SwitchDefaultCase:arrow"));
  }

  @Test
  void arrowCaseWithBlockBody() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " switch (x) {\n"
                + " case 1 -> {\n"
                + " log(\"one\")\n"
                + " }\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("Block"));
  }

  @Test
  void colonCasesWithFallthrough() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " switch (x) {\n"
                + " case 1:\n"
                + " log(\"one\")\n"
                + " break\n"
                + " case 2:\n"
                + " default:\n"
                + " log(\"other\")\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("SwitchCase:colon"));
    assertTrue(all.contains("SwitchDefaultCase:colon"));
    assertTrue(all.contains("BreakStatement"));
  }

  @Test
  void missingSwitchSubjectIsADiagnosedError() {
    var result = parse("class C {\n void m() {\n switch () {\n }\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void unterminatedSwitchIsADiagnosedError() {
    var result = parse("class C {\n void m() {\n switch (x) {\n case 1 -> log(1)\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }
}
