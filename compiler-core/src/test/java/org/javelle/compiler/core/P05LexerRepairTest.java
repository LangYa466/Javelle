package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.syntax.*;
import org.junit.jupiter.api.Test;

class P05LexerRepairTest {
  private static LexResult lex(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var budget = new ResourceBudget(1_000_000, 10_000, 10_000, 100, 100, Long.MAX_VALUE);
    var source = SourceFile.decode(SourceId.forContent("repair.javelle", bytes), bytes, budget);
    return new StatefulJavelleLexer()
        .lex(
            source,
            new FrontendOptions(1, 100),
            new ResourceTracker(budget, CancellationToken.none(), () -> 0),
            CancellationToken.none());
  }

  @Test
  void operatorsUseLongestMatch() {
    String source =
        "a != b <= c >= d == e && f || g ++ -- += -= *= /= %= &= |= ^= << >> >>> <<= >>= >>>= -> :: ...";
    var operators =
        lex(source).tokens().stream()
            .filter(token -> token.kind() == TokenKind.OPERATOR)
            .map(Token::value)
            .toList();
    assertEquals(
        List.of(
            "!=", "<=", ">=", "==", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=",
            "|=", "^=", "<<", ">>", ">>>", "<<=", ">>=", ">>>=", "->", "::", "..."),
        operators);
  }

  @Test
  void malformedNumericIsBoundedAndRecoveryContinues() {
    var result = lex("12abc + 3\n4e2 5L");
    assertEquals(TokenKind.ERROR, result.tokens().getFirst().kind());
    assertEquals("12abc", result.tokens().getFirst().value());
    assertEquals("JV-SYN-0002", result.diagnostics().getFirst().code().value());
    assertTrue(result.tokens().stream().anyMatch(token -> token.value().equals("+")));
    assertTrue(result.tokens().stream().anyMatch(token -> token.value().equals("3")));
    assertEquals(1, result.diagnostics().size());
  }

  @Test
  void allJavaNumericLiteralFamiliesRemainSingleTokens() {
    String source =
        "0 0777 0xFF 0XCAFE_BABE 0b1010 0B1_0_1 1_000 42L 1f 2D "
            + "3.14 .5 6. 1e10 1E-9 2.5e+3F 0x1.fp3 0X.8P-1f";
    var result = lex(source);
    assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
    assertEquals(
        List.of(
            "0",
            "0777",
            "0xFF",
            "0XCAFE_BABE",
            "0b1010",
            "0B1_0_1",
            "1_000",
            "42L",
            "1f",
            "2D",
            "3.14",
            ".5",
            "6.",
            "1e10",
            "1E-9",
            "2.5e+3F",
            "0x1.fp3",
            "0X.8P-1f"),
        result.tokens().stream()
            .filter(token -> token.kind() == TokenKind.LITERAL)
            .map(Token::value)
            .toList());
  }

  @Test
  void numericBoundariesDoNotConsumeFollowingOperators() {
    var result = lex("1..toString 0x1p2+3 0b10<<2 12abc.next");
    assertEquals(
        List.of(
            "1.", ".", "toString", "0x1p2", "+", "3", "0b10", "<<", "2", "12abc", ".", "next", ""),
        result.tokens().stream().map(Token::value).toList());
    assertEquals(1, result.diagnostics().size());
    assertEquals("12abc", result.tokens().get(9).value());
    assertEquals(TokenKind.ERROR, result.tokens().get(9).kind());
  }

  @Test
  void incompleteRadixAndExponentFormsAreBoundedErrors() {
    var result = lex("0xG 0b2 1e+ 0x1p- ok");
    assertEquals(
        List.of("0xG", "0b2", "1e+", "0x1p-", "ok", ""),
        result.tokens().stream().map(Token::value).toList());
    assertEquals(
        List.of("0xG", "0b2", "1e+", "0x1p-"),
        result.tokens().stream()
            .filter(token -> token.kind() == TokenKind.ERROR)
            .map(Token::value)
            .toList());
    assertEquals(4, result.diagnostics().size());
  }

  @Test
  void commentsBecomeTrailingAndRawTextIsConservedExactlyOnce() {
    String source = "  class /*x*/ C {\n // lead\n String s = \"a;b\" // tail\n}\n";
    var result = lex(source);
    Token keyword =
        result.tokens().stream()
            .filter(token -> token.value().equals("class"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        " /*x*/ ",
        keyword.trailingTrivia().stream().map(Trivia::rawText).reduce("", String::concat));
    StringBuilder rebuilt = new StringBuilder();
    for (Token token : result.tokens()) {
      token.leadingTrivia().forEach(item -> rebuilt.append(item.rawText()));
      rebuilt.append(token.rawText());
      token.trailingTrivia().forEach(item -> rebuilt.append(item.rawText()));
    }
    assertEquals(source, rebuilt.toString());
    assertEquals(
        0, result.tokens().stream().filter(token -> token.kind() == TokenKind.SEMICOLON).count());
  }

  @Test
  void literalsCharsCommentsAndTextBlocksProtectSemicolonData() {
    var ordinary = lex("\"a;b\" ';' /* ; */ // ;\n;");
    assertEquals(
        1, ordinary.tokens().stream().filter(token -> token.kind() == TokenKind.SEMICOLON).count());
    var block = lex("\"\"\"\n;a\n\"\"\"");
    assertEquals(TokenKind.ERROR, block.tokens().getFirst().kind());
    assertEquals("JV-DEV-0001", block.diagnostics().getFirst().code().value());
    assertEquals(
        0, block.tokens().stream().filter(token -> token.kind() == TokenKind.SEMICOLON).count());
  }

  @Test
  void unicodeEscapesRetainRawSpansAcrossLongestMatch() {
    var result = lex("a \\u0021= b \\u003b c");
    Token notEquals =
        result.tokens().stream()
            .filter(token -> token.value().equals("!="))
            .findFirst()
            .orElseThrow();
    assertEquals("\\u0021=", notEquals.rawText());
    assertEquals(7, notEquals.rawRange().length());
    Token semicolon =
        result.tokens().stream()
            .filter(token -> token.kind() == TokenKind.SEMICOLON)
            .findFirst()
            .orElseThrow();
    assertEquals("\\u003b", semicolon.rawText());
    assertEquals(6, semicolon.rawRange().length());
  }
}
