package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.syntax.*;

public final class StatefulJavelleLexer implements JavelleLexer {
  private static final List<String> MULTI =
      List.of(
          ">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "==", "!=", "<=", ">=", "&&", "||", "++",
          "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>");
  private static final Set<String> KEYWORDS =
      Set.of(
          "package",
          "import",
          "class",
          "enum",
          "public",
          "protected",
          "private",
          "static",
          "final",
          "abstract",
          "void",
          "return",
          "if",
          "else",
          "new",
          "null",
          "true",
          "false");

  public LexResult lex(
      SourceFile source,
      FrontendOptions options,
      ResourceTracker resources,
      CancellationToken cancellation) {
    String text = source.translatedText();
    var out = new ArrayList<Token>();
    var diagnostics = new ArrayList<Diagnostic>();
    var trivia = new ArrayList<Trivia>();
    int i = 0;
    boolean tokenOnLine = false;
    while (i < text.length()) {
      resources.checkpoint();
      cancellation.throwIfCancelled();
      char c = text.charAt(i);
      int start = i;
      if (c == ' ' || c == '\t' || c == '\f') {
        while (i < text.length() && " \t\f".indexOf(text.charAt(i)) >= 0) i++;
        trivia.add(trivia(source, TriviaKind.WHITESPACE, start, i));
        continue;
      }
      if (c == '\r' || c == '\n') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i += 2;
        else i++;
        if (tokenOnLine && hasComment(trivia)) {
          attachTrailing(out, trivia);
          trivia = new ArrayList<>();
        }
        out.add(
            token(
                source,
                TokenKind.NEWLINE,
                start,
                i,
                text.substring(start, i),
                tokenOnLine ? List.of() : trivia));
        trivia = new ArrayList<>();
        tokenOnLine = false;
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        i += 2;
        while (i < text.length() && text.charAt(i) != '\r' && text.charAt(i) != '\n') i++;
        trivia.add(trivia(source, TriviaKind.LINE_COMMENT, start, i));
        continue;
      }
      if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
        if (i + 1 < text.length()) i += 2;
        else
          diagnostics.add(
              diag(source, "JV-SYN-0002", start, Math.max(start + 1, i), "unterminated comment"));
        trivia.add(trivia(source, TriviaKind.BLOCK_COMMENT, start, i));
        continue;
      }
      if (tokenOnLine && hasComment(trivia)) {
        attachTrailing(out, trivia);
        trivia = new ArrayList<>();
      }
      TokenKind kind;
      String value;
      if (Character.isJavaIdentifierStart(text.codePointAt(i))) {
        i += Character.charCount(text.codePointAt(i));
        while (i < text.length() && Character.isJavaIdentifierPart(text.codePointAt(i)))
          i += Character.charCount(text.codePointAt(i));
        value = text.substring(start, i);
        kind = KEYWORDS.contains(value) ? TokenKind.KEYWORD : TokenKind.IDENTIFIER;
      } else if (Character.isDigit(c)
          || (c == '.' && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1)))) {
        int numericEnd = scanNumeric(text, i);
        boolean malformedNumeric = numericEnd < 0;
        i = malformedNumeric ? -numericEnd - 1 : numericEnd;
        value = text.substring(start, i);
        kind = malformedNumeric ? TokenKind.ERROR : TokenKind.LITERAL;
        if (kind == TokenKind.ERROR)
          diagnostics.add(diag(source, "JV-SYN-0002", start, i, "malformed numeric literal"));
      } else if (c == '"' && text.startsWith("\"\"\"", i)) {
        i += 3;
        int close = text.indexOf("\"\"\"", i);
        i = close < 0 ? text.length() : close + 3;
        value = text.substring(start, i);
        kind = TokenKind.ERROR;
        diagnostics.add(diag(source, "JV-DEV-0001", start, i, "text blocks unsupported at P05"));
      } else if (c == '"' || c == '\'') {
        char quote = c;
        i++;
        boolean closed = false;
        while (i < text.length()) {
          char x = text.charAt(i++);
          if (x == '\\' && i < text.length()) {
            i++;
            continue;
          }
          if (x == quote) {
            closed = true;
            break;
          }
          if (x == '\r' || x == '\n') break;
        }
        value = text.substring(start, i);
        kind = closed ? TokenKind.LITERAL : TokenKind.ERROR;
        if (!closed) diagnostics.add(diag(source, "JV-SYN-0002", start, i, "unterminated literal"));
      } else {
        String longest =
            MULTI.stream()
                .filter(candidate -> text.startsWith(candidate, start))
                .findFirst()
                .orElse(null);
        i += longest == null ? 1 : longest.length();
        kind =
            c == ';'
                ? TokenKind.SEMICOLON
                : longest != null
                    ? TokenKind.OPERATOR
                    : "(){}[],.?".indexOf(c) >= 0 ? TokenKind.PUNCTUATION : TokenKind.OPERATOR;
        value = text.substring(start, i);
      }
      out.add(token(source, kind, start, i, value, trivia));
      trivia = new ArrayList<>();
      tokenOnLine = true;
      resources.token();
      if (diagnostics.size() >= options.maxDiagnostics()) break;
    }
    if (tokenOnLine && hasComment(trivia)) {
      attachTrailing(out, trivia);
      trivia = new ArrayList<>();
    }
    out.add(token(source, TokenKind.EOF, text.length(), text.length(), "", trivia));
    return new LexResult(out, diagnostics);
  }

  private static int scanNumeric(String text, int start) {
    int i = start;
    boolean malformed = false;
    boolean floating = text.charAt(i) == '.';
    int radix = 10;
    if (floating) {
      i++;
      i = consumeDigits(text, i, 10);
    } else if (text.charAt(i) == '0' && i + 1 < text.length()) {
      char prefix = text.charAt(i + 1);
      if (prefix == 'x' || prefix == 'X') {
        radix = 16;
        i += 2;
        int integerStart = i;
        i = consumeDigits(text, i, radix);
        boolean hasHexDigit = containsDigit(text, integerStart, i, radix);
        if (i < text.length() && text.charAt(i) == '.') {
          floating = true;
          i++;
          int fractionStart = i;
          i = consumeDigits(text, i, radix);
          hasHexDigit |= containsDigit(text, fractionStart, i, radix);
        }
        if (!hasHexDigit) malformed = true;
        if (i < text.length() && (text.charAt(i) == 'p' || text.charAt(i) == 'P')) {
          floating = true;
          int exponentStart = exponentDigitsStart(text, i + 1);
          i = consumeExponent(text, i + 1);
          if (!containsDigit(text, exponentStart, i, 10)) malformed = true;
        } else if (floating) {
          malformed = true;
        }
      } else if (prefix == 'b' || prefix == 'B') {
        radix = 2;
        i += 2;
        int digits = i;
        i = consumeDigits(text, i, radix);
        if (i == digits) malformed = true;
      } else {
        i = consumeDigits(text, i, 10);
      }
    } else {
      i = consumeDigits(text, i, 10);
    }
    if (radix == 10) {
      if (i < text.length() && text.charAt(i) == '.') {
        floating = true;
        i = consumeDigits(text, i + 1, 10);
      }
      if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
        floating = true;
        int exponentStart = exponentDigitsStart(text, i + 1);
        i = consumeExponent(text, i + 1);
        if (!containsDigit(text, exponentStart, i, 10)) malformed = true;
      }
    }
    if (i < text.length()) {
      char suffix = text.charAt(i);
      if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D') {
        floating = true;
        i++;
      } else if ((suffix == 'l' || suffix == 'L') && !floating) {
        i++;
      }
    }
    int invalidStart = i;
    while (i < text.length() && Character.isJavaIdentifierPart(text.codePointAt(i)))
      i += Character.charCount(text.codePointAt(i));
    if (i > invalidStart) malformed = true;
    return malformed ? -i - 1 : i;
  }

  private static int consumeExponent(String text, int i) {
    if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) i++;
    return consumeDigits(text, i, 10);
  }

  private static int exponentDigitsStart(String text, int i) {
    return i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-') ? i + 1 : i;
  }

  private static boolean containsDigit(String text, int start, int end, int radix) {
    for (int i = start; i < end; i++) if (Character.digit(text.charAt(i), radix) >= 0) return true;
    return false;
  }

  private static int consumeDigits(String text, int i, int radix) {
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '_' || Character.digit(c, radix) >= 0) i++;
      else break;
    }
    return i;
  }

  private static boolean hasComment(List<Trivia> trivia) {
    return trivia.stream()
        .anyMatch(
            item ->
                item.kind() == TriviaKind.LINE_COMMENT
                    || item.kind() == TriviaKind.BLOCK_COMMENT
                    || item.kind() == TriviaKind.JAVADOC);
  }

  private static void attachTrailing(List<Token> tokens, List<Trivia> trivia) {
    int index = tokens.size() - 1;
    Token old = tokens.get(index);
    var combined = new ArrayList<>(old.trailingTrivia());
    combined.addAll(trivia);
    tokens.set(
        index,
        new Token(
            old.kind(),
            old.rawRange(),
            old.translatedRange(),
            old.rawText(),
            old.value(),
            old.leadingTrivia(),
            combined,
            old.missing()));
  }

  private static Token token(
      SourceFile s, TokenKind k, int a, int b, String v, List<Trivia> trivia) {
    var translated = new TextRange(OffsetUnit.TRANSLATED_UTF16, a, b);
    var raw = s.unicodeMap().rawRangeForTranslated(translated);
    return new Token(
        k,
        raw,
        translated,
        s.rawText().substring(raw.startOffset(), raw.endOffset()),
        v,
        List.copyOf(trivia),
        List.of(),
        false);
  }

  private static Trivia trivia(SourceFile s, TriviaKind k, int a, int b) {
    var t = new TextRange(OffsetUnit.TRANSLATED_UTF16, a, b);
    var r = s.unicodeMap().rawRangeForTranslated(t);
    return new Trivia(k, r, t, s.rawText().substring(r.startOffset(), r.endOffset()));
  }

  private static Diagnostic diag(SourceFile s, String code, int a, int b, String message) {
    var t = new TextRange(OffsetUnit.TRANSLATED_UTF16, a, b);
    return new Diagnostic(
        1,
        new DiagnosticCode(code),
        Severity.ERROR,
        s.id(),
        s.unicodeMap().rawRangeForTranslated(t),
        message,
        List.of(),
        Map.of());
  }
}
