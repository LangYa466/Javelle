package org.javelle.compiler.core.semantic;

import java.util.*;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.semantic.BoundCompilationUnit.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.*;

/** Binds the lossless P05 AST and CST token payload. It never scans or reparses source text. */
public final class EarlySemanticBinder {
  private SourceFile source;
  private String module;
  private List<Token> tokens;
  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private final Map<String, SymbolId> symbols = new LinkedHashMap<>();
  private final Map<String, TypeRef> types = new LinkedHashMap<>();
  private final Map<String, PropertyDescriptor> properties = new LinkedHashMap<>();

  public BindingResult bind(ParseResult parsed, SourceFile sourceFile, String moduleName) {
    Objects.requireNonNull(parsed);
    source = Objects.requireNonNull(sourceFile);
    module = Objects.requireNonNull(moduleName);
    diagnostics.clear();
    diagnostics.addAll(parsed.diagnostics());
    symbols.clear();
    types.clear();
    properties.clear();
    tokens = flatten(parsed.cst());
    if (parsed.recovered() || containsUnsupported(parsed.ast()))
      return new BindingResult(Optional.empty(), diagnostics);
    String packageName = "";
    var imports = new ArrayList<String>();
    var classes = new ArrayList<BoundClass>();
    for (var node : parsed.ast().children()) {
      if (node.kind().equals("PackageDeclaration")) packageName = qualified(node, "package");
      else if (node.kind().equals("ImportDeclaration")) imports.add(qualified(node, "import"));
      else if (node.kind().equals("ClassDeclaration")) classes.add(bindClass(node, packageName));
    }
    var unit = new BoundCompilationUnit(source, packageName, imports, classes);
    diagnostics.addAll(new EarlyTypeChecker().check(unit));
    if (diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR))
      return new BindingResult(Optional.empty(), diagnostics);
    return new BindingResult(Optional.of(unit), diagnostics);
  }

  private BoundClass bindClass(FrontendNode node, String pkg) {
    String owner = pkg.isBlank() ? node.name() : pkg + "." + node.name();
    var members = new ArrayList<BoundMember>();
    for (var child : node.children()) {
      TypeRef type = typeAt(child.range().startOffset());
      String display = type.displayName();
      SymbolKind kind =
          child.kind().equals("PropertyDeclaration")
              ? SymbolKind.PROPERTY
              : child.kind().equals("MethodDeclaration") ? SymbolKind.METHOD : SymbolKind.FIELD;
      var symbol = symbol(owner, kind, child.name(), display);
      symbols.put(child.name(), symbol);
      types.put(child.name(), type);
    }
    for (var child : node.children()) {
      TypeRef type = types.get(child.name());
      String display = type.displayName();
      if (child.kind().equals("FieldDeclaration"))
        members.add(
            new BoundField(
                child.id(),
                visibility(child),
                List.of(),
                display,
                child.name(),
                child.children().isEmpty()
                    ? Optional.empty()
                    : Optional.of(expression(child.children().getFirst(), owner))));
      else if (child.kind().equals("MethodDeclaration"))
        members.add(bindMethod(child, owner, display));
      else if (child.kind().equals("PropertyDeclaration"))
        members.add(bindProperty(child, owner, type));
    }
    return new BoundClass(node.id(), visibility(node), node.name(), members);
  }

  private BoundMethod bindMethod(FrontendNode node, String owner, String returnType) {
    var parameters = new ArrayList<BoundParameter>();
    var body = new ArrayList<BoundStatement>();
    for (var child : node.children()) {
      if (child.kind().equals("ParameterDeclaration")) {
        TypeRef type = typeAt(child.range().startOffset());
        parameters.add(new BoundParameter(type.displayName(), child.name()));
        symbols.put(
            child.name(),
            symbol(
                owner + "." + node.name(), SymbolKind.PARAMETER, child.name(), type.displayName()));
        types.put(child.name(), type);
      } else body.add(statement(child, owner));
    }
    for (var statement : body)
      if (statement instanceof BoundReturn r
          && r.value().isPresent()
          && !r.value().orElseThrow().type().displayName().equals(returnType))
        error(node, "JV-TYP-0001", "return type mismatch");
    return new BoundMethod(
        node.id(), visibility(node), List.of(), returnType, node.name(), parameters, body);
  }

  private BoundProperty bindProperty(FrontendNode node, String owner, TypeRef type) {
    Optional<FrontendNode> initializerNode =
        node.children().stream().filter(x -> x.kind().equals("PropertyInitializer")).findFirst();
    boolean stored =
        initializerNode.isPresent()
            || node.children().stream()
                .flatMap(x -> flattenAst(x).stream())
                .anyMatch(x -> x.kind().equals("NameExpression") && x.name().equals("field"))
            || node.children().stream()
                .filter(x -> !x.kind().equals("PropertyInitializer"))
                .allMatch(x -> x.children().isEmpty());
    var property = symbol(owner, SymbolKind.PROPERTY, node.name(), type.displayName());
    var storage =
        stored
            ? Optional.of(symbol(owner, SymbolKind.FIELD, node.name(), type.displayName()))
            : Optional.<SymbolId>empty();
    var origin =
        new GeneratedMemberOrigin(
            source.id(),
            node.id(),
            GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION,
            "property");
    Optional<BoundAccessor> getter = Optional.empty(), setter = Optional.empty();
    for (var child : node.children()) {
      if (child.kind().equals("PropertyInitializer")) continue;
      if (child.kind().equals("SetterDeclaration")) {
        symbols.put(
            child.name(),
            symbol(
                owner + "." + node.name(), SymbolKind.PARAMETER, child.name(), type.displayName()));
        types.put(child.name(), type);
      }
      if (stored) {
        symbols.put("field", storage.orElseThrow());
        types.put("field", type);
      }
      var body = child.children().stream().map(x -> statement(x, owner)).toList();
      var accessor =
          new BoundAccessor(
              child instanceof AccessorNode a ? a.visibility() : "",
              child.name(),
              body,
              body.isEmpty());
      if (child.kind().equals("GetterDeclaration")) getter = Optional.of(accessor);
      else if (child.kind().equals("SetterDeclaration")) setter = Optional.of(accessor);
    }
    var descriptor =
        new PropertyDescriptor(
            property,
            type,
            stored
                ? PropertyDescriptor.PropertyKind.STORED
                : PropertyDescriptor.PropertyKind.COMPUTED,
            storage,
            Optional.empty(),
            Optional.empty(),
            Set.of(),
            origin,
            1);
    properties.put(node.name(), descriptor);
    return new BoundProperty(
        node.id(),
        visibility(node),
        List.of(),
        type.displayName(),
        node.name(),
        initializerNode.map(x -> expression(x.children().getFirst(), owner)),
        getter,
        setter,
        descriptor);
  }

  private BoundStatement statement(FrontendNode node, String owner) {
    return switch (node.kind()) {
      case "ReturnStatement" ->
          new BoundReturn(
              node.children().isEmpty()
                  ? Optional.empty()
                  : Optional.of(expression(node.children().getFirst(), owner)));
      case "LocalVariableDeclaration" -> {
        String declared = tokenAt(node.range().startOffset()).value();
        BoundExpression value = expression(node.children().getFirst(), owner);
        TypeRef type =
            declared.equals("var") || declared.equals("val") ? value.type() : type(declared);
        symbols.put(node.name(), symbol(owner, SymbolKind.LOCAL, node.name(), type.displayName()));
        types.put(node.name(), type);
        yield new BoundLocal(
            declared.equals("var") || declared.equals("val") ? declared : "explicit",
            type.displayName(),
            node.name(),
            value);
      }
      case "ExpressionStatement" ->
          new BoundExpressionStatement(expression(node.children().getFirst(), owner));
      case "IfStatement" -> {
        var condition = expression(node.children().getFirst(), owner);
        var yes =
            node.children().size() > 1
                ? block(node.children().get(1), owner)
                : List.<BoundStatement>of();
        var no =
            node.children().size() > 2
                ? block(node.children().get(2), owner)
                : List.<BoundStatement>of();
        yield new BoundIf(condition, yes, no);
      }
      default ->
          throw new IllegalArgumentException("JV-DEV-0001 unsupported statement " + node.kind());
    };
  }

  private List<BoundStatement> block(FrontendNode node, String owner) {
    return node.kind().equals("Block")
        ? node.children().stream().map(x -> statement(x, owner)).toList()
        : List.of(new BoundExpressionStatement(expression(node, owner)));
  }

  private BoundExpression expression(FrontendNode n, String owner) {
    return switch (n.kind()) {
      case "StringLiteral", "NumericLiteral", "NullLiteral" ->
          new Literal(tokenAt(n.range().startOffset()).rawText(), literalType(n), n.id());
      case "NameExpression" -> {
        SymbolId symbol = symbols.get(n.name());
        if (symbol == null && !n.name().equals("field")) {
          error(n, "JV-TYP-0004", "unresolved symbol: " + n.name());
          yield new Name(n.name(), new ErrorType("unresolved"), n.id(), Optional.empty());
        }
        yield new Name(n.name(), types.get(n.name()), n.id(), Optional.ofNullable(symbol));
      }
      case "AssignmentExpression" -> {
        var left = expression(n.children().get(0), owner);
        yield new Assignment(left, expression(n.children().get(1), owner), left.type(), n.id());
      }
      case "BinaryExpression" -> {
        var left = expression(n.children().get(0), owner);
        yield new Binary(
            n.name(), left, expression(n.children().get(1), owner), left.type(), n.id());
      }
      case "UnaryExpression" -> {
        var value = expression(n.children().getFirst(), owner);
        yield new Unary(n.name(), value, value.type(), n.id());
      }
      case "MemberAccessExpression" ->
          new Member(
              expression(n.children().getFirst(), owner),
              n.name(),
              properties.containsKey(n.name())
                  ? properties.get(n.name()).type()
                  : expression(n.children().getFirst(), owner).type(),
              n.id(),
              Optional.ofNullable(properties.get(n.name())));
      case "CallExpression" -> {
        var args = n.children().stream().skip(1).map(x -> expression(x, owner)).toList();
        var targetNode = n.children().getFirst();
        if (targetNode.kind().equals("NewExpression")) {
          String typeName = targetNode.children().getFirst().name();
          yield new NewObject(typeName, args, type(typeName), n.id());
        }
        var target = expression(targetNode, owner);
        yield new Call(target, args, target.type(), n.id());
      }
      case "NewExpression" ->
          new NewObject(
              n.children().getFirst().name(),
              List.of(),
              type(n.children().getFirst().name()),
              n.id());
      case "CastExpression" -> {
        String target =
            tokensIn(n).stream()
                .filter(t -> !t.value().equals("("))
                .findFirst()
                .orElseThrow()
                .value();
        yield new Cast(target, expression(n.children().getFirst(), owner), type(target), n.id());
      }
      default ->
          throw new IllegalArgumentException("JV-DEV-0001 unsupported expression " + n.kind());
    };
  }

  private TypeRef literalType(FrontendNode n) {
    if (n.kind().equals("NullLiteral")) return new NullType();
    if (n.kind().equals("StringLiteral")) return type("String");
    String v = tokenAt(n.range().startOffset()).value();
    return new PrimitiveType(v.contains(".") ? "double" : "int");
  }

  private TypeRef typeAt(int offset) {
    return type(tokenAt(offset).value());
  }

  private TypeRef type(String name) {
    return Set.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char")
            .contains(name)
        ? new PrimitiveType(name)
        : new DeclaredType(name, List.of());
  }

  private SymbolId symbol(String owner, SymbolKind kind, String name, String type) {
    return new SymbolId(module, owner, kind, name, type, 0);
  }

  private String visibility(FrontendNode n) {
    return tokensIn(n).stream()
        .map(Token::value)
        .filter(Set.of("public", "protected", "private")::contains)
        .findFirst()
        .orElse("");
  }

  private String qualified(FrontendNode n, String keyword) {
    var values =
        tokensIn(n).stream()
            .map(Token::value)
            .filter(x -> !x.equals(keyword))
            .filter(x -> !x.equals("\n"))
            .toList();
    if (!values.isEmpty() && values.getFirst().equals("static"))
      return "static " + String.join("", values.subList(1, values.size()));
    return String.join("", values);
  }

  private Token tokenAt(int offset) {
    return tokens.stream()
        .filter(t -> t.rawRange().startOffset() == offset)
        .findFirst()
        .orElseThrow();
  }

  private List<Token> tokensIn(FrontendNode n) {
    return tokens.stream()
        .filter(
            t ->
                t.rawRange().startOffset() >= n.range().startOffset()
                    && t.rawRange().endOffset() <= n.range().endOffset()
                    && t.kind() != TokenKind.NEWLINE
                    && t.kind() != TokenKind.EOF)
        .toList();
  }

  private List<Token> flatten(CstNode root) {
    var out = new ArrayList<Token>();
    var q = new ArrayDeque<CstNode>();
    q.add(root);
    while (!q.isEmpty()) {
      var n = q.removeFirst();
      if (n instanceof CstToken t) out.add(t.token());
      else q.addAll(n.children());
    }
    return List.copyOf(out);
  }

  private boolean containsUnsupported(FrontendNode n) {
    return n instanceof UnsupportedSyntaxNode
        || n.kind().equals("ErrorNode")
        || n.children().stream().anyMatch(this::containsUnsupported);
  }

  private List<FrontendNode> flattenAst(FrontendNode n) {
    var out = new ArrayList<FrontendNode>();
    out.add(n);
    n.children().forEach(x -> out.addAll(flattenAst(x)));
    return out;
  }

  private void error(FrontendNode n, String code, String message) {
    diagnostics.add(
        new Diagnostic(
            1,
            new DiagnosticCode(code),
            Severity.ERROR,
            source.id(),
            n.range(),
            message,
            List.of(),
            Map.of()));
  }
}
