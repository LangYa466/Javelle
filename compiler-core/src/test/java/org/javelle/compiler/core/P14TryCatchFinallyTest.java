/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 9: try/catch/finally, multi-catch, and newline-separated resource headers. */
class P14TryCatchFinallyTest {
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
  void tryCatch() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " try {\n"
                + " risky()\n"
                + " } catch (Exception e) {\n"
                + " handle(e)\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("TryStatement"));
    assertTrue(all.contains("CatchClause"));
    assertTrue(all.contains("CatchType:Exception"));
    assertTrue(all.contains("CatchParameterDeclaration:e"));
  }

  @Test
  void multiCatch() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " try {\n"
                + " risky()\n"
                + " } catch (IOException | SQLException e) {\n"
                + " handle(e)\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("CatchType:IOException"));
    assertTrue(all.contains("CatchType:SQLException"));
  }

  @Test
  void tryFinallyWithoutCatch() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " try {\n"
                + " risky()\n"
                + " } finally {\n"
                + " cleanup()\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("FinallyBlock"));
  }

  @Test
  void tryCatchFinallyTogether() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " try {\n"
                + " risky()\n"
                + " } catch (Exception e) {\n"
                + " handle(e)\n"
                + " } finally {\n"
                + " cleanup()\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("CatchClause"));
    assertTrue(all.contains("FinallyBlock"));
  }

  @Test
  void tryWithMultipleResources() {
    var result =
        parse(
            "class C {\n"
                + " void m() {\n"
                + " try (\n"
                + " InputStream input = openInput()\n"
                + " OutputStream output = openOutput()\n"
                + " ) {\n"
                + " input.transferTo(output)\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ResourceDeclaration:input"));
    assertTrue(all.contains("ResourceDeclaration:output"));
  }

  @Test
  void tryWithExistingResource() {
    var result =
        parse("class C {\n void m() {\n try (\n existingResource\n ) {\n use()\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ResourceDeclaration"));
  }
}
