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
 * P13 round 6: top-level modifiers (public/final/abstract/sealed/non-sealed) on
 * class/interface/enum/record/@interface declarations, plus the {@code permits} clause.
 */
class P13SealedModifiersTest {
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
  void publicModifierOnClassIsCaptured() {
    var result = parse("public class C {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ClassDeclaration:C"));
    assertTrue(anchors(result.ast()).contains("ModifierDeclaration:public"));
  }

  @Test
  void publicModifierWorksOnInterfaceEnumRecordAndAnnotationType() {
    assertTrue(parse("public interface I {\n}\n").diagnostics().isEmpty());
    assertTrue(parse("public enum E {\n A\n}\n").diagnostics().isEmpty());
    assertTrue(parse("public record R(int x) {\n}\n").diagnostics().isEmpty());
    assertTrue(parse("public @interface A {\n}\n").diagnostics().isEmpty());
  }

  @Test
  void sealedClassWithPermitsClause() {
    var result = parse("sealed class Shape permits Circle, Square {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    var all = anchors(result.ast());
    assertTrue(all.contains("ModifierDeclaration:sealed"));
    assertTrue(all.contains("PermittedSubtypeDeclaration:Circle"));
    assertTrue(all.contains("PermittedSubtypeDeclaration:Square"));
  }

  @Test
  void nonSealedModifierIsCapturedAsOneModifier() {
    var result = parse("non-sealed class Circle {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("ModifierDeclaration:non-sealed"));
  }

  @Test
  void sealedInterfaceWithPermitsClause() {
    var result = parse("sealed interface Shape permits Circle {\n}\n");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertTrue(anchors(result.ast()).contains("PermittedSubtypeDeclaration:Circle"));
  }

  @Test
  void malformedPermitsClauseIsADiagnosedError() {
    var result = parse("sealed class Shape permits , {\n}\n");
    assertFalse(result.diagnostics().isEmpty());
  }
}
