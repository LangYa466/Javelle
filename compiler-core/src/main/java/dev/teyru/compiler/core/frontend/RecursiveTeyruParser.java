package dev.teyru.compiler.core.frontend;

import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.diagnostic.*;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.syntax.*;

public final class RecursiveTeyruParser implements TeyruParser {
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
      if (word("package")) {
        children.add(parsePackageDeclaration());
      } else if (word("import")) {
        children.add(parseImportDeclaration());
      } else {
        var modifiers = parseTopLevelModifiers();
        if (word("open") && lookWord(1, "module")) {
          Token start = take();
          take();
          children.add(parseModule(start, true));
        } else if (word("module")) {
          Token start = take();
          children.add(parseModule(start, false));
        } else if (word("open")) {
          children.add(unsupported("module-declaration"));
        } else if (word("@") && lookWord(1, "interface")) {
          take();
          Token start = take();
          String name = identifier();
          children.add(parseAnnotationType(start, name, modifiers));
        } else if (word("record")) {
          Token start = take();
          String name = identifier();
          children.add(parseRecord(start, name, modifiers));
        } else if (word("interface")) {
          Token start = take();
          String name = identifier();
          children.add(parseInterface(start, name, modifiers));
        } else if (word("enum")) {
          Token start = take();
          String name = identifier();
          children.add(parseEnum(start, name, modifiers));
        } else if (word("class")) {
          Token start = take();
          String name = identifier();
          children.add(parseClass(start, name, modifiers));
        } else {
          error(current(), "TY-SYN-0002", "expected declaration");
          take();
        }
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

  private FrontendNode parseClass(Token start, String name, List<FrontendNode> modifiers) {
    validateModifierCombinations(modifiers, "class");
    var members = new ArrayList<FrontendNode>(modifiers);
    members.addAll(parseSuperclassClause());
    members.addAll(parseTypeReferenceClause("implements", "SuperinterfaceDeclaration"));
    members.addAll(parsePermitsClause());
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing class body");
      return node("ClassDeclaration", name, start.rawRange(), members);
    }
    members.addAll(parseClassBodyMembers(Optional.of(name)));
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated class");
    return node("ClassDeclaration", name, span(start, end), members);
  }

  private static final Set<String> TOP_LEVEL_MODIFIERS =
      Set.of("public", "protected", "private", "static", "final", "abstract", "sealed");

  /**
   * Captures {@code public}/{@code private}/.../{@code sealed} and the two-word {@code non-sealed}
   * modifier (lexed as three tokens: {@code non}, {@code -}, {@code sealed}, since the lexer has no
   * single-token support for it yet) as {@code ModifierDeclaration} nodes.
   */
  private List<FrontendNode> parseTopLevelModifiers() {
    var modifiers = new ArrayList<FrontendNode>();
    while (true) {
      if (word("non") && lookWord(1, "-") && lookWord(2, "sealed")) {
        Token start = take();
        take();
        Token end = take();
        modifiers.add(node("ModifierDeclaration", "non-sealed", span(start, end), List.of()));
      } else if (TOP_LEVEL_MODIFIERS.contains(current().value())) {
        Token m = take();
        modifiers.add(node("ModifierDeclaration", m.value(), m.rawRange(), List.of()));
      } else break;
    }
    return modifiers;
  }

  /** {@code permits Name1, Name2, ...} — only meaningful after a sealed class/interface header. */
  private List<FrontendNode> parsePermitsClause() {
    return parseTypeReferenceClause("permits", "PermittedSubtypeDeclaration");
  }

  /** {@code extends Superclass} — a class has at most one superclass. */
  private List<FrontendNode> parseSuperclassClause() {
    var result = new ArrayList<FrontendNode>();
    if (!accept("extends")) return result;
    result.add(parseTypeReference("SuperclassDeclaration"));
    return result;
  }

  /**
   * A comma-separated list of type references introduced by {@code keyword}: {@code extends}
   * (interface superinterfaces, one or more), {@code implements} (class/record/enum
   * superinterfaces), or {@code permits} (sealed-type permitted subtypes).
   */
  private List<FrontendNode> parseTypeReferenceClause(String keyword, String nodeKind) {
    var result = new ArrayList<FrontendNode>();
    if (!accept(keyword)) return result;
    do {
      result.add(parseTypeReference(nodeKind));
    } while (accept(","));
    return result;
  }

  /**
   * A (possibly dotted) type name, e.g. {@code pkg.Type}. Generic type arguments after it are
   * scanned and dropped with an unsupported-feature diagnostic — full generics are P13-03.
   */
  private FrontendNode parseTypeReference(String nodeKind) {
    if (!isIdentifierLike()) {
      error(current(), "TY-SYN-0002", "expected type name");
      return node(nodeKind, "<missing>", current().rawRange(), List.of());
    }
    Token first = take();
    Token last = first;
    var name = new StringBuilder(first.value());
    while (word(".") && lookIdentifier(1)) {
      take();
      Token part = take();
      name.append('.').append(part.value());
      last = part;
    }
    if (word("<")) {
      Token angle = take();
      error(angle, "TY-DEV-0001", "unsupported generic type arguments");
      int depth = 1;
      while (depth > 0 && !at(TokenKind.EOF)) {
        Token t = take();
        if (t.value().equals("<")) depth++;
        else if (t.value().equals(">")) depth--;
        last = t;
      }
    }
    return node(nodeKind, name.toString(), span(first, last), List.of());
  }

  /** Parses class/enum-member-section members up to (not including) the closing {@code }}. */
  private List<FrontendNode> parseClassBodyMembers() {
    return parseClassBodyMembers(Optional.empty());
  }

  /**
   * @param enclosingName when present, a member spelled {@code Name(...)} (no return type,
   *     identical to the enclosing type name) is parsed as a constructor rather than requiring —
   *     and failing to find — a separate member name after it.
   */
  private List<FrontendNode> parseClassBodyMembers(Optional<String> enclosingName) {
    var members = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      int before = p;
      boolean sawStatic = false;
      for (; isModifier(); ) {
        if (word("static")) sawStatic = true;
        take();
      }
      if (word("var") || word("val")) {
        Token bad = take();
        error(bad, "TY-TYP-0003", "inferred field type is illegal");
        sync();
        members.add(node("ErrorNode", "", bad.rawRange(), List.of()));
      } else if (word("{")) {
        members.add(parseInitializerBlock(sawStatic));
      } else if (word("class")) {
        Token nestedStart = take();
        String nestedName = identifier();
        members.add(parseClass(nestedStart, nestedName, List.of()));
      } else if (word("interface")) {
        Token nestedStart = take();
        String nestedName = identifier();
        members.add(parseInterface(nestedStart, nestedName, List.of()));
      } else if (word("enum")) {
        Token nestedStart = take();
        String nestedName = identifier();
        members.add(parseEnum(nestedStart, nestedName, List.of()));
      } else if (word("record") && lookIdentifier(1) && lookIsOpenParen(2)) {
        Token nestedStart = take();
        String nestedName = identifier();
        members.add(parseRecord(nestedStart, nestedName, List.of()));
      } else if (word("@") && lookWord(1, "interface")) {
        take();
        Token nestedStart = take();
        String nestedName = identifier();
        members.add(parseAnnotationType(nestedStart, nestedName, List.of()));
      } else if (enclosingName.isPresent() && word(enclosingName.get()) && lookIsOpenParen(1)) {
        Token ctor = take();
        accept("(");
        members.add(parseConstructor(ctor));
      } else if (isIdentifierLike()) {
        Token type = take();
        if (!isIdentifierLike()) {
          error(current(), "TY-SYN-0002", "missing member name");
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
        error(current(), "TY-SYN-0002", "unsupported member");
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
  private FrontendNode parseEnum(Token start, String name, List<FrontendNode> modifiers) {
    validateModifierCombinations(modifiers, "enum");
    var members = new ArrayList<FrontendNode>(modifiers);
    members.addAll(parseTypeReferenceClause("implements", "SuperinterfaceDeclaration"));
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing enum body");
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
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated enum");
    return node("EnumDeclaration", name, span(start, end), members);
  }

  /**
   * Interface members are implicitly public; abstract methods have no body (bodiless, terminated by
   * newline per JLS interface-method-declaration grammar); {@code default}/{@code static} methods
   * require a real body; fields are implicitly public/static/final constants and require an
   * initializer.
   */
  private FrontendNode parseInterface(Token start, String name, List<FrontendNode> modifiers) {
    validateModifierCombinations(modifiers, "interface");
    var members = new ArrayList<FrontendNode>(modifiers);
    members.addAll(parseTypeReferenceClause("extends", "SuperinterfaceDeclaration"));
    members.addAll(parsePermitsClause());
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing interface body");
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
          error(current(), "TY-SYN-0002", "missing member name");
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
            error(member, "TY-SYN-0002", "interface constant requires an initializer");
            members.add(node("ErrorNode", "", span(type, member), List.of()));
          } else
            members.add(parseField(type, member, Optional.of(buildExpression(initializerTokens))));
        } else {
          error(current(), "TY-SYN-0002", "interface constant requires an initializer");
          sync();
        }
      } else {
        error(current(), "TY-SYN-0002", "unsupported member");
        sync();
      }
      if (before == p) take();
    }
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated interface");
    return node("InterfaceDeclaration", name, span(start, end), members);
  }

  /**
   * An annotation type element is spelled {@code Type name()} (never any parameters), optionally
   * followed by {@code default <value>}; a member spelled {@code Type NAME = value} is a constant,
   * same as in a plain interface. Nested annotation types and array-literal default values are not
   * yet supported.
   */
  private FrontendNode parseAnnotationType(Token start, String name, List<FrontendNode> modifiers) {
    validateModifierCombinations(modifiers, "annotation");
    var members = new ArrayList<FrontendNode>(modifiers);
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing annotation type body");
      return node("AnnotationTypeDeclaration", name, start.rawRange(), members);
    }
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      int before = p;
      for (; isModifier(); take()) {}
      if (isIdentifierLike()) {
        Token type = take();
        if (!isIdentifierLike()) {
          error(current(), "TY-SYN-0002", "missing member name");
          sync();
          continue;
        }
        Token member = take();
        if (accept("(")) {
          var parameterTokens = takeUntilCloseParen();
          if (!parameterTokens.isEmpty())
            error(member, "TY-SYN-0002", "annotation elements cannot have parameters");
          var kids = new ArrayList<FrontendNode>();
          if (accept("default")) {
            var defaultTokens = new ArrayList<Token>();
            while (!at(TokenKind.EOF) && !at(TokenKind.NEWLINE)) {
              if (at(TokenKind.SEMICOLON)) semicolon(take());
              else defaultTokens.add(take());
            }
            if (!defaultTokens.isEmpty()) kids.add(buildExpression(defaultTokens));
          }
          members.add(
              node("AnnotationElementDeclaration", member.value(), span(type, previous()), kids));
        } else if (accept("=")) {
          var initializerTokens = new ArrayList<Token>();
          while (!at(TokenKind.EOF) && !at(TokenKind.NEWLINE)) {
            if (at(TokenKind.SEMICOLON)) semicolon(take());
            else initializerTokens.add(take());
          }
          if (initializerTokens.isEmpty()) {
            error(member, "TY-SYN-0002", "annotation constant requires an initializer");
            members.add(node("ErrorNode", "", span(type, member), List.of()));
          } else
            members.add(parseField(type, member, Optional.of(buildExpression(initializerTokens))));
        } else {
          error(current(), "TY-SYN-0002", "expected annotation element or constant");
          sync();
        }
      } else {
        error(current(), "TY-SYN-0002", "unsupported member");
        sync();
      }
      if (before == p) take();
    }
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated annotation type");
    return node("AnnotationTypeDeclaration", name, span(start, end), members);
  }

  /** {@code static { ... }} or {@code { ... }} — a class initializer block with no name. */
  private FrontendNode parseInitializerBlock(boolean isStatic) {
    Token brace = take();
    var children = new ArrayList<FrontendNode>();
    if (isStatic) children.add(node("StaticModifier", "static", brace.rawRange(), List.of()));
    children.addAll(parseStatements());
    return node("InitializerBlockDeclaration", "", span(brace, previous()), children);
  }

  /** A constructor is spelled {@code Name(...)} with no return type; it always requires a body. */
  private FrontendNode parseConstructor(Token name) {
    var parameterTokens = takeUntilCloseParen();
    var methodChildren = parseCallableParameters(parameterTokens);
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing constructor body");
      return node("ConstructorDeclaration", name.value(), name.rawRange(), methodChildren);
    }
    var statements = parseStatements();
    validateExplicitConstructorInvocations(statements, true);
    methodChildren.addAll(statements);
    return node("ConstructorDeclaration", name.value(), span(name, previous()), methodChildren);
  }

  private FrontendNode parseMethod(Token type, Token name) {
    return parseMethod(type, name, true);
  }

  private FrontendNode parseMethod(Token type, Token name, boolean bodyRequired) {
    var parameterTokens = takeUntilCloseParen();
    var methodChildren = parseCallableParameters(parameterTokens);
    if (!accept("{")) {
      if (!bodyRequired && (at(TokenKind.NEWLINE) || at(TokenKind.EOF) || word("}")))
        return node("MethodDeclaration", name.value(), span(type, name), methodChildren);
      error(current(), "TY-SYN-0002", "missing method body");
      return node("MethodDeclaration", name.value(), span(type, name), methodChildren);
    }
    var statements = parseStatements();
    validateExplicitConstructorInvocations(statements, false);
    methodChildren.addAll(statements);
    return node("MethodDeclaration", name.value(), span(type, previous()), methodChildren);
  }

  /**
   * JLS 8.8.7.1: an explicit constructor invocation ({@code this(...)} or {@code super(...)}) may
   * appear only as the first statement of a constructor body — never in a regular method, and never
   * after any other statement in a constructor.
   */
  private void validateExplicitConstructorInvocations(
      List<FrontendNode> statements, boolean isConstructor) {
    for (int i = 0; i < statements.size(); i++) {
      if (!isExplicitConstructorInvocation(statements.get(i))) continue;
      if (!isConstructor)
        error(
            statements.get(i).range(),
            "TY-SYN-0002",
            "this()/super() call is only allowed as the first statement of a constructor");
      else if (i != 0)
        error(
            statements.get(i).range(),
            "TY-SYN-0002",
            "this()/super() call must be the first statement in a constructor");
    }
  }

  private boolean isExplicitConstructorInvocation(FrontendNode statement) {
    if (!statement.kind().equals("ExpressionStatement") || statement.children().isEmpty())
      return false;
    FrontendNode expr = statement.children().getFirst();
    if (!expr.kind().equals("CallExpression") || expr.children().isEmpty()) return false;
    String baseKind = expr.children().getFirst().kind();
    return baseKind.equals("ThisExpression") || baseKind.equals("SuperExpression");
  }

  /**
   * A record's header {@code (components)} is structurally identical to a parameter list; its
   * canonical constructor is parsed like any other constructor via the existing {@code Name(...)}
   * detection in {@link #parseClassBodyMembers(Optional)}. Compact constructors ({@code Name { ...
   * }}, no parentheses) are not yet supported.
   */
  private FrontendNode parseRecord(Token start, String name, List<FrontendNode> modifiers) {
    validateModifierCombinations(modifiers, "record");
    if (!accept("(")) {
      error(current(), "TY-SYN-0002", "missing record header");
      return node("RecordDeclaration", name, start.rawRange(), modifiers);
    }
    var componentTokens = takeUntilCloseParen();
    var members = new ArrayList<FrontendNode>(modifiers);
    members.addAll(parseCallableParameters(componentTokens, "RecordComponentDeclaration"));
    members.addAll(parseTypeReferenceClause("implements", "SuperinterfaceDeclaration"));
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing record body");
      return node("RecordDeclaration", name, span(start, previous()), members);
    }
    members.addAll(parseClassBodyMembers(Optional.of(name)));
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated record");
    return node("RecordDeclaration", name, span(start, end), members);
  }

  /** Shared varargs-rejection + parameter-list parsing for methods and constructors alike. */
  private ArrayList<FrontendNode> parseCallableParameters(List<Token> parameterTokens) {
    return parseCallableParameters(parameterTokens, "ParameterDeclaration");
  }

  private ArrayList<FrontendNode> parseCallableParameters(
      List<Token> parameterTokens, String nodeKind) {
    var children = new ArrayList<FrontendNode>();
    Optional<Token> varargs =
        parameterTokens.stream().filter(token -> token.value().equals("...")).findFirst();
    if (varargs.isPresent()) {
      Token introducing = varargs.orElseThrow();
      error(introducing, "TY-DEV-0001", "unsupported varargs");
      TextRange unsupportedRange =
          parameterTokens.isEmpty()
              ? introducing.rawRange()
              : span(parameterTokens.getFirst(), parameterTokens.getLast());
      children.add(
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
        error(parameterType, "TY-SYN-0002", "invalid parameter");
        break;
      }
      children.add(
          node(nodeKind, parameterName.value(), span(parameterType, parameterName), List.of()));
      if (i < parameterTokens.size() && parameterTokens.get(i).value().equals(",")) i++;
      else if (i < parameterTokens.size()) {
        error(parameterTokens.get(i), "TY-SYN-0002", "expected parameter comma");
        break;
      }
    }
    return children;
  }

  /** A {@code { ... }} block, or (if/while/do bodies without braces) a single statement. */
  private FrontendNode parseStatementOrBlock() {
    if (accept("{")) {
      Token blockStart = previous();
      var statements = parseStatements();
      return node("Block", "", span(blockStart, previous()), statements);
    }
    skipLines();
    return parseExpressionUntilBoundary();
  }

  private List<FrontendNode> parseStatements() {
    var out = parseStatementsUntil(() -> word("}"));
    if (!accept("}")) {
      error(current(), "TY-SYN-0002", "unterminated block");
      out.add(node("ErrorNode", "", current().rawRange(), List.of()));
    }
    return out;
  }

  /**
   * {@code case}/{@code default} colon-bodies run until the next case label or the switch's closing
   * {@code }} — never their own closing brace, since a case body isn't itself a block.
   */
  private List<FrontendNode> parseSwitchCaseBody() {
    return parseStatementsUntil(() -> word("}") || word("case") || word("default"));
  }

  /**
   * Both {@code case value1, value2 -> ...} (arrow, no fallthrough, single statement/block) and
   * {@code case value:} (colon, classic fallthrough, statements run until the next label). Guards
   * ({@code when}) and deconstruction patterns are not yet supported — case labels are plain
   * expressions.
   */
  private List<FrontendNode> parseSwitchCases() {
    var cases = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      if (!word("case") && !word("default")) {
        error(current(), "TY-SYN-0002", "expected case or default");
        sync();
        continue;
      }
      Token labelStart = take();
      boolean isDefault = labelStart.value().equals("default");
      var labels = new ArrayList<FrontendNode>();
      if (!isDefault) {
        do {
          var labelTokens = new ArrayList<Token>();
          while (!at(TokenKind.EOF) && !word(",") && !word(":") && !word("->"))
            labelTokens.add(take());
          if (!labelTokens.isEmpty()) labels.add(buildExpression(labelTokens));
        } while (accept(","));
      }
      boolean isArrow = accept("->");
      if (!isArrow && !accept(":"))
        error(current(), "TY-SYN-0002", "expected : or -> in switch case");
      var body = new ArrayList<FrontendNode>();
      if (isArrow) {
        if (accept("{")) {
          Token blockStart = previous();
          var statements = parseStatements();
          body.add(node("Block", "", span(blockStart, previous()), statements));
        } else {
          var expr = parseExpressionUntilBoundary();
          if (expr != null) body.add(node("ExpressionStatement", "", expr.range(), List.of(expr)));
        }
      } else {
        body.addAll(parseSwitchCaseBody());
      }
      var children = new ArrayList<FrontendNode>(labels);
      children.addAll(body);
      cases.add(
          node(
              isDefault ? "SwitchDefaultCase" : "SwitchCase",
              isArrow ? "arrow" : "colon",
              span(labelStart, previous()),
              children));
    }
    if (!accept("}")) error(current(), "TY-SYN-0002", "unterminated switch");
    return cases;
  }

  private List<FrontendNode> parseStatementsUntil(java.util.function.Supplier<Boolean> stop) {
    var out = new ArrayList<FrontendNode>();
    while (!at(TokenKind.EOF) && !stop.get()) {
      skipLines();
      if (stop.get()) break;
      if (looksLikeLocalTypeDeclaration()) {
        while (isModifier()) take();
        if (word("class")) {
          Token s = take();
          String n = identifier();
          out.add(parseClass(s, n, List.of()));
        } else if (word("interface")) {
          Token s = take();
          String n = identifier();
          out.add(parseInterface(s, n, List.of()));
        } else if (word("enum")) {
          Token s = take();
          String n = identifier();
          out.add(parseEnum(s, n, List.of()));
        } else if (word("record")) {
          Token s = take();
          String n = identifier();
          out.add(parseRecord(s, n, List.of()));
        } else {
          take();
          Token s = take();
          String n = identifier();
          out.add(parseAnnotationType(s, n, List.of()));
        }
        continue;
      }
      if (word("for")) {
        if (looksLikeEnhancedFor()) {
          Token start = take();
          out.add(parseEnhancedFor(start));
        } else if (looksLikeBasicFor()) {
          Token start = take();
          out.add(parseBasicFor(start));
        } else {
          out.add(unsupported("basic-for"));
        }
        continue;
      }
      if (word("while")) {
        Token start = take();
        var conditionTokens = takeBalanced("(", ")");
        var kids = new ArrayList<FrontendNode>();
        if (!conditionTokens.isEmpty()) kids.add(buildExpression(conditionTokens));
        else error(start, "TY-SYN-0002", "missing while condition");
        var whileBody = parseStatementOrBlock();
        if (whileBody != null) kids.add(whileBody);
        else error(current(), "TY-SYN-0002", "missing while body");
        out.add(node("WhileStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("do")) {
        Token start = take();
        var kids = new ArrayList<FrontendNode>();
        var doBody = parseStatementOrBlock();
        if (doBody != null) kids.add(doBody);
        else error(current(), "TY-SYN-0002", "missing do body");
        skipLines();
        if (!accept("while")) {
          error(current(), "TY-SYN-0002", "expected while after do body");
        } else {
          var conditionTokens = takeBalanced("(", ")");
          if (!conditionTokens.isEmpty()) kids.add(buildExpression(conditionTokens));
          else error(previous(), "TY-SYN-0002", "missing do-while condition");
        }
        out.add(node("DoWhileStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("break") || word("continue")) {
        Token start = take();
        String label = !atLineBoundary() && isIdentifierLike() ? take().value() : "";
        out.add(
            node(
                start.value().equals("break") ? "BreakStatement" : "ContinueStatement",
                label,
                span(start, previous()),
                List.of()));
        continue;
      }
      if (word("switch")) {
        Token start = take();
        var subjectTokens = takeBalanced("(", ")");
        var kids = new ArrayList<FrontendNode>();
        if (!subjectTokens.isEmpty()) kids.add(buildExpression(subjectTokens));
        else error(start, "TY-SYN-0002", "missing switch subject");
        if (!accept("{")) error(current(), "TY-SYN-0002", "missing switch body");
        else kids.addAll(parseSwitchCases());
        out.add(node("SwitchStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("try")) {
        Token start = take();
        var kids = new ArrayList<FrontendNode>();
        if (accept("(")) kids.addAll(parseResources());
        var tryBody = parseStatementOrBlock();
        if (tryBody != null) kids.add(tryBody);
        else error(current(), "TY-SYN-0002", "missing try body");
        skipLines();
        while (word("catch")) {
          kids.add(parseCatchClause());
          skipLines();
        }
        if (accept("finally")) {
          Token finallyStart = previous();
          var finallyBody = parseStatementOrBlock();
          if (finallyBody != null)
            kids.add(
                node("FinallyBlock", "", span(finallyStart, previous()), List.of(finallyBody)));
          else error(current(), "TY-SYN-0002", "missing finally body");
        }
        out.add(node("TryStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("synchronized")) {
        Token start = take();
        var subjectTokens = takeBalanced("(", ")");
        var kids = new ArrayList<FrontendNode>();
        if (!subjectTokens.isEmpty()) kids.add(buildExpression(subjectTokens));
        else error(start, "TY-SYN-0002", "missing synchronized subject");
        if (accept("{")) {
          Token blockStart = previous();
          var statements = parseStatements();
          kids.add(node("Block", "", span(blockStart, previous()), statements));
        } else {
          error(current(), "TY-SYN-0002", "missing synchronized body");
        }
        out.add(node("SynchronizedStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("return")) {
        Token start = take();
        var expr = parseExpressionUntilBoundary();
        if (expr == null && at(TokenKind.EOF)) {
          error(start, "TY-SYN-0002", "missing return expression");
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
      if (word("throw")) {
        Token start = take();
        var expr = parseExpressionUntilBoundary();
        if (expr == null) {
          error(start, "TY-SYN-0002", "missing throw expression");
          out.add(node("ErrorNode", "", start.rawRange(), List.of()));
        } else out.add(node("ThrowStatement", "", span(start, previous()), List.of(expr)));
        continue;
      }
      if (word("yield") && looksLikeYieldStatement()) {
        Token start = take();
        var expr = parseExpressionUntilBoundary();
        if (expr == null) {
          error(start, "TY-SYN-0002", "missing yield expression");
          out.add(node("ErrorNode", "", start.rawRange(), List.of()));
        } else out.add(node("YieldStatement", "", span(start, previous()), List.of(expr)));
        continue;
      }
      if (word("assert")) {
        Token start = take();
        var conditionTokens = new ArrayList<Token>();
        while (!at(TokenKind.EOF) && !atLineBoundary() && !word(":")) {
          if (at(TokenKind.SEMICOLON)) semicolon(take());
          else conditionTokens.add(take());
        }
        var kids = new ArrayList<FrontendNode>();
        if (conditionTokens.isEmpty()) error(start, "TY-SYN-0002", "missing assert condition");
        else kids.add(buildExpression(conditionTokens));
        if (accept(":")) {
          var message = parseExpressionUntilBoundary();
          if (message != null) kids.add(message);
        }
        out.add(node("AssertStatement", "", span(start, previous()), kids));
        continue;
      }
      if (word("if")) {
        Token start = take();
        var conditionTokens = takeBalanced("(", ")");
        var kids = new ArrayList<FrontendNode>();
        if (!conditionTokens.isEmpty()) kids.add(buildExpression(conditionTokens));
        else error(start, "TY-SYN-0002", "missing if condition");
        var thenBranch = parseStatementOrBlock();
        if (thenBranch != null) kids.add(thenBranch);
        skipLines();
        if (accept("else")) {
          var elseBranch = parseStatementOrBlock();
          if (elseBranch != null) kids.add(elseBranch);
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
            error(previous(), "TY-SYN-0002", "missing initializer expression");
            kids.add(node("ErrorNode", "", previous().rawRange(), List.of()));
          }
          if ((type.value().equals("var") || type.value().equals("val"))
              && expr != null
              && expr.kind().equals("NullLiteral"))
            error(previous(), "TY-TYP-0002", "cannot infer null");
        } else if (type.value().equals("var") || type.value().equals("val"))
          error(name, "TY-SYN-0002", "initializer required");
        out.add(node("LocalVariableDeclaration", name.value(), span(type, previous()), kids));
        continue;
      }
      var expr = parseExpressionUntilBoundary();
      if (expr != null) out.add(node("ExpressionStatement", "", expr.range(), List.of(expr)));
      else take();
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
                      new DiagnosticCode("TY-PROP-0006"),
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
        error(current(), "TY-SYN-0002", "incomplete accessor");
        take();
      }
    }
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated property");
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
            .anyMatch(d -> d.code().value().equals("TY-SYN-0001"))) {
      var range = diagnostics.getLast().range();
      kids.add(node("ErrorNode", "", range, List.of()));
    }
    return node("FieldDeclaration", name.value(), span(type, previous()), kids);
  }

  private FrontendNode parseField(
      Token type, Token name, Optional<FrontendNode> parsedInitializer) {
    var kids = new ArrayList<FrontendNode>();
    parsedInitializer.ifPresent(kids::add);
    if (!diagnostics.isEmpty() && diagnostics.getLast().code().value().equals("TY-SYN-0001"))
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
        parts.stream().filter(t -> t.value().equals("...")).findFirst();
    if (unsupportedOperator.isPresent()) {
      Token token = unsupportedOperator.orElseThrow();
      TextRange range = span(parts.getFirst(), parts.getLast());
      error(token, "TY-DEV-0001", "unsupported varargs");
      return new UnsupportedSyntaxNode(
          node("UnsupportedSyntaxNode", range), Optional.empty(), "varargs", range, List.of());
    }
    if (parts.stream().anyMatch(t -> t.value().startsWith("\"\"\""))) {
      Token token =
          parts.stream().filter(t -> t.value().startsWith("\"\"\"")).findFirst().orElseThrow();
      error(token, "TY-DEV-0001", "unsupported text-block");
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

    private static final Set<String> ASSIGNMENT_OPERATORS =
        Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");

    FrontendNode expression(int minimum) {
      FrontendNode left = unary();
      while (index < values.size()) {
        String op = values.get(index).value();
        if (op.equals("instanceof")) {
          int prec = 8;
          if (prec < minimum) break;
          index++;
          left = instanceOfRest(left);
          continue;
        }
        int precedence = precedence(op);
        if (precedence < minimum) break;
        boolean isAssignment = ASSIGNMENT_OPERATORS.contains(op);
        Token operator = values.get(index++);
        int next = isAssignment ? precedence : precedence + 1;
        FrontendNode right = expression(next);
        left =
            node(
                isAssignment ? "AssignmentExpression" : "BinaryExpression",
                operator.value(),
                new TextRange(
                    OffsetUnit.RAW_UTF16, left.range().startOffset(), right.range().endOffset()),
                List.of(left, right));
      }
      if (index < values.size() && values.get(index).value().equals("?")) {
        Token question = values.get(index++);
        FrontendNode yes = expression(1);
        if (index >= values.size() || !values.get(index).value().equals(":")) {
          error(question, "TY-SYN-0002", "missing ternary colon");
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

    /**
     * {@code x instanceof Type} with an optional single pattern-binding identifier ({@code x
     * instanceof String s}). Record deconstruction patterns and generic type arguments on the
     * checked type are not yet supported — full pattern matching is a separate, larger feature.
     */
    FrontendNode instanceOfRest(FrontendNode left) {
      if (index >= values.size() || !isIdentifierLike(values.get(index))) {
        error(
            values.get(Math.min(index, values.size() - 1)),
            "TY-SYN-0002",
            "expected type after instanceof");
        return left;
      }
      Token type = values.get(index++);
      Token end = type;
      String binding = "";
      if (index < values.size() && isIdentifierLike(values.get(index))) {
        binding = values.get(index).value();
        end = values.get(index++);
      }
      return node(
          "InstanceOfExpression",
          binding,
          new TextRange(
              OffsetUnit.RAW_UTF16, left.range().startOffset(), end.rawRange().endOffset()),
          List.of(left));
    }

    FrontendNode unary() {
      if (index < values.size() && Set.of("!", "-", "+", "~").contains(values.get(index).value())) {
        Token op = values.get(index++);
        FrontendNode value = unary();
        return node("UnaryExpression", op.value(), span(op, tokenEnd(value)), List.of(value));
      }
      if (index < values.size() && Set.of("++", "--").contains(values.get(index).value())) {
        Token op = values.get(index++);
        FrontendNode value = unary();
        return node("PrefixExpression", op.value(), span(op, tokenEnd(value)), List.of(value));
      }
      return postfix(primary());
    }

    FrontendNode postfix(FrontendNode base) {
      while (index < values.size()) {
        if (Set.of("++", "--").contains(values.get(index).value())) {
          Token op = values.get(index++);
          base =
              node(
                  "PostfixExpression",
                  op.value(),
                  new TextRange(
                      OffsetUnit.RAW_UTF16, base.range().startOffset(), op.rawRange().endOffset()),
                  List.of(base));
        } else if (values.get(index).value().equals("::")) {
          Token op = values.get(index++);
          if (index >= values.size()) {
            error(op, "TY-SYN-0002", "missing method reference target");
            return base;
          }
          Token member = values.get(index++);
          base =
              node(
                  "MethodReferenceExpression",
                  member.value(),
                  new TextRange(
                      OffsetUnit.RAW_UTF16,
                      base.range().startOffset(),
                      member.rawRange().endOffset()),
                  List.of(base));
        } else if (values.get(index).value().equals(".")) {
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
      if (looksLikeLambda()) return lambda();
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
      if (t.value().equals("this")) return node("ThisExpression", "", t.rawRange(), List.of());
      if (t.value().equals("super")) return node("SuperExpression", "", t.rawRange(), List.of());
      String kind =
          t.value().equals("null")
              ? "NullLiteral"
              : t.kind() == TokenKind.LITERAL
                      && (t.value().startsWith("\"") || t.value().startsWith("'"))
                  ? "StringLiteral"
                  : t.kind() == TokenKind.LITERAL ? "NumericLiteral" : "NameExpression";
      return node(kind, t.kind() == TokenKind.IDENTIFIER ? t.value() : "", t.rawRange(), List.of());
    }

    /**
     * A lambda starts either at a bare identifier immediately followed by {@code ->} (implicit
     * single parameter, no parens), or at {@code (} whose matching {@code )} is immediately
     * followed by {@code ->}.
     */
    boolean looksLikeLambda() {
      if (index >= values.size()) return false;
      Token first = values.get(index);
      if (isIdentifierLike(first) && index + 1 < values.size())
        return values.get(index + 1).value().equals("->");
      if (!first.value().equals("(")) return false;
      int depth = 0;
      for (int i = index; i < values.size(); i++) {
        String v = values.get(i).value();
        if (v.equals("(")) depth++;
        else if (v.equals(")")) {
          depth--;
          if (depth == 0) return i + 1 < values.size() && values.get(i + 1).value().equals("->");
        }
      }
      return false;
    }

    /**
     * {@code (params) -> body} or {@code identifier -> body}. A block body ({@code -> { ... }}) is
     * diagnosed unsupported: the cursor operates over a single pre-sliced, single-line token
     * window, so a multi-line block body would need architecture beyond this round's scope.
     */
    FrontendNode lambda() {
      Token start = values.get(index);
      var params = lambdaParameters();
      if (index >= values.size() || !values.get(index).value().equals("->")) {
        error(start, "TY-SYN-0002", "expected -> in lambda");
        return node("ErrorNode", "", start.rawRange(), List.of());
      }
      index++;
      if (index < values.size() && values.get(index).value().equals("{")) {
        Token brace = values.get(index);
        int depth = 0;
        Token last = brace;
        while (index < values.size()) {
          Token tk = values.get(index++);
          last = tk;
          if (tk.value().equals("{")) depth++;
          else if (tk.value().equals("}")) {
            depth--;
            if (depth == 0) break;
          }
        }
        error(brace, "TY-DEV-0001", "unsupported block-bodied lambda");
        TextRange range = span(start, last);
        return new UnsupportedSyntaxNode(
            node("UnsupportedSyntaxNode", range),
            Optional.empty(),
            "block-lambda",
            range,
            List.of());
      }
      FrontendNode body = expression(1);
      var children = new ArrayList<FrontendNode>(params);
      children.add(body);
      return node(
          "LambdaExpression",
          "",
          new TextRange(
              OffsetUnit.RAW_UTF16, start.rawRange().startOffset(), body.range().endOffset()),
          children);
    }

    List<FrontendNode> lambdaParameters() {
      var params = new ArrayList<FrontendNode>();
      if (!values.get(index).value().equals("(")) {
        Token name = values.get(index++);
        params.add(node("LambdaParameterDeclaration", name.value(), name.rawRange(), List.of()));
        return params;
      }
      index++;
      while (index < values.size() && !values.get(index).value().equals(")")) {
        Token first = values.get(index++);
        if (index < values.size()
            && isIdentifierLike(values.get(index))
            && !values.get(index).value().equals(",")) {
          Token name = values.get(index++);
          params.add(
              node("LambdaParameterDeclaration", name.value(), span(first, name), List.of()));
        } else {
          params.add(
              node("LambdaParameterDeclaration", first.value(), first.rawRange(), List.of()));
        }
        if (index < values.size() && values.get(index).value().equals(",")) index++;
        else break;
      }
      if (index < values.size() && values.get(index).value().equals(")")) index++;
      return params;
    }

    int precedence(String op) {
      return switch (op) {
        case "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=" -> 1;
        case "||" -> 2;
        case "&&" -> 3;
        case "|" -> 4;
        case "^" -> 5;
        case "&" -> 6;
        case "==", "!=" -> 7;
        case "<", ">", "<=", ">=" -> 8;
        case "<<", ">>", ">>>" -> 9;
        case "+", "-" -> 10;
        case "*", "/", "%" -> 11;
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
    error(start, "TY-DEV-0001", "unsupported " + feature);
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
            new DiagnosticCode("TY-SYN-0001"),
            Severity.ERROR,
            source.id(),
            t.rawRange(),
            "Teyru syntax does not allow semicolons",
            List.of(fix),
            Map.of()));
    recovered = true;
  }

  private void error(Token t, String code, String message) {
    error(t.rawRange(), code, message);
  }

  private void error(TextRange range, String code, String message) {
    if (diagnostics.size() < options.maxDiagnostics()
        && diagnostics.stream()
            .noneMatch(d -> d.code().value().equals(code) && d.range().equals(range)))
      diagnostics.add(
          new Diagnostic(
              1,
              new DiagnosticCode(code),
              Severity.ERROR,
              source.id(),
              range,
              message,
              List.of(),
              Map.of()));
    recovered = true;
  }

  /**
   * JLS 8.1.1/8.9.1/8.10 modifier-combination rules that don't need any semantic (whole-program)
   * information: {@code final}+{@code abstract} on a class, {@code final} on an interface or
   * annotation type, and {@code abstract}/{@code final} on an enum or record (both implicitly
   * final, and neither can be abstract).
   */
  private void validateModifierCombinations(List<FrontendNode> modifiers, String declarationKind) {
    boolean hasFinal = hasModifier(modifiers, "final");
    boolean hasAbstract = hasModifier(modifiers, "abstract");
    boolean hasSealed = hasModifier(modifiers, "sealed");
    boolean hasNonSealed = hasModifier(modifiers, "non-sealed");
    switch (declarationKind) {
      case "class" -> {
        if (hasFinal && hasAbstract)
          errorOnModifier(modifiers, "abstract", "a class cannot be both final and abstract");
        if (hasFinal && hasSealed)
          errorOnModifier(modifiers, "sealed", "a class cannot be both final and sealed");
        if (hasFinal && hasNonSealed)
          errorOnModifier(modifiers, "non-sealed", "a class cannot be both final and non-sealed");
        if (hasSealed && hasNonSealed)
          errorOnModifier(modifiers, "non-sealed", "a class cannot be both sealed and non-sealed");
      }
      case "interface", "annotation" -> {
        if (hasFinal) errorOnModifier(modifiers, "final", "an interface cannot be final");
      }
      case "enum" -> {
        if (hasAbstract) errorOnModifier(modifiers, "abstract", "an enum cannot be abstract");
        if (hasFinal) errorOnModifier(modifiers, "final", "an enum cannot be final");
      }
      case "record" -> {
        if (hasAbstract) errorOnModifier(modifiers, "abstract", "a record cannot be abstract");
        if (hasFinal) errorOnModifier(modifiers, "final", "a record cannot be final");
      }
      default -> {}
    }
  }

  private boolean hasModifier(List<FrontendNode> modifiers, String name) {
    return modifiers.stream().anyMatch(m -> m.name().equals(name));
  }

  private void errorOnModifier(List<FrontendNode> modifiers, String name, String message) {
    modifiers.stream()
        .filter(m -> m.name().equals(name))
        .findFirst()
        .ifPresent(m -> error(m.range(), "TY-MOD-0001", message));
  }

  /**
   * {@code [open] module dotted.name { requires ...; exports ...; opens ...; uses ...; provides
   * ...; }} — a module-info compilation unit (P13-07).
   */
  private FrontendNode parseModule(Token start, boolean isOpen) {
    String name = parseDottedName();
    var members = new ArrayList<FrontendNode>();
    if (isOpen) members.add(node("OpenModifier", "open", start.rawRange(), List.of()));
    if (!accept("{")) {
      error(current(), "TY-SYN-0002", "missing module body");
      return node("ModuleDeclaration", name, span(start, previous()), members);
    }
    while (!at(TokenKind.EOF) && !word("}")) {
      skipLines();
      if (word("}")) break;
      int before = p;
      if (word("requires")) {
        Token d = take();
        var mods = new ArrayList<FrontendNode>();
        while (word("transitive") || word("static")) {
          Token m = take();
          mods.add(
              node(
                  m.value().equals("transitive") ? "TransitiveModifier" : "StaticModifier",
                  m.value(),
                  m.rawRange(),
                  List.of()));
        }
        String required = parseDottedName();
        members.add(node("RequiresDirective", required, span(d, previous()), mods));
      } else if (word("exports") || word("opens")) {
        Token d = take();
        boolean isExports = d.value().equals("exports");
        String pkg = parseDottedName();
        var targets = new ArrayList<FrontendNode>();
        if (accept("to")) {
          do {
            String target = parseDottedName();
            targets.add(node("ExportsTarget", target, previous().rawRange(), List.of()));
          } while (accept(","));
        }
        members.add(
            node(
                isExports ? "ExportsDirective" : "OpensDirective",
                pkg,
                span(d, previous()),
                targets));
      } else if (word("uses")) {
        Token d = take();
        String service = parseDottedName();
        members.add(node("UsesDirective", service, span(d, previous()), List.of()));
      } else if (word("provides")) {
        Token d = take();
        String service = parseDottedName();
        var implementations = new ArrayList<FrontendNode>();
        if (accept("with")) {
          do {
            String impl = parseDottedName();
            implementations.add(
                node("ProvidesImplementation", impl, previous().rawRange(), List.of()));
          } while (accept(","));
        }
        members.add(node("ProvidesDirective", service, span(d, previous()), implementations));
      } else {
        error(current(), "TY-SYN-0002", "unsupported module directive");
        sync();
      }
      if (before == p) take();
    }
    Token end = current();
    if (!accept("}")) error(end, "TY-SYN-0002", "unterminated module");
    return node("ModuleDeclaration", name, span(start, end), members);
  }

  /** Dotted identifiers with no wildcard support, e.g. {@code com.example.Service}. */
  private String parseDottedName() {
    if (!isIdentifierLike()) {
      error(current(), "TY-SYN-0002", "expected module/package name");
      return "<missing>";
    }
    var name = new StringBuilder(take().value());
    while (word(".") && lookIdentifier(1)) {
      take();
      name.append('.').append(take().value());
    }
    return name.toString();
  }

  private FrontendNode parsePackageDeclaration() {
    Token start = take();
    var nameTokens = new ArrayList<Token>();
    while (!atLineBoundary()) {
      if (at(TokenKind.SEMICOLON)) semicolon(take());
      else nameTokens.add(take());
    }
    String name = buildQualifiedName(nameTokens, false);
    if (name == null) {
      error(start, "TY-SYN-0002", "invalid package name");
      return node("PackageDeclaration", "<missing>", span(start, previous()), List.of());
    }
    return node("PackageDeclaration", name, span(start, previous()), List.of());
  }

  private FrontendNode parseImportDeclaration() {
    Token start = take();
    boolean isStatic = accept("static");
    var nameTokens = new ArrayList<Token>();
    while (!atLineBoundary()) {
      if (at(TokenKind.SEMICOLON)) semicolon(take());
      else nameTokens.add(take());
    }
    String name = buildQualifiedName(nameTokens, true);
    if (name == null) {
      error(start, "TY-SYN-0002", "invalid import name");
      return node("ImportDeclaration", "<missing>", span(start, previous()), List.of());
    }
    var children =
        isStatic
            ? List.<FrontendNode>of(node("StaticModifier", "static", start.rawRange(), List.of()))
            : List.<FrontendNode>of();
    return node("ImportDeclaration", name, span(start, previous()), children);
  }

  /**
   * Dotted identifiers, optionally ending in a bare {@code *} (on-demand import), e.g. {@code
   * pkg.Type} or {@code pkg.*}. Returns {@code null} (a malformed name, diagnosed by the caller)
   * for anything else rather than silently swallowing arbitrary trailing tokens.
   */
  private String buildQualifiedName(List<Token> tokens, boolean allowWildcard) {
    if (tokens.isEmpty()) return null;
    var name = new StringBuilder();
    int i = 0;
    while (true) {
      if (i >= tokens.size()) return null;
      Token t = tokens.get(i);
      if (allowWildcard && t.value().equals("*") && i == tokens.size() - 1) {
        name.append('*');
        i++;
        break;
      }
      if (!isIdentifierLike(t)) return null;
      name.append(t.value());
      i++;
      if (i < tokens.size() && tokens.get(i).value().equals(".")) {
        name.append('.');
        i++;
      } else break;
    }
    return i == tokens.size() ? name.toString() : null;
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

  private boolean lookIdentifier(int n) {
    return p + n < tokens.size() && isIdentifierLike(tokens.get(p + n));
  }

  private boolean lookIsOpenParen(int n) {
    return p + n < tokens.size() && tokens.get(p + n).value().equals("(");
  }

  private boolean lookWord(int n, String v) {
    return p + n < tokens.size() && tokens.get(p + n).value().equals(v);
  }

  /**
   * Peeks past any leading modifiers (without consuming) to decide whether a statement position
   * actually starts a local class/interface/enum/record/{@code @interface} declaration, so a
   * modifier like {@code final} ahead of an unrelated statement is never mistaken for one.
   */
  private boolean looksLikeLocalTypeDeclaration() {
    int i = p;
    while (i < tokens.size()
        && Set.of("public", "protected", "private", "static", "final", "abstract")
            .contains(tokens.get(i).value())) i++;
    if (i >= tokens.size()) return false;
    String v = tokens.get(i).value();
    if (v.equals("class") || v.equals("interface") || v.equals("enum")) return true;
    if (v.equals("record")
        && i + 2 < tokens.size()
        && isIdentifierLike(tokens.get(i + 1))
        && tokens.get(i + 2).value().equals("(")) return true;
    return v.equals("@") && i + 1 < tokens.size() && tokens.get(i + 1).value().equals("interface");
  }

  private static final Set<String> YIELD_EXPRESSION_CONTINUATIONS =
      Set.of(
          ".", "=", "(", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=",
          ">>>=");

  /**
   * {@code yield} is a contextual keyword (lexes as a plain identifier), so a lone {@code yield}
   * token could just as well be a variable named {@code yield} being used in an expression
   * (assigned to, called, dereferenced, incremented, ...). Only treat it as a yield statement when
   * the next token doesn't look like one of those continuations.
   */
  private boolean looksLikeYieldStatement() {
    return p + 1 < tokens.size()
        && !YIELD_EXPRESSION_CONTINUATIONS.contains(tokens.get(p + 1).value());
  }

  /**
   * {@code for (Type name : iterable)} / {@code for (var name : iterable)} — exactly one top-level
   * (ternary-aware) colon inside the header, preceded by exactly a type/modifier token and a name
   * token. Basic for (colon-separated init/condition/update) is a separate, larger round; anything
   * not matching this exact single-variable shape falls back to it.
   */
  private boolean looksLikeEnhancedFor() {
    if (!lookWord(1, "(")) return false;
    int i = p + 2;
    int depth = 1;
    int ternaryDepth = 0;
    var colonIndexes = new ArrayList<Integer>();
    while (i < tokens.size() && depth > 0) {
      String v = tokens.get(i).value();
      if (v.equals("(") || v.equals("[")) depth++;
      else if (v.equals(")") || v.equals("]")) {
        depth--;
        if (depth == 0) break;
      } else if (depth == 1 && v.equals("?")) ternaryDepth++;
      else if (depth == 1 && v.equals(":")) {
        if (ternaryDepth > 0) ternaryDepth--;
        else colonIndexes.add(i);
      }
      i++;
    }
    if (colonIndexes.size() != 1) return false;
    int declStart = p + 2;
    int declLength = colonIndexes.get(0) - declStart;
    return declLength == 2
        && isIdentifierLike(tokens.get(declStart))
        && isIdentifierLike(tokens.get(declStart + 1));
  }

  private FrontendNode parseEnhancedFor(Token start) {
    accept("(");
    Token typeToken = take();
    Token nameToken = take();
    if (!accept(":")) error(current(), "TY-SYN-0002", "expected : in enhanced for");
    var iterableTokens = takeUntilCloseParen();
    var kids = new ArrayList<FrontendNode>();
    kids.add(
        node("ForVariableDeclaration", nameToken.value(), span(typeToken, nameToken), List.of()));
    if (!iterableTokens.isEmpty()) kids.add(buildExpression(iterableTokens));
    else error(start, "TY-SYN-0002", "missing enhanced for iterable");
    var body = parseStatementOrBlock();
    if (body != null) kids.add(body);
    else error(current(), "TY-SYN-0002", "missing for body");
    return node("EnhancedForStatement", "", span(start, previous()), kids);
  }

  /**
   * Teyru's colon-separated basic for: {@code for (init : condition : update)} — exactly two
   * structural (ternary-aware) colons at header-top-level, as opposed to enhanced-for's one.
   */
  private boolean looksLikeBasicFor() {
    if (!lookWord(1, "(")) return false;
    int i = p + 2;
    int depth = 1;
    int ternaryDepth = 0;
    int structuralColons = 0;
    while (i < tokens.size() && depth > 0) {
      String v = tokens.get(i).value();
      if (v.equals("(") || v.equals("[")) depth++;
      else if (v.equals(")") || v.equals("]")) {
        depth--;
        if (depth == 0) break;
      } else if (depth == 1 && v.equals("?")) ternaryDepth++;
      else if (depth == 1 && v.equals(":")) {
        if (ternaryDepth > 0) ternaryDepth--;
        else structuralColons++;
      }
      i++;
    }
    return structuralColons == 2;
  }

  /** One of the three colon/paren-delimited segments of a basic for header. */
  private List<Token> takeForSegment() {
    var result = new ArrayList<Token>();
    int depth = 0;
    int ternaryDepth = 0;
    while (!at(TokenKind.EOF)) {
      if (word(")") && depth == 0) {
        take();
        return result;
      }
      if (word(":") && depth == 0) {
        if (ternaryDepth > 0) {
          ternaryDepth--;
          result.add(take());
          continue;
        }
        take();
        return result;
      }
      Token t = take();
      if (t.value().equals("(") || t.value().equals("[")) depth++;
      else if (t.value().equals(")") || t.value().equals("]")) depth--;
      else if (t.value().equals("?") && depth == 0) ternaryDepth++;
      result.add(t);
    }
    return result;
  }

  private List<List<Token>> splitTopLevelCommas(List<Token> tokens) {
    var groups = new ArrayList<List<Token>>();
    var current = new ArrayList<Token>();
    int depth = 0;
    for (Token t : tokens) {
      if (t.value().equals("(") || t.value().equals("[")) depth++;
      else if (t.value().equals(")") || t.value().equals("]")) depth--;
      if (t.value().equals(",") && depth == 0) {
        groups.add(current);
        current = new ArrayList<>();
      } else current.add(t);
    }
    groups.add(current);
    return groups;
  }

  /**
   * A basic-for init clause is either one variable declaration ({@code Type name = expr}, a single
   * declarator only — multiple comma-joined declarators sharing one type are not yet supported) or
   * one or more comma-separated plain expressions ({@code i = 0, j = 10}).
   */
  private List<FrontendNode> parseForInitClauses(List<Token> tokens) {
    if (tokens.isEmpty()) return List.of();
    if (tokens.size() >= 3
        && isIdentifierLike(tokens.get(0))
        && isIdentifierLike(tokens.get(1))
        && tokens.get(2).value().equals("=")) {
      Token type = tokens.get(0);
      Token name = tokens.get(1);
      var initTokens = tokens.subList(3, tokens.size());
      var kids = new ArrayList<FrontendNode>();
      if (!initTokens.isEmpty()) kids.add(buildExpression(initTokens));
      return List.of(
          node("ForVariableDeclaration", name.value(), span(type, tokens.getLast()), kids));
    }
    return splitTopLevelCommas(tokens).stream()
        .filter(g -> !g.isEmpty())
        .map(this::buildExpression)
        .toList();
  }

  private List<FrontendNode> parseForUpdateClauses(List<Token> tokens) {
    return splitTopLevelCommas(tokens).stream()
        .filter(g -> !g.isEmpty())
        .map(this::buildExpression)
        .toList();
  }

  private FrontendNode parseBasicFor(Token start) {
    accept("(");
    var initTokens = takeForSegment();
    var conditionTokens = takeForSegment();
    var updateTokens = takeForSegment();
    var initNode =
        node(
            "ForInit",
            "",
            initTokens.isEmpty()
                ? start.rawRange()
                : span(initTokens.getFirst(), initTokens.getLast()),
            parseForInitClauses(initTokens));
    var conditionNode =
        node(
            "ForCondition",
            "",
            conditionTokens.isEmpty()
                ? start.rawRange()
                : span(conditionTokens.getFirst(), conditionTokens.getLast()),
            conditionTokens.isEmpty() ? List.of() : List.of(buildExpression(conditionTokens)));
    var updateNode =
        node(
            "ForUpdate",
            "",
            updateTokens.isEmpty()
                ? start.rawRange()
                : span(updateTokens.getFirst(), updateTokens.getLast()),
            parseForUpdateClauses(updateTokens));
    var kids = new ArrayList<FrontendNode>();
    kids.add(initNode);
    kids.add(conditionNode);
    kids.add(updateNode);
    var body = parseStatementOrBlock();
    if (body != null) kids.add(body);
    else error(current(), "TY-SYN-0002", "missing for body");
    return node("BasicForStatement", "", span(start, previous()), kids);
  }

  /**
   * {@code try (}, one resource declaration per newline-separated segment: {@code Type name = expr}
   * or a bare expression naming an already-declared effectively-final resource. A resource
   * initializer spanning multiple physical lines itself is not yet supported — each resource must
   * be exactly one line.
   */
  private List<FrontendNode> parseResources() {
    var segments = new ArrayList<List<Token>>();
    var current = new ArrayList<Token>();
    int depth = 0;
    while (!at(TokenKind.EOF)) {
      if (word(")") && depth == 0) {
        take();
        break;
      }
      if (at(TokenKind.NEWLINE) && depth == 0) {
        take();
        if (!current.isEmpty()) {
          segments.add(current);
          current = new ArrayList<>();
        }
        continue;
      }
      Token t = take();
      if (t.value().equals("(") || t.value().equals("[")) depth++;
      else if (t.value().equals(")") || t.value().equals("]")) depth--;
      current.add(t);
    }
    if (!current.isEmpty()) segments.add(current);
    var resources = new ArrayList<FrontendNode>();
    for (var seg : segments) {
      if (seg.size() >= 3
          && isIdentifierLike(seg.get(0))
          && isIdentifierLike(seg.get(1))
          && seg.get(2).value().equals("=")) {
        Token type = seg.get(0);
        Token name = seg.get(1);
        var initTokens = seg.subList(3, seg.size());
        var kids = new ArrayList<FrontendNode>();
        if (!initTokens.isEmpty()) kids.add(buildExpression(initTokens));
        resources.add(node("ResourceDeclaration", name.value(), span(type, seg.getLast()), kids));
      } else {
        resources.add(
            node(
                "ResourceDeclaration",
                "",
                span(seg.getFirst(), seg.getLast()),
                List.of(buildExpression(seg))));
      }
    }
    return resources;
  }

  /** {@code catch (Type1 | Type2 name) { ... }} — one or more pipe-separated exception types. */
  private FrontendNode parseCatchClause() {
    Token start = take();
    accept("(");
    var types = new ArrayList<FrontendNode>();
    while (true) {
      Token type = take();
      types.add(node("CatchType", type.value(), type.rawRange(), List.of()));
      if (accept("|")) continue;
      break;
    }
    Token exceptionName = take();
    accept(")");
    var kids = new ArrayList<FrontendNode>(types);
    kids.add(
        node(
            "CatchParameterDeclaration",
            exceptionName.value(),
            exceptionName.rawRange(),
            List.of()));
    var body = parseStatementOrBlock();
    if (body != null) kids.add(body);
    else error(current(), "TY-SYN-0002", "missing catch body");
    return node("CatchClause", "", span(start, previous()), kids);
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
