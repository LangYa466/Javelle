/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 3: constructor declarations. */
class P13ConstructorDeclarationTest {
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
  void basicNoArgConstructor() {
    var result = parse("class Point {\n Point() {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ConstructorDeclaration:Point"));
  }

  @Test
  void constructorWithParameters() {
    var result = parse("class Point {\n int x\n Point(int x, int y) {\n this.x = x\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ConstructorDeclaration:Point"));
    assertTrue(anchors(result.ast()).contains("ParameterDeclaration:x"));
    assertTrue(anchors(result.ast()).contains("ParameterDeclaration:y"));
  }

  @Test
  void constructorWithVarargsStillDiagnosesUnsupportedVarargs() {
    var result = parse("class Point {\n Point(int... xs) {\n }\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-DEV-0001")));
  }

  @Test
  void missingConstructorBodyIsADiagnosedError() {
    var result = parse("class Point {\n Point()\n}\n");
    assertFalse(result.diagnostics().isEmpty());
    assertEquals("JV-SYN-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void sameNameMethodWithReturnTypeIsNotMistakenForAConstructor() {
    var result = parse("class Point {\n Point create() {\n return this\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:create"));
  }
}
