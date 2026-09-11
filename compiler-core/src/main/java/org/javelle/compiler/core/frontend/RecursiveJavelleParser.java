package org.javelle.compiler.core.frontend;

import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.syntax.*;

public final class RecursiveJavelleParser implements JavelleParser {
  private SourceFile source;
  private List<Token> tokens;
  private FrontendOptions options;
  private ResourceTracker resources;
  private int p, ordinal;
  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private boolean recovered;

  public ParseResult parse(
      SourceFile source,
      LexResult lexical,
      FrontendOptions options,
      ResourceTracker resources,
      CancellationToken cancellation) {
    this.source = source;
    tokens = lexical.tokens();
    this.options = options;
    this.resources = resources;
    diagnostics.addAll(lexical.diagnostics());
    var children = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF)) {
      skipLines();
      if (at(TokenKind.EOF)) break;
      int before = p;
      if (word("record") || word("@interface") || word("module") || word("open")) {
        children.add(
            unsupported(
                word("record")
                    ? "record-declaration"
                    : word("module") || word("open")
                        ? "module-declaration"
                        : "interface-declaration"));
      } else if (word("interface")) {
        Token start = take();
        String name = identifier();
        children.add(parseInterface(start, name));
      } else if (word("enum")) {
        Token start = take();
        String name = identifier();
        children.add(parseEnum(start, name));
      } else if (word("package")) {
        children.add(scanLine("PackageDeclaration"));
      } else if (word("import")) {
        children.add(scanLine("ImportDeclaration"));
      } else if (findWordAhead("class", 6)) {
        while (!word("class") && !at(TokenKind.EOF)) take();
        Token start = take();
        String name = identifier();
        children.add(parseClass(start, name));
      } else {
        error(current(), "JV-SYN-0002", "expected declaration");
        take();
      }
      if (p == before) take();
    }
    var range = new TextRange(OffsetUnit.RAW_UTF16, 0, source.rawText().length());
    var ast = node("CompilationUnit", "", range, children);
    var rootId = node("CompilationUnit", range);
    var cstChildren = new ArrayList<CstNode>();
    for (Token token : tokens) {
      var id = new NodeId(source.id(), "Token", token.rawRange(), ordinal++);
      cstChildren.add(new CstToken(id, token.rawRange(), token, Optional.of(rootId)));
    }
    var cst = new CstBranch(rootId, range, cstChildren, Optional.empty());
    return new ParseResult(
        ast,
        cst,
        new NodeIndex(List.of(cst)),
        List.copyOf(diagnostics),
        recovered || !diagnostics.isEmpty());
  }

  private FrontendNode parseClass(Token start, String name) {
    var members = new ArrayList<FrontendNode>();
    if (!accept("{")) {
      error(current(), "JV-SYN-0002", "missing class body");
      return node("ClassDeclaration", name, start.rawRange(), members);
    }
    members.addAll(parseClassBodyMembers());
    Token end = current();
    if (!accept("}")) error(end, "JV-SYN-0002", "unterminated class");
    return node("ClassDeclaration", name, span(start, end), members);
  }

  /** Parses class/enum-member-section members up to (not including) the closing {@code }}. */
  private List<FrontendNode> parseClassBodyMembers() {
    var members = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      int before = p;
      for (; isModifier(); take()) {}
      if (word("var") || word("val")) {
        Token bad = take();
        error(bad, "JV-TYP-0003", "inferred field type is illegal");
        sync();
        members.add(node("ErrorNode", "", bad.rawRange(), List.of()));
      } else if (isIdentifierLike()) {
        Token type = take();
        if (!isIdentifierLike()) {
          error(current(), "JV-SYN-0002", "missing member name");
          sync();
          continue;
        }
        Token member = take();
        if (accept("(")) members.add(parseMethod(type, member));
        else if (accept("{")) members.add(parseProperty(type, member, Optional.empty()));
        else if (accept("=")) {
          var initializerTokens = new ArrayList<Token>();
          while (!at(TokenKind.EOF) && !at(TokenKind.NEWLINE) && !word("{")) {
            if (at(TokenKind.SEMICOLON)) semicolon(take());
            else initializerTokens.add(take());
          }
          if (accept("{") && !initializerTokens.isEmpty())
            members.add(
                parseProperty(type, member, Optional.of(buildExpression(initializerTokens))));
          else
            members.add(
                parseField(
                    type,
                    member,
                    initializerTokens.isEmpty()
                        ? Optional.empty()
                        : Optional.of(buildExpression(initializerTokens))));
        } else members.add(parseField(type, member));
      } else if (word("for")) {
        members.add(unsupported("basic-for"));
      } else {
        error(current(), "JV-SYN-0002", "unsupported member");
        sync();
      }
      if (before == p) take();
    }
    return members;
  }

  /**
   * Enum constants end at {@code :} (member section boundary), {@code }} (no member section), or a
   * trailing comma before either. Constant arguments and constant-specific class bodies are not yet
   * supported (P13 round 2).
   */
  private FrontendNode parseEnum(Token start, String name) {
    var members = new ArrayList<FrontendNode>();
    if (!accept("{")) {
      error(current(), "JV-SYN-0002", "missing enum body");
      return node("EnumDeclaration", name, start.rawRange(), members);
    }
    skipLines();
    while (isIdentifierLike() && !word("}") && !word(":")) {
      Token constant = take();
      members.add(
          node("EnumConstantDeclaration", constant.value(), constant.rawRange(), List.of()));
      skipLines();
      if (word(",")) {
        take();
        skipLines();
      } else break;
    }
    if (word(":")) {
      take();
      members.addAll(parseClassBodyMembers());
    }
    Token end = current();
    if (!accept("}")) error(end, "JV-SYN-0002", "unterminated enum");
    return node("EnumDeclaration", name, span(start, end), members);
  }

  /**
   * Interface members are implicitly public; abstract methods have no body (bodiless, terminated by
   * newline per JLS interface-method-declaration grammar); {@code default}/{@code static} methods
   * require a real body; fields are implicitly public/static/final constants and require an
   * initializer.
   */
  private FrontendNode parseInterface(Token start, String name) {
    var members = new ArrayList<FrontendNode>();
    if (!accept("{")) {
      error(current(), "JV-SYN-0002", "missing interface body");
      return node("InterfaceDeclaration", name, start.rawRange(), members);
    }
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      int before = p;
      boolean hasBody = false;
      for (; isModifier() || word("default") || word("static"); ) {
        hasBody |= word("default") || word("static");
        take();
      }
      if (isIdentifierLike()) {
        Token type = take();
        if (!isIdentifierLike()) {
          error(current(), "JV-SYN-0002", "missing member name");
          sync();
          continue;
        }
        Token member = take();
        if (accept("(")) members.add(parseMethod(type, member, hasBody));
        else if (accept("=")) {
          var initializerTokens = new ArrayList<Token>();
          while (!at(TokenKind.EOF) && !at(TokenKind.NEWLINE)) {
            if (at(TokenKind.SEMICOLON)) semicolon(take());
            else initializerTokens.add(take());
          }
          if (initializerTokens.isEmpty()) {
            error(member, "JV-SYN-0002", "interface constant requires an initializer");
            members.add(node("ErrorNode", "", span(type, member), List.of()));
          } else
            members.add(parseField(type, member, Optional.of(buildExpression(initializerTokens))));
        } else {
          error(current(), "JV-SYN-0002", "interface constant requires an initializer");
          sync();
        }
      } else {
        error(current(), "JV-SYN-0002", "unsupported member");
        sync();
      }
      if (before == p) take();
    }
    Token end = current();
    if (!accept("}")) error(end, "JV-SYN-0002", "unterminated interface");
    return node("InterfaceDeclaration", name, span(start, end), members);
  }

  private FrontendNode parseMethod(Token type, Token name) {
    return parseMethod(type, name, true);
  }

  private FrontendNode parseMethod(Token type, Token name, boolean bodyRequired) {
    var parameterTokens = takeUntilCloseParen();
    var methodChildren = new ArrayList<FrontendNode>();
    Optional<Token> varargs =
        parameterTokens.stream().filter(token -> token.value().equals("...")).findFirst();
    if (varargs.isPresent()) {
      Token introducing = varargs.orElseThrow();
      error(introducing, "JV-DEV-0001", "unsupported varargs");
      TextRange unsupportedRange =
          parameterTokens.isEmpty()
              ? introducing.rawRange()
              : span(parameterTokens.getFirst(), parameterTokens.getLast());
      methodChildren.add(
          new UnsupportedSyntaxNode(
              node("UnsupportedSyntaxNode", unsupportedRange),
              Optional.empty(),
              "varargs",
              unsupportedRange,
              List.of()));
    }
    for (int i = 0; varargs.isEmpty() && i + 1 < parameterTokens.size(); ) {
      Token parameterType = parameterTokens.get(i++);
      Token parameterName = parameterTokens.get(i++);
      if (!isIdentifierLike(parameterType) || !isIdentifierLike(parameterName)) {
        error(parameterType, "JV-SYN-0002", "invalid parameter");
        break;
      }
      methodChildren.add(
          node(
              "ParameterDeclaration",
              parameterName.value(),
              span(parameterType, parameterName),
              List.of()));
      if (i < parameterTokens.size() && parameterTokens.get(i).value().equals(",")) i++;
      else if (i < parameterTokens.size()) {
        error(parameterTokens.get(i), "JV-SYN-0002", "expected parameter comma");
        break;
      }
    }
    if (!accept("{")) {
      if (!bodyRequired && (at(TokenKind.NEWLINE) || at(TokenKind.EOF) || word("}")))
        return node("MethodDeclaration", name.value(), span(type, name), methodChildren);
      error(current(), "JV-SYN-0002", "missing method body");
      return node("MethodDeclaration", name.value(), span(type, name), methodChildren);
    }
    methodChildren.addAll(parseStatements());
    return node("MethodDeclaration", name.value(), span(type, previous()), methodChildren);
  }

  private List<FrontendNode> parseStatements() {
    var out = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      if (word("for")) {
        out.add(unsupported("basic-for"));
        continue;
      }
      if (Set.of("while", "do", "switch", "try", "synchronized", "assert")
          .contains(current().value())) {
        String feature =
            switch (current().value()) {
              case "while", "do" -> "loop-statement";
              case "switch" -> "switch";
              case "try" -> "try";
              case "synchronized" -> "synchronized";
              default -> "assert";
            };
        out.add(unsupported(feature));
        continue;
      }
      if (word("return")) {
        Token start = take();
        var expr = parseExpressionUntilBoundary();
        if (expr == null && at(TokenKind.EOF)) {
          error(start, "JV-SYN-0002", "missing return expression");
          out.add(node("ErrorNode", "", start.rawRange(), List.of()));
        } else
          out.add(
              node(
                  "ReturnStatement",
                  "",
                  expr == null ? start.rawRange() : span(start, previous()),
                  expr == null ? List.of() : List.of(expr)));
        continue;
      }
      if (word("if")) {
        Token start = take();
        var conditionTokens = takeBalanced("(", ")");
        var kids = new ArrayList<FrontendNode>();
        if (!conditionTokens.isEmpty()) kids.add(buildExpression(conditionTokens));
        else error(start, "JV-SYN-0002", "missing if condition");
        if (accept("{")) {
          Token blockStart = previous();
          var statements = parseStatements();
          kids.add(node("Block", "", span(blockStart, previous()), statements));
        } else {
          var statement = parseExpressionUntilBoundary();
          if (statement != null) kids.add(statement);
        }
        skipLines();
        if (accept("else")) {
          if (accept("{")) {
            Token blockStart = previous();
            var statements = parseStatements();
            kids.add(node("Block", "", span(blockStart, previous()), statements));
          } else {
            var statement = parseExpressionUntilBoundary();
            if (statement != null) kids.add(statement);
          }
        }
        out.add(node("IfStatement", "", span(start, previous()), kids));
        continue;
      }
      if ((word("var") || word("val") || isIdentifierLike()) && lookIdentifier(1)) {
        Token type = take(), name = take();
        var kids = new ArrayList<FrontendNode>();
        if (accept("=")) {
          var expr = parseExpressionUntilBoundary();
          if (expr != null) kids.add(expr);
          else {
            error(previous(), "JV-SYN-0002", "missing initializer expression");
            kids.add(node("ErrorNode", "", previous().rawRange(), List.of()));
          }
          if ((type.value().equals("var") || type.value().equals("val"))
              && expr != null
              && expr.kind().equals("NullLiteral"))
            error(previous(), "JV-TYP-0002", "cannot infer null");
        } else if (type.value().equals("var") || type.value().equals("val"))
          error(name, "JV-SYN-0002", "initializer required");
        out.add(node("LocalVariableDeclaration", name.value(), span(type, previous()), kids));
        continue;
      }
      var expr = parseExpressionUntilBoundary();
      if (expr != null) out.add(node("ExpressionStatement", "", expr.range(), List.of(expr)));
      else take();
    }
    if (!accept("}")) {
      error(current(), "JV-SYN-0002", "unterminated block");
      out.add(node("ErrorNode", "", current().rawRange(), List.of()));
    }
    return out;
  }

  private FrontendNode parseProperty(Token type, Token name, Optional<FrontendNode> initializer) {
    var accessors = new ArrayList<FrontendNode>();
    initializer.ifPresent(
        value -> accessors.add(node("PropertyInitializer", "", value.range(), List.of(value))));
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      String visibility = "";
      for (; isModifier(); ) {
        if (Set.of("public", "protected", "private").contains(current().value()))
          visibility = current().value();
        take();
      }
      if (word("get") || word("set")) {
        Token a = take();
        String n = a.value().equals("set") ? "value" : "";
        if (accept("(")) {
          if (isIdentifierLike()) n = take().value();
          balancedUntil(")");
        }
        var kids = new ArrayList<FrontendNode>();
        if (accept("{")) kids.addAll(parseStatements());
        if (a.value().equals("get")) {
          for (var child : flatten(kids))
            if (child.kind().equals("NameExpression") && child.name().equals("field")) {
              diagnostics.add(
                  new Diagnostic(
                      1,
                      new DiagnosticCode("JV-PROP-0006"),
                      Severity.ERROR,
                      source.id(),
                      child.range(),
                      "computed property has no backing field",
                      List.of(),
                      Map.of()));
              recovered = true;
            }
        }
        String kind = a.value().equals("get") ? "GetterDeclaration" : "SetterDeclaration";
        TextRange ar = span(a, previous());
        NodeId aid = node(kind, ar);
        var accessorChildren = kids.stream().map(child -> withParent(child, aid)).toList();
        accessors.add(
            new AccessorNode(aid, Optional.empty(), kind, n, visibility, ar, accessorChildren));
      } else {
        error(current(), "JV-SYN-0002", "incomplete accessor");
        take();
      }
    }
    Token end = current();
    if (!accept("}")) error(end, "JV-SYN-0002", "unterminated property");
    return node("PropertyDeclaration", name.value(), span(type, end), accessors);
  }

  private FrontendNode parseField(Token type, Token name) {
    var kids = new ArrayList<FrontendNode>();
    int diagnosticsBefore = diagnostics.size();
    if (accept("=")) {
      var x = parseExpressionUntilBoundary();
      if (x != null) kids.add(x);
    }
    syncLine();
    if (diagnostics.size() > diagnosticsBefore
        && diagnostics.subList(diagnosticsBefore, diagnostics.size()).stream()
            .anyMatch(d -> d.code().value().equals("JV-SYN-0001"))) {
      var range = diagnostics.getLast().range();
      kids.add(node("ErrorNode", "", range, List.of()));
    }
    return node("FieldDeclaration", name.value(), span(type, previous()), kids);
  }

  private FrontendNode parseField(
      Token type, Token name, Optional<FrontendNode> parsedInitializer) {
    var kids = new ArrayList<FrontendNode>();
    parsedInitializer.ifPresent(kids::add);
    if (!diagnostics.isEmpty() && diagnostics.getLast().code().value().equals("JV-SYN-0001"))
      kids.add(node("ErrorNode", "", diagnostics.getLast().range(), List.of()));
    syncLine();
    return node("FieldDeclaration", name.value(), span(type, previous()), kids);
  }

  private FrontendNode parseExpressionUntilBoundary() {
    if (atLineBoundary()) return null;
    Token start = current();
    var parts = new ArrayList<Token>();
    int depth = 0;
    while (!at(TokenKind.EOF) && !(depth == 0 && atLineBoundary())) {
      if (word("(")) depth++;
      if (word(")")) {
        if (depth == 0) break;
        depth--;
      }
      if (at(TokenKind.SEMICOLON)) {
        semicolon(take());
        continue;
      }
      parts.add(take());
    }
    if (parts.isEmpty()) return null;
    return buildExpression(parts);
  }

  private FrontendNode buildExpression(List<Token> parts) {
    Optional<Token> unsupportedOperator =
        parts.stream().filter(t -> Set.of("->", "::", "...").contains(t.value())).findFirst();
    if (unsupportedOperator.isPresent()) {
      Token token = unsupportedOperator.orElseThrow();
      String feature =
          token.value().equals("->")
              ? "lambda"
              : token.value().equals("::") ? "method-reference" : "varargs";
      TextRange range = span(parts.getFirst(), parts.getLast());
      error(token, "JV-DEV-0001", "unsupported " + feature);
      return new UnsupportedSyntaxNode(
          node("UnsupportedSyntaxNode", range), Optional.empty(), feature, range, List.of());
    }
    if (parts.stream().anyMatch(t -> t.value().startsWith("\"\"\""))) {
      Token token =
          parts.stream().filter(t -> t.value().startsWith("\"\"\"")).findFirst().orElseThrow();
      error(token, "JV-DEV-0001", "unsupported text-block");
      return new UnsupportedSyntaxNode(
          node("UnsupportedSyntaxNode", token.rawRange()),
          Optional.empty(),
          "text-block",
          token.rawRange(),
          List.of());
    }
    return new ExpressionCursor(parts).expression(1);
  }

  private List<FrontendNode> flatten(List<FrontendNode> roots) {
    var out = new ArrayList<FrontendNode>();
    var todo = new ArrayDeque<FrontendNode>(roots);
    while (!todo.isEmpty()) {
      var n = todo.removeFirst();
      out.add(n);
      todo.addAll(n.children());
    }
    return out;
  }

  private final class ExpressionCursor {
    private final List<Token> values;
    private int index;

    ExpressionCursor(List<Token> values) {
      this.values = values;
    }

    FrontendNode expression(int minimum) {
      FrontendNode left = unary();
      while (index < values.size()) {
        String op = values.get(index).value();
        int precedence = precedence(op);
        if (precedence < minimum) break;
        Token operator = values.get(index++);
        int next = op.equals("=") ? precedence : precedence + 1;
        FrontendNode right = expression(next);
        left =
            node(
                op.equals("=") ? "AssignmentExpression" : "BinaryExpression",
                operator.value(),
                new TextRange(
                    OffsetUnit.RAW_UTF16, left.range().startOffset(), right.range().endOffset()),
                List.of(left, right));
      }
      if (index < values.size() && values.get(index).value().equals("?")) {
        Token question = values.get(index++);
        FrontendNode yes = expression(1);
        if (index >= values.size() || !values.get(index).value().equals(":")) {
          error(question, "JV-SYN-0002", "missing ternary colon");
          return left;
        }
        index++;
        FrontendNode no = expression(1);
        return node(
            "ConditionalExpression",
            "",
            new TextRange(OffsetUnit.RAW_UTF16, left.range().startOffset(), no.range().endOffset()),
            List.of(left, yes, no));
      }
      return left;
    }

    FrontendNode unary() {
      if (index < values.size() && Set.of("!", "-", "+").contains(values.get(index).value())) {
        Token op = values.get(index++);
        FrontendNode value = unary();
        return node("UnaryExpression", op.value(), span(op, tokenEnd(value)), List.of(value));
      }
      return postfix(primary());
    }

    FrontendNode postfix(FrontendNode base) {
      while (index < values.size()) {
        if (values.get(index).value().equals(".")) {
          index++;
          if (index >= values.size()) return base;
          Token member = values.get(index++);
          base =
              node(
                  "MemberAccessExpression",
                  member.value(),
                  new TextRange(
                      OffsetUnit.RAW_UTF16,
                      base.range().startOffset(),
                      member.rawRange().endOffset()),
                  List.of(base));
        } else if (values.get(index).value().equals("(")) {
          Token open = values.get(index++);
          var args = new ArrayList<FrontendNode>();
          while (index < values.size() && !values.get(index).value().equals(")")) {
            args.add(expression(1));
            if (index < values.size() && values.get(index).value().equals(",")) index++;
            else break;
          }
          Token end = index < values.size() ? values.get(index++) : open;
          base =
              node(
                  "CallExpression",
                  "",
                  new TextRange(
                      OffsetUnit.RAW_UTF16, base.range().startOffset(), end.rawRange().endOffset()),
                  combine(base, args));
        } else break;
      }
      return base;
    }

    FrontendNode primary() {
      if (index >= values.size())
        return node("ErrorNode", "", values.getLast().rawRange(), List.of());
      Token t = values.get(index++);
      if (t.value().equals("new")) {
        FrontendNode target = primary();
        return node("NewExpression", "", span(t, tokenEnd(target)), List.of(target));
      }
      if (t.value().equals("(")) {
        int saved = index;
        if (index + 1 < values.size()
            && isIdentifierLike(values.get(index))
            && values.get(index + 1).value().equals(")")) {
          Token type = values.get(index);
          index += 2;
          FrontendNode value = unary();
          return node("CastExpression", "", span(t, tokenEnd(value)), List.of(value));
        }
        index = saved;
        FrontendNode nested = expression(1);
        if (index < values.size() && values.get(index).value().equals(")")) index++;
        return nested;
      }
      String kind =
          t.value().equals("null")
              ? "NullLiteral"
              : t.kind() == TokenKind.LITERAL
                      && (t.value().startsWith("\"") || t.value().startsWith("'"))
                  ? "StringLiteral"
                  : t.kind() == TokenKind.LITERAL ? "NumericLiteral" : "NameExpression";
      return node(kind, t.kind() == TokenKind.IDENTIFIER ? t.value() : "", t.rawRange(), List.of());
    }

    int precedence(String op) {
      return switch (op) {
        case "=" -> 1;
        case "||" -> 2;
        case "&&" -> 3;
        case "==", "!=" -> 4;
        case "<", ">", "<=", ">=" -> 5;
        case "+", "-" -> 6;
        case "*", "/", "%" -> 7;
        default -> -1;
      };
    }

    Token tokenEnd(FrontendNode n) {
      return values.stream()
          .filter(t -> t.rawRange().endOffset() == n.range().endOffset())
          .findFirst()
          .orElse(values.get(Math.max(0, index - 1)));
    }

    List<FrontendNode> combine(FrontendNode first, List<FrontendNode> rest) {
      var all = new ArrayList<FrontendNode>();
      all.add(first);
      all.addAll(rest);
      return all;
    }
  }

  private FrontendNode unsupported(String feature) {
    Token start = take();
    error(start, "JV-DEV-0001", "unsupported " + feature);
    int depth = 0;
    while (!at(TokenKind.EOF)) {
      Token t = take();
      if (t.value().equals("{") || t.value().equals("(")) depth++;
      else if (t.value().equals("}") || t.value().equals(")")) {
        if (depth == 0) break;
        depth--;
      }
      if (depth == 0 && atLineBoundary()) break;
    }
    TextRange range = span(start, previous());
    return new UnsupportedSyntaxNode(
        node("UnsupportedSyntaxNode", range), Optional.empty(), feature, range, List.of());
  }

  private void semicolon(Token t) {
    var fix =
        new Fix(
            "remove-semicolon",
            "Remove semicolon",
            FixKind.QUICK_FIX,
            List.of(new TextEdit(source.id(), t.rawRange(), "")));
    diagnostics.add(
        new Diagnostic(
            1,
            new DiagnosticCode("JV-SYN-0001"),
            Severity.ERROR,
            source.id(),
            t.rawRange(),
            "Javelle syntax does not allow semicolons",
            List.of(fix),
            Map.of()));
    recovered = true;
  }

  private void error(Token t, String code, String message) {
    if (diagnostics.size() < options.maxDiagnostics()
        && diagnostics.stream()
            .noneMatch(d -> d.code().value().equals(code) && d.range().equals(t.rawRange())))
      diagnostics.add(
          new Diagnostic(
              1,
              new DiagnosticCode(code),
              Severity.ERROR,
              source.id(),
              t.rawRange(),
              message,
              List.of(),
              Map.of()));
    recovered = true;
  }

  private SyntaxNode scanLine(String kind) {
    Token start = take();
    while (!atLineBoundary()) take();
    return node(kind, "", span(start, previous()), List.of());
  }

  private SyntaxNode node(String kind, String name, TextRange range, List<FrontendNode> children) {
    resources.node();
    NodeId id = node(kind, range);
    var reparented = children.stream().map(child -> withParent(child, id)).toList();
    return new SyntaxNode(id, Optional.empty(), kind, name, range, reparented);
  }

  private FrontendNode withParent(FrontendNode child, NodeId parent) {
    if (child instanceof SyntaxNode s)
      return new SyntaxNode(
          s.id(), Optional.of(parent), s.kind(), s.name(), s.range(), s.children());
    if (child instanceof AccessorNode a)
      return new AccessorNode(
          a.id(), Optional.of(parent), a.kind(), a.name(), a.visibility(), a.range(), a.children());
    return new UnsupportedSyntaxNode(
        child.id(), Optional.of(parent), child.name(), child.range(), child.children());
  }

  private NodeId node(String kind, TextRange r) {
    return new NodeId(source.id(), kind, r, ordinal++);
  }

  private void skipLines() {
    while (at(TokenKind.NEWLINE) || at(TokenKind.SEMICOLON)) {
      if (at(TokenKind.SEMICOLON)) semicolon(take());
      else take();
    }
  }

  private void sync() {
    while (!atLineBoundary() && !word("}")) take();
  }

  private void syncLine() {
    while (!atLineBoundary() && !word("}")) {
      if (at(TokenKind.SEMICOLON)) semicolon(take());
      else take();
    }
  }

  private void balancedUntil(String close) {
    int d = 0;
    while (!at(TokenKind.EOF)) {
      if (word(close) && d == 0) {
        take();
        return;
      }
      Token t = take();
      if (t.value().equals("(")) d++;
      else if (t.value().equals(")")) d--;
    }
  }

  private List<Token> takeUntilCloseParen() {
    var result = new ArrayList<Token>();
    int depth = 0;
    while (!at(TokenKind.EOF)) {
      if (word(")") && depth == 0) {
        take();
        return result;
      }
      Token token = take();
      if (token.value().equals("(")) depth++;
      else if (token.value().equals(")")) depth--;
      result.add(token);
    }
    return result;
  }

  private void balancedIf(String open, String close) {
    if (accept(open)) balancedUntil(close);
  }

  private List<Token> takeBalanced(String open, String close) {
    var result = new ArrayList<Token>();
    if (!accept(open)) return result;
    int depth = 0;
    while (!at(TokenKind.EOF)) {
      if (word(close) && depth == 0) {
        take();
        return result;
      }
      Token token = take();
      if (token.value().equals(open)) depth++;
      else if (token.value().equals(close)) depth--;
      result.add(token);
    }
    return result;
  }

  private boolean findWordAhead(String w, int n) {
    for (int i = p; i < tokens.size() && i < p + n; i++)
      if (tokens.get(i).value().equals(w)) return true;
    return false;
  }

  private boolean lookIdentifier(int n) {
    return p + n < tokens.size() && isIdentifierLike(tokens.get(p + n));
  }

  private boolean isModifier() {
    return Set.of("public", "protected", "private", "static", "final", "abstract")
        .contains(current().value());
  }

  private boolean isIdentifierLike() {
    return isIdentifierLike(current());
  }

  private static final Set<String> PRIMITIVE_TYPE_KEYWORDS =
      Set.of("boolean", "byte", "char", "double", "float", "int", "long", "short", "void");

  private boolean isIdentifierLike(Token t) {
    return t.kind() == TokenKind.IDENTIFIER
        || t.kind() == TokenKind.KEYWORD
            && (PRIMITIVE_TYPE_KEYWORDS.contains(t.value())
                || Set.of("var", "val", "get", "set", "field", "value").contains(t.value()));
  }

  private String identifier() {
    return isIdentifierLike() ? take().value() : "<missing>";
  }

  private boolean accept(String v) {
    if (word(v)) {
      take();
      return true;
    }
    return false;
  }

  private boolean word(String v) {
    return current().value().equals(v);
  }

  private boolean at(TokenKind k) {
    return current().kind() == k;
  }

  private boolean atLineBoundary() {
    return at(TokenKind.NEWLINE) || at(TokenKind.EOF) || word("}");
  }

  private Token current() {
    return tokens.get(Math.min(p, tokens.size() - 1));
  }

  private Token previous() {
    return tokens.get(Math.max(0, p - 1));
  }

  private Token take() {
    resources.checkpoint();
    if (p < tokens.size() - 1) p++;
    return tokens.get(p - 1);
  }

  private TextRange span(Token a, Token b) {
    return new TextRange(
        OffsetUnit.RAW_UTF16, a.rawRange().startOffset(), b.rawRange().endOffset());
  }
}
