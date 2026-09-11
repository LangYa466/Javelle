/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.junit.jupiter.api.Test;

/** P13 round 10: modifier-combination validation (JLS 8.1.1/8.9.1/8.10). */
class P13ModifierValidationTest {
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

  private static boolean hasModError(ParseResult result) {
    return result.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-MOD-0001"));
  }

  @Test
  void finalAndAbstractClassIsRejected() {
    assertTrue(hasModError(parse("final abstract class C {\n}\n")));
  }

  @Test
  void finalAndSealedClassIsRejected() {
    assertTrue(hasModError(parse("final sealed class C permits D {\n}\n")));
  }

  @Test
  void sealedAndNonSealedClassIsRejected() {
    assertTrue(hasModError(parse("sealed non-sealed class C permits D {\n}\n")));
  }

  @Test
  void plainFinalClassIsAccepted() {
    assertFalse(hasModError(parse("final class C {\n}\n")));
  }

  @Test
  void finalInterfaceIsRejected() {
    assertTrue(hasModError(parse("final interface I {\n}\n")));
  }

  @Test
  void abstractEnumIsRejected() {
    assertTrue(hasModError(parse("abstract enum E {\n A\n}\n")));
  }

  @Test
  void finalEnumIsRejected() {
    assertTrue(hasModError(parse("final enum E {\n A\n}\n")));
  }

  @Test
  void abstractRecordIsRejected() {
    assertTrue(hasModError(parse("abstract record R(int x) {\n}\n")));
  }

  @Test
  void finalRecordIsRejected() {
    assertTrue(hasModError(parse("final record R(int x) {\n}\n")));
  }

  @Test
  void finalAnnotationTypeIsRejected() {
    assertTrue(hasModError(parse("final @interface A {\n}\n")));
  }

  @Test
  void plainRecordAndEnumAreAccepted() {
    assertFalse(hasModError(parse("record R(int x) {\n}\n")));
    assertFalse(hasModError(parse("enum E {\n A\n}\n")));
  }
}
