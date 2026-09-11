/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 1: interface declarations (abstract methods, default/static methods, constants). */
class P13InterfaceDeclarationTest {
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
  void abstractMethodHasNoBodyAndTerminatesAtNewline() {
    var result = parse("interface Greeter {\n String greet(String name)\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("InterfaceDeclaration:Greeter"));
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:greet"));
  }

  @Test
  void defaultAndStaticMethodsRequireARealBody() {
    var result =
        parse(
            "interface Greeter {\n"
                + " default String greet() {\n"
                + " return \"hi\"\n"
                + " }\n"
                + " static Greeter create() {\n"
                + " return null\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:greet"));
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:create"));
  }

  @Test
  void missingBodyOnADefaultMethodIsADiagnosedError() {
    var result = parse("interface Greeter {\n default String greet()\n}\n");
    assertFalse(result.diagnostics().isEmpty());
    assertEquals("JV-SYN-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void constantFieldRequiresAnInitializer() {
    var ok = parse("interface Constants {\n int MAX = 10\n}\n");
    assertTrue(ok.diagnostics().isEmpty(), ok.diagnostics().toString());
    assertTrue(anchors(ok.ast()).contains("FieldDeclaration:MAX"));

    var missing = parse("interface Constants {\n int MAX\n}\n");
    assertFalse(missing.diagnostics().isEmpty());
  }

  @Test
  void unterminatedInterfaceIsADiagnosedError() {
    var result = parse("interface Greeter {\n String greet()\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }
}
