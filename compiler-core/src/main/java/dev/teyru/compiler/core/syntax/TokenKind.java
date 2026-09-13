package dev.teyru.compiler.core.syntax;

public enum TokenKind {
  IDENTIFIER,
  KEYWORD,
  LITERAL,
  SYMBOL,
  END_OF_FILE,
  ERROR,
  NEWLINE,
  SEMICOLON,
  EOF,
  OPERATOR,
  PUNCTUATION
}
