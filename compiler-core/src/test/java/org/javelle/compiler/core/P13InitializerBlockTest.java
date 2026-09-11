/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 12: static/instance initializer blocks in a class body. */
class P13InitializerBlockTest {
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
  void instanceInitializerBlock() {
    var result = parse("class C {\n int x\n {\n x = 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("InitializerBlockDeclaration"));
  }

  @Test
  void staticInitializerBlock() {
    var result = parse("class C {\n static int x\n static {\n x = 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("InitializerBlockDeclaration"));
    assertTrue(all.contains("StaticModifier:static"));
  }

  @Test
  void emptyInitializerBlock() {
    var result = parse("class C {\n {\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("InitializerBlockDeclaration"));
  }

  @Test
  void unterminatedInitializerBlockIsADiagnosedError() {
    var result = parse("class C {\n static {\n x = 1\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
  }

  @Test
  void initializerBlockInRecordAndEnum() {
    var record = parse("record R(int x) {\n {\n }\n}\n");
    assertTrue(record.diagnostics().isEmpty(), record.diagnostics().toString());
    assertTrue(anchors(record.ast()).contains("InitializerBlockDeclaration"));

    var enumResult = parse("enum E {\n A\n :\n static {\n }\n}\n");
    assertTrue(enumResult.diagnostics().isEmpty(), enumResult.diagnostics().toString());
    assertTrue(anchors(enumResult.ast()).contains("InitializerBlockDeclaration"));
  }
}
