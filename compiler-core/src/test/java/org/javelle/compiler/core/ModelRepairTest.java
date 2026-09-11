package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.ResourceBudget;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.*;
import org.junit.jupiter.api.Test;

class ModelRepairTest {
  private static ResourceBudget budget() {
    return new ResourceBudget(10000, 100, 100, 100, 100, Long.MAX_VALUE);
  }

  private static SourceFile source(String text) {
    byte[] b = text.getBytes(StandardCharsets.UTF_8);
    return SourceFile.decode(SourceId.forContent("model.javelle", b), b, budget());
  }

  @Test
  void allOffsetUnitsRoundTripAcrossEmojiChineseAndBom() {
    var s = source("\ufeff中😀x");
    for (OffsetUnit unit :
        List.of(OffsetUnit.RAW_UTF16, OffsetUnit.UNICODE_CODE_POINT, OffsetUnit.UTF8_BYTE)) {
      int end = s.convertBoundary(s.rawText().length(), OffsetUnit.RAW_UTF16, unit, Bias.END);
      for (int i = 0; i <= end; i++) {
        try {
          int raw = s.convertBoundary(i, unit, OffsetUnit.RAW_UTF16, Bias.START);
          assertEquals(i, s.convertBoundary(raw, OffsetUnit.RAW_UTF16, unit, Bias.START));
        } catch (IllegalArgumentException expected) {
        }
      }
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> s.convertBoundary(5, OffsetUnit.UTF8_BYTE, OffsetUnit.RAW_UTF16, Bias.START));
  }

  @Test
  void escapeProducedSurrogateInteriorIsRejected() {
    var s = source("\\uD83D\\uDE00");
    assertEquals("😀", s.translatedText());
    assertThrows(IllegalArgumentException.class, () -> s.unicodeMap().rawBoundary(1, Bias.START));
  }

  @Test
  void unicodeEligibilityParityRetainsIneligibleEscape() {
    assertEquals("\\\\u0061", source("\\\\u0061").translatedText());
    assertEquals("a", source("\\u0061").translatedText());
  }

  @Test
  void cstParentsStableIdsTriviaAndErrorsAreValidated() {
    var s = source(" //x\nname");
    var raw = new TextRange(OffsetUnit.RAW_UTF16, 5, 9);
    var translated = new TextRange(OffsetUnit.TRANSLATED_UTF16, 5, 9);
    var rootId = new NodeId(s.id(), "Root", new TextRange(OffsetUnit.RAW_UTF16, 0, 9), 0);
    var childId = new NodeId(s.id(), "Name", raw, 0);
    var trivia =
        new Trivia(
            TriviaKind.LINE_COMMENT,
            new TextRange(OffsetUnit.RAW_UTF16, 1, 4),
            new TextRange(OffsetUnit.TRANSLATED_UTF16, 1, 4),
            "//x");
    var token =
        new Token(
            TokenKind.IDENTIFIER,
            raw,
            translated,
            "name",
            "name",
            List.of(trivia),
            List.of(),
            false);
    var child = new CstToken(childId, raw, token, Optional.of(rootId));
    var root = new CstBranch(rootId, rootId.rawRange(), List.of(child), Optional.empty());
    assertEquals(List.of(root, child), new NodeIndex(List.of(root)).ordered());
    assertEquals(childId, new NodeId(s.id(), "Name", raw, 0));
    var error = new CstError(childId, raw, "incomplete", List.of(), Optional.empty());
    assertSame(error, new NodeIndex(List.of(error)).find(childId).orElseThrow());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new NodeIndex(
                List.of(
                    new CstBranch(
                        rootId,
                        rootId.rawRange(),
                        List.of(new CstToken(childId, raw, token, Optional.empty())),
                        Optional.empty()))));
    assertEquals("//x", token.leadingTrivia().getFirst().rawText());
  }

  @Test
  void astParentsAndErrorStateAreValidated() {
    var s = source("x");
    var r = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    var rootId = new NodeId(s.id(), "Decl", r, 0);
    var errorId = new NodeId(s.id(), "Error", r, 0);
    var error =
        new AstError(
            errorId, r, "missing expression", List.of(TokenKind.IDENTIFIER), Optional.of(rootId));
    var root = new AstDeclaration(rootId, r, List.of(error), Optional.empty());
    assertEquals(2, new AstIndex(List.of(root)).ordered().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AstIndex(
                List.of(
                    new AstDeclaration(
                        rootId,
                        r,
                        List.of(new AstError(errorId, r, "x", List.of(), Optional.empty())),
                        Optional.empty()))));
  }

  @Test
  void typeModelPreservesAnnotationsCaptureAndAnonymousIdentity() {
    TypeRef string = new DeclaredType("java.lang.String", List.of());
    var annotated =
        new AnnotatedType(
            string, List.of(new AnnotationRef("org.example.NonNull", Map.of("value", "x"))));
    var wildcard = new WildcardType(Optional.of(string), Optional.empty());
    var capture = new CapturedType("stable-1", wildcard, List.of(string), Optional.empty());
    var anonymous =
        new AnonymousType(
            "node-7", string, List.of(new DeclaredType("java.io.Serializable", List.of())));
    assertTrue(annotated.displayName().contains("NonNull"));
    assertTrue(capture.displayName().contains("stable-1"));
    assertNotEquals(anonymous, new AnonymousType("node-8", string, List.of()));
    assertFalse(capture.displayName().equals("java.lang.Object"));
  }
}
