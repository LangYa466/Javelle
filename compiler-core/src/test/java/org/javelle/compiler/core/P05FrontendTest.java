package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.syntax.*;
import org.junit.jupiter.api.Test;

class P05FrontendTest {
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

  private static void has(ParseResult r, String... expected) {
    var actual = anchors(r.ast());
    for (String x : expected) assertTrue(actual.contains(x), x + " absent from " + actual);
  }

  @Test
  void parsesSelectedPositiveAndNegativeCases() {
    var literal = parse("class C {\n String s() {\n String s = \"a;b\"\n return s\n }\n}\n");
    assertTrue(literal.diagnostics().isEmpty());
    has(
        literal,
        "ClassDeclaration:C",
        "MethodDeclaration:s",
        "LocalVariableDeclaration:s",
        "ReturnStatement");
    var semi = parse("class C {\n int x = 1;\n}\n");
    assertEquals("JV-SYN-0001", semi.diagnostics().getFirst().code().value());
    assertEquals(
        "", semi.diagnostics().getFirst().fixes().getFirst().edits().getFirst().replacement());
    has(semi, "FieldDeclaration:x");
    var cast = parse("class C { String m() {\n var x = (String) null\n return x\n }}");
    has(cast, "LocalVariableDeclaration:x", "CastExpression", "NullLiteral");
    var nul = parse("class C { Object m() {\n var x = null\n return x\n }}");
    assertTrue(nul.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-TYP-0002")));
    var field = parse("class C { private var x = 1\n}");
    assertTrue(field.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-TYP-0003")));
  }

  @Test
  void distinguishesFieldPropertiesAndContextualNames() {
    var plain = parse("class C { private String raw = \"\"\n}");
    has(plain, "FieldDeclaration:raw", "StringLiteral");
    var property = parse("class C { private String name {\n public get\n public set(value)\n }}");
    has(property, "PropertyDeclaration:name", "GetterDeclaration", "SetterDeclaration:value");
    var computed = parse("class C { String x {\n get { return \"x\" }\n }}");
    has(computed, "PropertyDeclaration:x", "GetterDeclaration", "ReturnStatement", "StringLiteral");
    var names =
        parse(
            "class C { Object m(Object value) {\n Object field = value\n get()\n set()\n return field\n }}");
    assertTrue(names.diagnostics().isEmpty(), names.diagnostics().toString());
  }

  @Test
  void unsupportedAndIncompleteFailExplicitly() {
    var incomplete = parse("class C { String x { get { return");
    assertTrue(incomplete.recovered());
    assertTrue(
        incomplete.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
    has(incomplete, "ErrorNode");
    var loop = parse("class C { void m() { for (int i = 0 : i < 2 : i++) {} } }");
    assertTrue(loop.diagnostics().isEmpty(), loop.diagnostics().toString());
    has(loop, "BasicForStatement");
    var en = parse("enum E { A, B : int value() { return 1 } }");
    assertTrue(en.diagnostics().isEmpty(), en.diagnostics().toString());
    has(en, "EnumDeclaration:E", "EnumConstantDeclaration:A", "EnumConstantDeclaration:B");
  }

  @Test
  void lexerConservesRawUnicodeAndProtectsDataSemicolons() {
    String text = "//前\r\nclass 中文 { String s = \"a;b\" \\u003b }";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("lex.javelle", bytes),
            bytes,
            new ResourceBudget(10000, 1000, 1000, 100, 100, Long.MAX_VALUE));
    var tracker =
        new ResourceTracker(
            new ResourceBudget(10000, 1000, 1000, 100, 100, Long.MAX_VALUE),
            CancellationToken.none(),
            () -> 0L);
    var lex =
        new StatefulJavelleLexer()
            .lex(source, new FrontendOptions(1, 10), tracker, CancellationToken.none());
    assertEquals(1, lex.tokens().stream().filter(t -> t.kind() == TokenKind.SEMICOLON).count());
    StringBuilder rebuilt = new StringBuilder();
    for (var token : lex.tokens()) {
      token.leadingTrivia().forEach(t -> rebuilt.append(t.rawText()));
      rebuilt.append(token.rawText());
    }
    assertEquals(text, rebuilt.toString());
  }

  @Test
  void truncationAtEveryUtf16BoundaryTerminatesWithinDiagnosticCeiling() {
    String sample = "class C { String x { get { return \"x\" } } }";
    for (int i = 0; i <= sample.length(); i++) {
      if (i > 0 && i < sample.length() && Character.isLowSurrogate(sample.charAt(i))) continue;
      var result = parse(sample.substring(0, i));
      assertTrue(result.diagnostics().size() <= 10, "cut=" + i);
    }
  }

  @Test
  void precedencePostfixTernaryAndAssignmentAreStructural() {
    var result =
        parse("class C { Object m() {\n x = a + b * c\n return flag ? obj.get(1) : new C()\n }}");
    var all = new ArrayList<FrontendNode>();
    var todo = new ArrayDeque<FrontendNode>();
    todo.add(result.ast());
    while (!todo.isEmpty()) {
      var n = todo.removeFirst();
      all.add(n);
      todo.addAll(n.children());
    }
    var assignment =
        all.stream().filter(n -> n.kind().equals("AssignmentExpression")).findFirst().orElseThrow();
    assertEquals("=", assignment.name());
    var plus = assignment.children().get(1);
    assertEquals("+", plus.name());
    assertEquals("*", plus.children().get(1).name());
    assertTrue(all.stream().anyMatch(n -> n.kind().equals("ConditionalExpression")));
    assertTrue(
        all.stream()
            .anyMatch(n -> n.kind().equals("MemberAccessExpression") && n.name().equals("get")));
    assertTrue(all.stream().anyMatch(n -> n.kind().equals("CallExpression")));
    assertTrue(all.stream().anyMatch(n -> n.kind().equals("NewExpression")));
    for (var parent : all)
      for (var child : parent.children()) {
        assertEquals(Optional.of(parent.id()), child.parentId());
        assertTrue(parent.range().startOffset() <= child.range().startOffset());
        assertTrue(parent.range().endOffset() >= child.range().endOffset());
      }
  }

  @Test
  void ifParametersVisibilityComputedFieldAndRecoveryAreConcrete() {
    var result =
        parse(
            "class C { Object m(Object value) {\n if (value != null) { return value } else { return null }\n }}");
    var all = preorder(result.ast());
    assertTrue(all.contains("ParameterDeclaration:value"));
    var ifNode = find(result.ast(), "IfStatement");
    assertEquals(3, ifNode.children().size());
    assertEquals("BinaryExpression", ifNode.children().getFirst().kind());
    assertEquals("Block", ifNode.children().get(1).kind());
    var property = parse("class C { String x {\n private get { return field }\n }}");
    var getter = (AccessorNode) find(property.ast(), "GetterDeclaration");
    assertEquals("private", getter.visibility());
    assertTrue(
        property.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-PROP-0006")));
    var rhs = parse("class C { Object m() {\n var x =\n }}");
    assertTrue(rhs.diagnostics().stream().anyMatch(d -> d.code().value().equals("JV-SYN-0002")));
    assertTrue(preorder(rhs.ast()).contains("ErrorNode"));
    assertTrue(parse("class C { void m() {\n return\n }}").diagnostics().isEmpty());
    var record = parse("record R(int x) {}");
    assertTrue(record.diagnostics().isEmpty(), record.diagnostics().toString());
    assertTrue(preorder(record.ast()).contains("RecordDeclaration:R"));
  }

  @Test
  void manifestLoadsAllElevenCasesAndChecksOrderedExpectations() throws Exception {
    byte[] bytes =
        Objects.requireNonNull(getClass().getResourceAsStream("/p05/cases.json")).readAllBytes();
    Class<?> parser = Class.forName("org.javelle.compiler.core.diagnostic.JsonParser");
    var constructor = parser.getDeclaredConstructor(byte[].class, int.class, int.class);
    constructor.setAccessible(true);
    Object instance = constructor.newInstance(bytes, bytes.length + 1, 50);
    var method = parser.getDeclaredMethod("parse");
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    var root = (Map<String, Object>) method.invoke(instance);
    @SuppressWarnings("unchecked")
    var cases = (List<Map<String, Object>>) root.get("cases");
    assertEquals(11, cases.size());
    var completeSnapshot = new StringBuilder();
    for (var fixture : cases) {
      String source = (String) fixture.get("source");
      var result = parse(source);
      completeSnapshot
          .append("=== ")
          .append(fixture.get("id"))
          .append(" ===\n")
          .append(FrontendSnapshot.canonical(result));
      @SuppressWarnings("unchecked")
      var expectedAst = (List<String>) fixture.get("expectedAst");
      var actual = preorder(result.ast());
      int cursor = 0;
      for (String expected : expectedAst) {
        while (cursor < actual.size() && !actual.get(cursor).equals(expected)) cursor++;
        assertTrue(
            cursor < actual.size(),
            fixture.get("id") + " missing ordered " + expected + " from " + actual);
        cursor++;
      }
      @SuppressWarnings("unchecked")
      var expectedDiagnostics = (List<Map<String, Object>>) fixture.get("expectedDiagnostics");
      assertEquals(
          expectedDiagnostics.stream().map(x -> x.get("code")).toList(),
          result.diagnostics().stream().map(x -> x.code().value()).distinct().toList(),
          fixture.get("id").toString());
      for (var expected : expectedDiagnostics) {
        String anchor = (String) expected.get("anchor");
        int start = source.indexOf(anchor);
        assertTrue(start >= 0);
        var diagnostic =
            result.diagnostics().stream()
                .filter(d -> d.code().value().equals(expected.get("code")))
                .findFirst()
                .orElseThrow();
        assertEquals(start, diagnostic.range().startOffset());
        assertEquals(((Long) expected.get("length")).intValue(), diagnostic.range().length());
      }
    }
    String golden =
        new String(
            Objects.requireNonNull(getClass().getResourceAsStream("/p05/ast-snapshots.txt"))
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertEquals(
        golden,
        completeSnapshot.toString(),
        "complete AST/diagnostic snapshot drift; review the full golden diff");
  }

  @Test
  void truncationRunsUnderTimeoutWithProgressErrorAndDeduplication() {
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(3),
        () -> {
          String sample = "class C { String x { get { return \"x\" } } }";
          for (int i = 1; i < sample.length(); i++) {
            var result = parse(sample.substring(0, i));
            assertTrue(result.recovered());
            assertFalse(result.diagnostics().isEmpty());
            assertTrue(result.diagnostics().size() <= 10);
            var keys = result.diagnostics().stream().map(d -> d.code() + ":" + d.range()).toList();
            assertEquals(keys.size(), new HashSet<>(keys).size());
          }
        });
  }

  private static FrontendNode find(FrontendNode root, String kind) {
    var todo = new ArrayDeque<FrontendNode>();
    todo.add(root);
    while (!todo.isEmpty()) {
      var node = todo.removeFirst();
      if (node.kind().equals(kind)) return node;
      todo.addAll(node.children());
    }
    throw new NoSuchElementException(kind);
  }

  private static List<String> preorder(FrontendNode root) {
    var out = new ArrayList<String>();
    preorder(root, out);
    return out;
  }

  private static void preorder(FrontendNode node, List<String> out) {
    out.add(node.kind() + (node.name().isBlank() ? "" : ":" + node.name()));
    for (var child : node.children()) preorder(child, out);
  }

  @Test
  void everyFrozenUnsupportedRepresentativeIsSingleAndBalanced() {
    var samples =
        Map.of(
            "try",
            "class C { void m() { try { x() } catch (E e) { y() } return } }",
            "synchronized",
            "class C { void m() { synchronized (this) { x() } return } }");
    for (var sample : samples.entrySet()) {
      var result = parse(sample.getValue());
      var development =
          result.diagnostics().stream()
              .filter(d -> d.code().value().equals("JV-DEV-0001"))
              .toList();
      assertEquals(1, development.size(), sample.getKey() + result.diagnostics());
      assertTrue(
          preorder(result.ast()).stream().anyMatch(x -> x.startsWith("UnsupportedSyntaxNode:")),
          sample.getKey());
      assertTrue(
          result.diagnostics().stream().noneMatch(d -> d.code().value().equals("JV-SYN-0002")),
          sample.getKey() + result.diagnostics());
    }
  }

  @Test
  void methodParameterVarargsIsExactlyOneBalancedDevelopmentUnsupported() {
    var result = parse("class C { void m(String... x){} }");
    assertEquals(
        1,
        result.diagnostics().stream().filter(d -> d.code().value().equals("JV-DEV-0001")).count());
    assertEquals(
        0,
        result.diagnostics().stream().filter(d -> d.code().value().startsWith("JV-SYN-")).count());
    var unsupported =
        preorder(result.ast()).stream()
            .filter(x -> x.equals("UnsupportedSyntaxNode:varargs"))
            .toList();
    assertEquals(1, unsupported.size());
    assertTrue(result.recovered());
  }
}
