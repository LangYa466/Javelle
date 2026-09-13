/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.syntax.*;
import org.junit.jupiter.api.Test;

/** P12: complete lexer inventory, Unicode/CRLF/BOM policy, and error recovery. */
class P12LexerCompleteTest {
  private static LexResult lex(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var budget = new ResourceBudget(1_000_000, 10_000, 10_000, 1_000, 1_000, Long.MAX_VALUE);
    var source = SourceFile.decode(SourceId.forContent("p12.teyru", bytes), bytes, budget);
    return new StatefulTeyruLexer()
        .lex(
            source,
            new FrontendOptions(1, 1_000),
            new ResourceTracker(budget, CancellationToken.none(), () -> 0),
            CancellationToken.none());
  }

  @Test
  void fullJava25ReservedWordsLexAsKeywords() {
    String source =
        "abstract assert boolean break byte case catch char class const continue default do "
            + "double else enum extends final finally float for goto if implements import "
            + "instanceof int interface long native new package private protected public return "
            + "short static strictfp super switch synchronized this throw throws transient try "
            + "void volatile while true false null";
    var result = lex(source);
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    for (Token token : result.tokens())
      if (!token.value().isBlank())
        assertEquals(TokenKind.KEYWORD, token.kind(), token.value() + " must lex as KEYWORD");
  }

  @Test
  void contextualWordsLexAsPlainIdentifiers() {
    // These are NOT reserved in Java; disambiguating their grammar role is a parser concern.
    for (String word : List.of("var", "yield", "record", "sealed", "permits", "val")) {
      var result = lex(word + " " + word + "()");
      assertTrue(result.diagnostics().isEmpty(), word + ": " + result.diagnostics());
      assertEquals(
          TokenKind.IDENTIFIER,
          result.tokens().getFirst().kind(),
          word + " must lex as IDENTIFIER outside grammar context");
    }
  }

  @Test
  void javadocCommentsAreDistinguishedFromPlainBlockComments() {
    var doc = lex("/** hello */ class C {}");
    Token classToken =
        doc.tokens().stream().filter(t -> t.value().equals("class")).findFirst().orElseThrow();
    assertEquals(TriviaKind.JAVADOC, classToken.leadingTrivia().getFirst().kind());

    var plain = lex("/* hello */ class C {}");
    Token classToken2 =
        plain.tokens().stream().filter(t -> t.value().equals("class")).findFirst().orElseThrow();
    assertEquals(TriviaKind.BLOCK_COMMENT, classToken2.leadingTrivia().getFirst().kind());

    var empty = lex("/**/ class C {}");
    Token classToken3 =
        empty.tokens().stream().filter(t -> t.value().equals("class")).findFirst().orElseThrow();
    assertEquals(
        TriviaKind.BLOCK_COMMENT,
        classToken3.leadingTrivia().getFirst().kind(),
        "/**/ is empty, not a doc comment");
  }

  @Test
  void utf8BomIsConsumedAsLeadingTriviaNotIdentifierText() {
    var result = lex("\uFEFFclass C {}");
    Token classToken = result.tokens().getFirst();
    assertEquals("class", classToken.value());
    assertEquals(TriviaKind.BOM, classToken.leadingTrivia().getFirst().kind());
  }

  @Test
  void crLfAndLoneCrAllLexAsNewlineTokens() {
    for (String newline : List.of("\r\n", "\n", "\r")) {
      var result = lex("a" + newline + "b");
      assertEquals(
          List.of("a", newline, "b", ""),
          result.tokens().stream().map(Token::value).toList(),
          "newline form: " + newline.replace("\r", "\\r").replace("\n", "\\n"));
    }
  }

  @Test
  void astralIdentifiersViaSurrogatePairsLexAsOneIdentifier() {
    String astral = "\uD800\uDC00"; // U+10000, a valid Java identifier start/part
    var result = lex(astral + " " + astral + astral);
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(
        List.of(astral, astral + astral, ""), result.tokens().stream().map(Token::value).toList());
  }

  @Test
  void validStringEscapesAreAccepted() {
    var result = lex("\"\\b\\t\\n\\f\\r\\\"\\'\\\\\\s\\0\\12\\177\"");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(TokenKind.LITERAL, result.tokens().getFirst().kind());
  }

  @Test
  void invalidEscapeIsDiagnosedButLexingContinues() {
    var result = lex("\"\\q\" ok");
    assertEquals("TY-SYN-0003", result.diagnostics().getFirst().code().value());
    assertEquals(TokenKind.LITERAL, result.tokens().getFirst().kind());
    assertTrue(result.tokens().stream().anyMatch(t -> t.value().equals("ok")));
  }

  @Test
  void backslashBeforeRawNewlineInPlainStringIsInvalidAndUnterminated() {
    var result = lex("\"a\\\nb\nok");
    assertEquals(TokenKind.ERROR, result.tokens().getFirst().kind());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0003")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().value().equals("TY-SYN-0002")));
    assertTrue(result.tokens().stream().anyMatch(t -> t.value().equals("ok")));
  }

  @Test
  void wellFormedTextBlockLexesAsOneLiteralWithNoDiagnostics() {
    var result = lex("\"\"\"\n  hello\n  world\n  \"\"\"");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(TokenKind.LITERAL, result.tokens().getFirst().kind());
    assertEquals("\"\"\"\n  hello\n  world\n  \"\"\"", result.tokens().getFirst().value());
  }

  @Test
  void textBlockEscapedClosingQuotesDoNotEndTheBlock() {
    var result = lex("\"\"\"\n  a \\\"\"\" b\n  \"\"\"");
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(TokenKind.LITERAL, result.tokens().getFirst().kind());
  }

  @Test
  void textBlockLineContinuationEscapeIsOnlyValidInTextBlocks() {
    var block = lex("\"\"\"\n  a\\\n  b\n  \"\"\"");
    assertTrue(block.diagnostics().isEmpty(), block.diagnostics().toString());
  }

  @Test
  void textBlockOpenDelimiterMustBeFollowedByLineTerminator() {
    var result = lex("\"\"\"bad\n\"\"\"");
    assertEquals("TY-SYN-0004", result.diagnostics().getFirst().code().value());
  }

  @Test
  void unterminatedTextBlockIsOneBoundedDiagnosticNotAHang() {
    var result = lex("\"\"\"\nabc");
    assertEquals(TokenKind.ERROR, result.tokens().getFirst().kind());
    assertEquals(1, result.diagnostics().size());
    assertEquals("TY-SYN-0002", result.diagnostics().getFirst().code().value());
  }

  @Test
  void deterministicMutationFuzzNeverThrowsHangsOrExceedsBudgets() {
    var random = new Random(42);
    String[] pieces = {
      "\"", "'", "\\", "u", "0021", "\r", "\n", "/*", "*/", "//", "\"\"\"", "class", "int", "0x",
      "1e", ".", "_", " ", "\t", "\uFEFF", "\uD800", "\uDC00", ";", "%"
    };
    for (int trial = 0; trial < 500; trial++) {
      var sb = new StringBuilder();
      int length = random.nextInt(40);
      for (int i = 0; i < length; i++) sb.append(pieces[random.nextInt(pieces.length)]);
      String source = sb.toString();
      int trialNumber = trial;
      assertDoesNotThrow(
          () -> lex(source),
          () -> "fuzz trial " + trialNumber + " threw for input: " + escape(source));
    }
  }

  @Test
  void newlineTokensCarryPositionMetadataAndNoSyntheticSemicolonIsEverInserted() {
    var result = lex("a\nb\r\nc\rd");
    var newlines = result.tokens().stream().filter(t -> t.kind() == TokenKind.NEWLINE).toList();
    assertEquals(3, newlines.size());
    for (Token newline : newlines) {
      assertTrue(newline.rawRange().length() > 0);
      assertTrue(newline.translatedRange().length() > 0);
    }
    assertTrue(
        result.tokens().stream().noneMatch(t -> t.kind() == TokenKind.SEMICOLON),
        "the lexer must never insert a synthetic ';' — statement continuation is a parser concern");
  }

  private static String escape(String s) {
    var out = new StringBuilder();
    s.chars().forEach(c -> out.append(String.format("\\u%04x", c)));
    return out.toString();
  }
}
