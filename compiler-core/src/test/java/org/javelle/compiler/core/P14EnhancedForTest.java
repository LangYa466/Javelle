/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 5: enhanced for loops. Basic (colon-separated) for is a separate, later round. */
class P14EnhancedForTest {
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
  void enhancedForWithTypedVariable() {
    var result = parse("class C {\n void m() {\n for (int x : items) {\n use(x)\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("EnhancedForStatement"));
    assertTrue(all.contains("ForVariableDeclaration:x"));
  }

  @Test
  void enhancedForWithVarVariable() {
    var result = parse("class C {\n void m() {\n for (var x : items) {\n use(x)\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ForVariableDeclaration:x"));
  }

  @Test
  void enhancedForWithSingleStatementBody() {
    var result = parse("class C {\n void m() {\n for (var x : items)\n use(x)\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnhancedForStatement"));
  }

  @Test
  void ternaryInsideEnhancedForIterableDoesNotConfuseColonCounting() {
    var result =
        parse("class C {\n void m() {\n for (var x : flag ? a : b) {\n use(x)\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnhancedForStatement"));
  }

  @Test
  void basicForStillFallsBackToUnsupported() {
    var result = parse("class C {\n void m() {\n for (int i = 0 : i < 10 : i++) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-DEV-0001")));
    assertTrue(anchors(result.ast()).contains("UnsupportedSyntaxNode:basic-for"));
  }
}
