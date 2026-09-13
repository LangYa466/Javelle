/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 7: extends/implements clauses on class/interface/enum/record. */
class P13ExtendsImplementsTest {
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
  void classWithSuperclassAndSuperinterfaces() {
    var result = parse("class Circle extends Shape implements Drawable, Comparable {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("SuperclassDeclaration:Shape"));
    assertTrue(all.contains("SuperinterfaceDeclaration:Drawable"));
    assertTrue(all.contains("SuperinterfaceDeclaration:Comparable"));
  }

  @Test
  void interfaceWithMultipleSuperinterfaces() {
    var result = parse("interface Combined extends A, B {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("SuperinterfaceDeclaration:A"));
    assertTrue(all.contains("SuperinterfaceDeclaration:B"));
  }

  @Test
  void enumImplementsInterface() {
    var result = parse("enum Status implements Describable {\n READY\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("SuperinterfaceDeclaration:Describable"));
  }

  @Test
  void recordImplementsInterface() {
    var result = parse("record Point(int x, int y) implements Comparable {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("SuperinterfaceDeclaration:Comparable"));
  }

  @Test
  void dottedQualifiedTypeName() {
    var result = parse("class Widget extends pkg.ui.Base {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("SuperclassDeclaration:pkg.ui.Base"));
  }

  @Test
  void genericTypeArgumentsAreDiagnosedAsUnsupportedButRecovered() {
    var result = parse("class Box extends Container<String> {\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-DEV-0001")));
    assertTrue(anchors(result.ast()).contains("SuperclassDeclaration:Container"));
  }

  @Test
  void sealedClassWithExtendsImplementsAndPermits() {
    var result =
        parse("sealed class Shape extends Base implements Drawable permits Circle, Square {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("SuperclassDeclaration:Base"));
    assertTrue(all.contains("SuperinterfaceDeclaration:Drawable"));
    assertTrue(all.contains("PermittedSubtypeDeclaration:Circle"));
    assertTrue(all.contains("PermittedSubtypeDeclaration:Square"));
  }
}
