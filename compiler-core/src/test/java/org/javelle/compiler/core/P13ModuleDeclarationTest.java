/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 14: module-info compilation units ({@code module}/{@code open module}). */
class P13ModuleDeclarationTest {
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
  void moduleWithRequiresAndExports() {
    var result =
        parse(
            "module com.example.app {\n"
                + " requires java.base\n"
                + " requires transitive com.example.api\n"
                + " exports com.example.app.api\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ModuleDeclaration:com.example.app"));
    assertTrue(all.contains("RequiresDirective:java.base"));
    assertTrue(all.contains("RequiresDirective:com.example.api"));
    assertTrue(all.contains("TransitiveModifier:transitive"));
    assertTrue(all.contains("ExportsDirective:com.example.app.api"));
  }

  @Test
  void exportsToSpecificModules() {
    var result =
        parse(
            "module com.example.app {\n"
                + " exports com.example.app.internal to com.example.tests, com.example.tools\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ExportsTarget:com.example.tests"));
    assertTrue(all.contains("ExportsTarget:com.example.tools"));
  }

  @Test
  void opensUsesAndProvides() {
    var result =
        parse(
            "module com.example.app {\n"
                + " opens com.example.app.model\n"
                + " uses com.example.spi.Plugin\n"
                + " provides com.example.spi.Plugin with com.example.app.MyPlugin\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("OpensDirective:com.example.app.model"));
    assertTrue(all.contains("UsesDirective:com.example.spi.Plugin"));
    assertTrue(all.contains("ProvidesDirective:com.example.spi.Plugin"));
    assertTrue(all.contains("ProvidesImplementation:com.example.app.MyPlugin"));
  }

  @Test
  void openModule() {
    var result = parse("open module com.example.app {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ModuleDeclaration:com.example.app"));
    assertTrue(all.contains("OpenModifier:open"));
  }

  @Test
  void unterminatedModuleIsADiagnosedError() {
    var result = parse("module com.example.app {\n requires java.base\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void packageOnlyCompilationUnitIsValid() {
    var result = parse("package com.example.app\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("PackageDeclaration:com.example.app"));
  }
}
