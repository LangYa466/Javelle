/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 3: while, do-while, break/continue (with optional label). */
class P14LoopStatementTest {
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
  void whileLoopWithBlockBody() {
    var result = parse("class C {\n void m() {\n while (x < 10) {\n x = x + 1\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("WhileStatement"));
  }

  @Test
  void whileLoopWithSingleStatementBody() {
    var result = parse("class C {\n void m() {\n while (x < 10)\n x = x + 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("WhileStatement"));
  }

  @Test
  void doWhileLoop() {
    var result = parse("class C {\n void m() {\n do {\n x = x + 1\n } while (x < 10)\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("DoWhileStatement"));
  }

  @Test
  void breakAndContinueWithoutLabel() {
    var result = parse("class C {\n void m() {\n while (true) {\n break\n continue\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("BreakStatement"));
    assertTrue(all.contains("ContinueStatement"));
  }

  @Test
  void breakAndContinueWithLabel() {
    var result =
        parse(
            "class C {\n void m() {\n while (true) {\n break outer\n continue outer\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("BreakStatement:outer"));
    assertTrue(all.contains("ContinueStatement:outer"));
  }

  @Test
  void missingWhileConditionIsADiagnosedError() {
    var result = parse("class C {\n void m() {\n while () {\n }\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }
}
