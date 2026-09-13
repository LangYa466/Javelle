/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 4: record declarations (components, canonical constructor, extra methods). */
class P13RecordDeclarationTest {
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
  void basicRecordWithComponentsAndEmptyBody() {
    var result = parse("record Point(int x, int y) {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("RecordDeclaration:Point"));
    assertTrue(anchors(result.ast()).contains("RecordComponentDeclaration:x"));
    assertTrue(anchors(result.ast()).contains("RecordComponentDeclaration:y"));
  }

  @Test
  void recordWithNoComponents() {
    var result = parse("record Empty() {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("RecordDeclaration:Empty"));
  }

  @Test
  void recordWithCanonicalConstructorAndExtraMethod() {
    var result =
        parse(
            "record Point(int x, int y) {\n"
                + " Point(int x, int y) {\n"
                + " this.x = x\n"
                + " }\n"
                + " int sum() {\n"
                + " return x + y\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ConstructorDeclaration:Point"));
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:sum"));
  }

  @Test
  void missingRecordHeaderIsADiagnosedError() {
    var result = parse("record Point {\n}\n");
    assertFalse(result.diagnostics().isEmpty());
    assertEquals("TY-SYN-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void missingRecordBodyIsADiagnosedError() {
    var result = parse("record Point(int x, int y)\n");
    assertFalse(result.diagnostics().isEmpty());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }

  @Test
  void unterminatedRecordIsADiagnosedError() {
    var result = parse("record Point(int x, int y) {\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }
}
