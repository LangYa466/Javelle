/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/**
 * P13 round 11: real package/import declarations (single-type, on-demand, static single, static
 * on-demand) instead of raw scanned lines.
 */
class P13PackageImportTest {
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
  void packageDeclaration() {
    var result = parse("package org.javelle.demo\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("PackageDeclaration:org.javelle.demo"));
  }

  @Test
  void singleTypeImport() {
    var result = parse("import java.util.List\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ImportDeclaration:java.util.List"));
  }

  @Test
  void onDemandImport() {
    var result = parse("import java.util.*\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ImportDeclaration:java.util.*"));
  }

  @Test
  void staticSingleImport() {
    var result = parse("import static java.util.Map.Entry\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ImportDeclaration:java.util.Map.Entry"));
    assertTrue(anchors(result.ast()).contains("StaticModifier:static"));
  }

  @Test
  void staticOnDemandImport() {
    var result = parse("import static java.lang.Math.*\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ImportDeclaration:java.lang.Math.*"));
    assertTrue(anchors(result.ast()).contains("StaticModifier:static"));
  }

  @Test
  void malformedImportIsADiagnosedError() {
    var result = parse("import .invalid\nclass C {\n}\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void multipleImportsAndPackageTogether() {
    var result =
        parse(
            "package org.javelle.demo\nimport java.util.List\nimport java.util.*\nclass C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("PackageDeclaration:org.javelle.demo"));
    assertTrue(all.contains("ImportDeclaration:java.util.List"));
    assertTrue(all.contains("ImportDeclaration:java.util.*"));
  }
}
