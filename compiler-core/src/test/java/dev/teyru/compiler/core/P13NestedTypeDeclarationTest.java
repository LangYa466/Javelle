/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 8: nested (member) class/interface/enum/record/@interface declarations. */
class P13NestedTypeDeclarationTest {
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
  void nestedClass() {
    var result = parse("class Outer {\n static class Inner {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ClassDeclaration:Outer"));
    assertTrue(anchors(result.ast()).contains("ClassDeclaration:Inner"));
  }

  @Test
  void nestedInterface() {
    var result = parse("class Outer {\n interface Callback {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("InterfaceDeclaration:Callback"));
  }

  @Test
  void nestedEnum() {
    var result = parse("class Outer {\n enum Mode {\n A,\n B\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnumDeclaration:Mode"));
  }

  @Test
  void nestedRecord() {
    var result = parse("class Outer {\n record Point(int x, int y) {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("RecordDeclaration:Point"));
  }

  @Test
  void nestedAnnotationType() {
    var result = parse("class Outer {\n @interface Config {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("AnnotationTypeDeclaration:Config"));
  }

  @Test
  void fieldNamedRecordIsStillAPlainField() {
    var result = parse("class Outer {\n Object record = null\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("FieldDeclaration:record"));
  }
}
