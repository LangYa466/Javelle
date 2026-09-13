/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 9: local class/interface/enum/record declarations inside a method body. */
class P13LocalTypeDeclarationTest {
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
  void localClassInsideMethodBody() {
    var result = parse("class Outer {\n void run() {\n class Helper {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ClassDeclaration:Helper"));
  }

  @Test
  void localClassWithFinalModifier() {
    var result = parse("class Outer {\n void run() {\n final class Helper {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ClassDeclaration:Helper"));
  }

  @Test
  void localInterfaceAndEnum() {
    var withInterface = parse("class Outer {\n void run() {\n interface Callback {\n }\n }\n}\n");
    assertTrue(withInterface.diagnostics().isEmpty(), withInterface.diagnostics().toString());
    assertTrue(anchors(withInterface.ast()).contains("InterfaceDeclaration:Callback"));

    var withEnum = parse("class Outer {\n void run() {\n enum Mode {\n A,\n B\n }\n }\n}\n");
    assertTrue(withEnum.diagnostics().isEmpty(), withEnum.diagnostics().toString());
    assertTrue(anchors(withEnum.ast()).contains("EnumDeclaration:Mode"));
  }

  @Test
  void localRecord() {
    var result = parse("class Outer {\n void run() {\n record Pair(int a, int b) {\n }\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("RecordDeclaration:Pair"));
  }

  @Test
  void localVariableNamedRecordIsUnaffected() {
    var result = parse("class Outer {\n void run() {\n var record = 1\n }\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("LocalVariableDeclaration:record"));
  }
}
