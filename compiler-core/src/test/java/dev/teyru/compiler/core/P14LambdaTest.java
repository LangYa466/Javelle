/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P14 round 2: expression-bodied lambdas (single-line only; block bodies remain unsupported). */
class P14LambdaTest {
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
  void implicitSingleParamLambda() {
    var result = parse("class C {\n void m() {\n x = a -> a + 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("LambdaExpression"));
    assertTrue(all.contains("LambdaParameterDeclaration:a"));
  }

  @Test
  void zeroParamLambda() {
    var result = parse("class C {\n void m() {\n x = () -> 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("LambdaExpression"));
  }

  @Test
  void multipleUntypedParamsLambda() {
    var result = parse("class C {\n void m() {\n x = (a, b) -> a + b\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("LambdaParameterDeclaration:a"));
    assertTrue(all.contains("LambdaParameterDeclaration:b"));
  }

  @Test
  void typedParamsLambda() {
    var result = parse("class C {\n void m() {\n x = (int a, int b) -> a + b\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("LambdaParameterDeclaration:a"));
    assertTrue(all.contains("LambdaParameterDeclaration:b"));
  }

  @Test
  void lambdaAsCallArgument() {
    var result = parse("class C {\n void m() {\n list.forEach(x -> x + 1)\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("LambdaExpression"));
  }

  @Test
  void blockBodiedLambdaIsUnsupportedButRecovered() {
    var result = parse("class C {\n void m() {\n x = a -> { return a }\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-DEV-0001")));
  }
}
