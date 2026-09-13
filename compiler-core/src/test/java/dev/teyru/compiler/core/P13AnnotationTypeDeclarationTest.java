/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 5: annotation type declarations ({@code @interface}). */
class P13AnnotationTypeDeclarationTest {
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
  void elementWithNoDefault() {
    var result = parse("@interface Config {\n String name()\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("AnnotationTypeDeclaration:Config"));
    assertTrue(anchors(result.ast()).contains("AnnotationElementDeclaration:name"));
  }

  @Test
  void elementWithDefaultValue() {
    var result = parse("@interface Config {\n int retries() default 3\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("AnnotationElementDeclaration:retries"));
    assertTrue(anchors(result.ast()).contains("NumericLiteral"));
  }

  @Test
  void constantFieldRequiresAnInitializer() {
    var ok = parse("@interface Config {\n int MAX = 10\n}\n");
    assertTrue(ok.diagnostics().isEmpty(), ok.diagnostics().toString());
    assertTrue(anchors(ok.ast()).contains("FieldDeclaration:MAX"));

    var missing = parse("@interface Config {\n int MAX\n}\n");
    assertFalse(missing.diagnostics().isEmpty());
  }

  @Test
  void elementWithParametersIsADiagnosedError() {
    var result = parse("@interface Config {\n String name(int x)\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }

  @Test
  void unterminatedAnnotationTypeIsADiagnosedError() {
    var result = parse("@interface Config {\n String name()\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }

  @Test
  void emptyAnnotationTypeIsValid() {
    var result = parse("@interface Marker {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("AnnotationTypeDeclaration:Marker"));
  }
}
