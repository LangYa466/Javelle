/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 2: enum declarations (constants, colon member-section delimiter). */
class P13EnumDeclarationTest {
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
  void constantsOnlyEnumWithNoMemberSection() {
    var result = parse("enum Status {\n READY,\n RUNNING,\n DONE\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnumDeclaration:Status"));
    assertTrue(anchors(result.ast()).contains("EnumConstantDeclaration:READY"));
    assertTrue(anchors(result.ast()).contains("EnumConstantDeclaration:RUNNING"));
    assertTrue(anchors(result.ast()).contains("EnumConstantDeclaration:DONE"));
  }

  @Test
  void trailingCommaAfterLastConstantIsAccepted() {
    var result = parse("enum Status {\n READY,\n RUNNING,\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnumConstantDeclaration:RUNNING"));
  }

  @Test
  void colonIntroducesAMemberSectionWithMethods() {
    var result =
        parse(
            "enum Status {\n"
                + " READY,\n"
                + " DONE\n"
                + " :\n"
                + " boolean isDone() {\n"
                + " return this == DONE\n"
                + " }\n"
                + "}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("MethodDeclaration:isDone"));
  }

  @Test
  void emptyEnumWithNoConstantsAndNoMembersIsValid() {
    var result = parse("enum Empty {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("EnumDeclaration:Empty"));
  }

  @Test
  void unterminatedEnumIsADiagnosedError() {
    var result = parse("enum Status {\n READY\n");
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
  }
}
