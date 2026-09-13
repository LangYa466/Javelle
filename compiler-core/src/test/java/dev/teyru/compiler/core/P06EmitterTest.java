package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.emitter.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.lowering.*;
import dev.teyru.compiler.core.semantic.*;
import dev.teyru.compiler.core.semantic.BoundCompilationUnit.*;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.sourcemap.*;
import dev.teyru.compiler.core.symbol.*;
import dev.teyru.compiler.core.syntax.NodeId;
import org.junit.jupiter.api.Test;

class P06EmitterTest {
  private static final TypeRef STRING = new DeclaredType("String", List.of());

  private static BoundCompilationUnit fixture() {
    String sourceText = "package demo\npublic class User { property name }";
    byte[] bytes = sourceText.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("demo/User.teyru", bytes),
            bytes,
            new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE));
    var range = new TextRange(OffsetUnit.RAW_UTF16, 0, sourceText.length());
    var node = new NodeId(source.id(), "Property", range, 0);
    var propertySymbol =
        new SymbolId("main", "demo.User", SymbolKind.PROPERTY, "name", "Ljava/lang/String;", 0);
    var storage =
        new SymbolId("main", "demo.User", SymbolKind.FIELD, "name", "Ljava/lang/String;", 0);
    var origin =
        new GeneratedMemberOrigin(
            source.id(), node, GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION, "property");
    var descriptor =
        new PropertyDescriptor(
            propertySymbol,
            STRING,
            PropertyDescriptor.PropertyKind.STORED,
            Optional.of(storage),
            Optional.empty(),
            Optional.empty(),
            Set.of(),
            origin,
            1);
    var empty = new Literal("\"\"", STRING, node);
    var valueSymbol =
        new SymbolId("main", "demo.User", SymbolKind.PARAMETER, "value", "Ljava/lang/String;", 0);
    var value = new Name("value", STRING, node, Optional.of(valueSymbol));
    var trimMember = new Member(value, "trim", STRING, node, Optional.empty());
    var trim = new Call(trimMember, List.of(), STRING, node);
    var fieldName = new Name("field", STRING, node, Optional.of(storage));
    var setterBody =
        List.<BoundStatement>of(
            new BoundExpressionStatement(new Assignment(fieldName, trim, STRING, node)));
    var property =
        new BoundProperty(
            node,
            "public",
            List.of(),
            "String",
            "name",
            Optional.of(empty),
            Optional.of(new BoundAccessor("public", "", List.of(), true)),
            Optional.of(new BoundAccessor("public", "value", setterBody, false)),
            descriptor);
    var raw = new BoundField(node, "public", List.of(), "String", "raw", Optional.of(empty));
    var clazz = new BoundClass(node, "public", "User", List.of(raw, property));
    return new BoundCompilationUnit(source, "demo", List.of(), List.of(clazz));
  }

  private static EmitResult emit(BoundCompilationUnit unit) {
    return new DeterministicJavaEmitter()
        .emit(
            unit,
            new EmitOptions(1, 21, "0.1-test", "    ", "\n"),
            new ResourceTracker(
                new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE),
                CancellationToken.none(),
                () -> 0L),
            CancellationToken.none());
  }

  @Test
  void earlyLoweringPreservesTypedOrderAndOrigins() {
    var unit = fixture();
    var property = (BoundProperty) unit.classes().getFirst().members().get(1);
    var initializer = new Literal("\"value\"", STRING, property.node());
    var body = List.<BoundStatement>of(new BoundLocal("val", "", "temporary", initializer));
    var lowered = new EarlyLowerer().lower(body, property.node(), STRING);
    assertEquals(1, lowered.operations().size());
    var temporary = assertInstanceOf(IrTemporary.class, lowered.operations().getFirst());
    assertEquals(STRING, temporary.type());
    assertInstanceOf(IrValue.class, temporary.initializer());
    assertEquals(unit.source(), temporary.origin().locations().getFirst().source());
  }

  @Test
  void parseBindEmitJavacAndJvmEndToEnd() throws Exception {
    String text =
        "package demo\nimport java.util.Set\nimport java.util.List\nimport java.util.Set\nclass User {\nString name = \"\" {\nget\nset(value) {\nfield = value.trim()\n}\n}\nString copy(User other) {\nreturn other.name\n}\nvoid rename(User other, String next) {\nother.name = next\n}\n}\n";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("demo/User.teyru", bytes),
            bytes,
            new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE));
    var parsed =
        new TeyruFrontend()
            .parse(
                source,
                new FrontendOptions(1, 20),
                new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE),
                CancellationToken.none());
    assertTrue(parsed.diagnostics().isEmpty());
    var binding = new EarlySemanticBinder().bind(parsed, source, "main");
    assertTrue(binding.diagnostics().isEmpty(), binding.diagnostics().toString());
    var result = emit(binding.unit().orElseThrow());
    assertTrue(result.diagnostics().isEmpty());
    String java = result.files().getFirst().javaText();
    assertTrue(java.indexOf("import java.util.List;") < java.indexOf("import java.util.Set;"));
    assertEquals(1, java.split("import java.util.Set;", -1).length - 1);
    assertTrue(java.contains("String copy(User other)"));
    assertTrue(java.contains("String getName()"));
    assertTrue(java.contains("this.name = value.trim();"));
    assertTrue(java.contains("return other.getName();"));
    assertTrue(java.contains("other.setName(next);"));
    var featureIds =
        result.files().getFirst().sourceMap().segments().stream()
            .flatMap(s -> s.memberOrigin().stream())
            .map(GeneratedMemberOrigin::featureId)
            .collect(Collectors.toSet());
    assertTrue(
        featureIds.containsAll(Set.of("property-storage", "property-getter", "property-setter")));
    assertTrue(
        result.files().getFirst().sourceMap().segments().stream()
            .anyMatch(s -> s.reason().equals("return expression")));
    Path root = Files.createTempDirectory("teyru-bind-e2e-");
    try {
      Path generated = root.resolve("demo/User.java");
      Files.createDirectories(generated.getParent());
      Files.writeString(generated, java);
      Path consumer = root.resolve("demo/Consumer.java");
      Files.writeString(
          consumer,
          "package demo; public class Consumer { public static void main(String[] x) { User u=new User(); User target=new User(); u.rename(target, \" Alice \" ); System.out.print(u.copy(target)); } }");
      Path classes = root.resolve("classes");
      Files.createDirectories(classes);
      assertEquals(
          0,
          ToolProvider.getSystemJavaCompiler()
              .run(
                  null,
                  null,
                  null,
                  "-proc:none",
                  "--release",
                  "21",
                  "-d",
                  classes.toString(),
                  generated.toString(),
                  consumer.toString()));
      var process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  classes.toString(),
                  "demo.Consumer")
              .start();
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
      assertEquals(
          "Alice", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    } finally {
      try (var paths = Files.walk(root)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  @Test
  void binderFailsClosedForUnresolvedAndUnsupportedInput() {
    for (String text :
        List.of(
            "class Bad { String x() { return missing\n} }\n",
            "class Bad { int x() { return \"wrong\"\n} }\n",
            "class Bad { String x() { while (true) {}\n} }\n")) {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
      var source =
          SourceFile.decode(
              SourceId.forContent("Bad.teyru", bytes),
              bytes,
              new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE));
      var parsed =
          new TeyruFrontend()
              .parse(
                  source,
                  new FrontendOptions(1, 20),
                  new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE),
                  CancellationToken.none());
      var binding = new EarlySemanticBinder().bind(parsed, source, "main");
      assertTrue(binding.unit().isEmpty());
      assertTrue(
          binding.diagnostics().stream()
              .anyMatch(d -> d.severity() == dev.teyru.compiler.core.diagnostic.Severity.ERROR));
    }
  }

  @Test
  void computedPropertyHasNoStorageAndKeepsAccessorVisibility() {
    String text = "class Computed {\nString label {\nprivate get {\nreturn \"fixed\"\n}\n}\n}\n";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("Computed.teyru", bytes),
            bytes,
            new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE));
    var parsed =
        new TeyruFrontend()
            .parse(
                source,
                new FrontendOptions(1, 20),
                new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE),
                CancellationToken.none());
    var binding = new EarlySemanticBinder().bind(parsed, source, "main");
    assertTrue(binding.diagnostics().isEmpty(), binding.diagnostics().toString());
    var property =
        assertInstanceOf(
            BoundProperty.class,
            binding.unit().orElseThrow().classes().getFirst().members().getFirst());
    assertEquals(PropertyDescriptor.PropertyKind.COMPUTED, property.descriptor().kind());
    assertTrue(property.descriptor().storage().isEmpty());
    String java = emit(binding.unit().orElseThrow()).files().getFirst().javaText();
    assertTrue(java.contains("private String getLabel()"));
    assertFalse(java.contains("String label;"));
  }

  @Test
  void crlfEmojiMapUsesCodePointsAndFindsExactReturnedExpression() {
    String text = "// 😀\r\nclass Bad {\r\nString x() {\r\nreturn missing\r\n}\r\n}\r\n";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    var source =
        SourceFile.decode(
            SourceId.forContent("emoji/Bad.teyru", bytes),
            bytes,
            new ResourceBudget(100000, 1000, 1000, 100, 100, Long.MAX_VALUE));
    int rawStart = text.indexOf("missing");
    var expressionRange =
        new TextRange(OffsetUnit.RAW_UTF16, rawStart, rawStart + "missing".length());
    var expressionNode = new NodeId(source.id(), "NameExpression", expressionRange, 1);
    var methodNode =
        new NodeId(
            source.id(),
            "MethodDeclaration",
            new TextRange(
                OffsetUnit.RAW_UTF16, text.indexOf("String x"), text.indexOf("}\r\n") + 1),
            0);
    var missing =
        new Name(
            "missing",
            STRING,
            expressionNode,
            Optional.of(new SymbolId("main", "Bad.x", SymbolKind.LOCAL, "missing", "String", 0)));
    var method =
        new BoundMethod(
            methodNode,
            "",
            List.of(),
            "String",
            "x",
            List.of(),
            List.of(new BoundReturn(Optional.of(missing))));
    var unit =
        new BoundCompilationUnit(
            source, "", List.of(), List.of(new BoundClass(methodNode, "", "Bad", List.of(method))));
    var result = emit(unit);
    var file = result.files().getFirst();
    int generatedOffset = file.javaText().codePointCount(0, file.javaText().indexOf("missing"));
    var exact =
        file.sourceMap().generatedAt(file.relativeUri(), generatedOffset).stream()
            .filter(s -> s.reason().equals("return expression"))
            .findFirst()
            .orElseThrow();
    int expectedStart =
        source.convertBoundary(
            rawStart, OffsetUnit.RAW_UTF16, OffsetUnit.UNICODE_CODE_POINT, Bias.START);
    int expectedEnd =
        source.convertBoundary(
            rawStart + "missing".length(),
            OffsetUnit.RAW_UTF16,
            OffsetUnit.UNICODE_CODE_POINT,
            Bias.END);
    assertEquals(
        new TextRange(OffsetUnit.UNICODE_CODE_POINT, expectedStart, expectedEnd),
        exact.originals().getFirst().range());
    assertTrue(
        file.sourceMap().segments().stream()
            .anyMatch(
                s ->
                    s.kind() == MappingKind.SYNTHETIC
                        && s.memberOrigin().orElseThrow().featureId().equals("generated-header")));
  }

  @Test
  void readableEmissionIsDeterministicMappedAndCompilesForReal() throws Exception {
    var unit = fixture();
    var first = emit(unit);
    var second = emit(unit);
    assertTrue(first.diagnostics().isEmpty());
    assertEquals(first.files().getFirst().javaText(), second.files().getFirst().javaText());
    assertEquals(
        first.files().getFirst().contentSha256(), second.files().getFirst().contentSha256());
    String java = first.files().getFirst().javaText();
    String expected =
        "// Generated by Teyru 0.1-test\n// Source: demo/User.teyru sha256="
            + unit.source().contentSha256()
            + "\npackage demo;\n\npublic class User {\n    public String raw = \"\";\n    private String name = \"\";\n    public String getName() {\n        return this.name;\n    }\n    public void setName(String value) {\n        this.name = value.trim();\n    }\n}\n";
    assertEquals(expected, java);
    assertFalse(java.contains(System.getProperty("user.dir")));
    assertFalse(java.matches("(?s).*20\\d\\d-.*"));
    assertFalse(first.files().getFirst().sourceMap().segments().isEmpty());
    assertTrue(
        first.files().getFirst().sourceMap().segments().stream()
            .anyMatch(s -> s.memberOrigin().isPresent()));
    Path root = Files.createTempDirectory("teyru p06 測試 ");
    try {
      Path source = root.resolve("demo/User.java");
      Files.createDirectories(source.getParent());
      Files.writeString(source, java);
      Path consumer = root.resolve("Consumer.java");
      Files.writeString(
          consumer,
          "import demo.User; public class Consumer { public static void main(String[] a) { User u=new User(); u.setName(\" Alice \" ); System.out.print(u.getName()); } }");
      Path classes = root.resolve("classes");
      Files.createDirectories(classes);
      int exit =
          ToolProvider.getSystemJavaCompiler()
              .run(
                  null,
                  null,
                  null,
                  "-proc:none",
                  "--release",
                  "21",
                  "-d",
                  classes.toString(),
                  source.toString(),
                  consumer.toString());
      assertEquals(0, exit);
      var process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  classes.toString(),
                  "Consumer")
              .start();
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
      assertEquals(
          "Alice", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
      try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL()})) {
        Class<?> type = loader.loadClass("demo.User");
        var field = type.getDeclaredField("name");
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertEquals(String.class, field.getType());
        assertEquals(String.class, type.getMethod("getName").getReturnType());
        assertEquals(void.class, type.getMethod("setName", String.class).getReturnType());
        assertThrows(NoSuchMethodException.class, () -> type.getMethod("getRaw"));
      }
      Path illegal = root.resolve("Illegal.java");
      Files.writeString(
          illegal, "import demo.User; class Illegal { String x(User u) { return u.name; } }");
      assertNotEquals(
          0,
          ToolProvider.getSystemJavaCompiler()
              .run(
                  null,
                  null,
                  null,
                  "-proc:none",
                  "--release",
                  "21",
                  "-cp",
                  classes.toString(),
                  "-d",
                  classes.toString(),
                  illegal.toString()));
    } finally {
      try (var paths = Files.walk(root)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  @Test
  void boundPropertyReadWriteAndPlainFieldStayDistinct() {
    var unit = fixture();
    var property = (BoundProperty) unit.classes().getFirst().members().get(1);
    var node = property.node();
    var receiver =
        new Name(
            "user",
            new DeclaredType("User", List.of()),
            node,
            Optional.of(
                new SymbolId("main", "demo.C", SymbolKind.LOCAL, "user", "Ldemo/User;", 0)));
    var read = new Member(receiver, "name", STRING, node, Optional.of(property.descriptor()));
    var write = new Assignment(read, new Literal("\" Bob \"", STRING, node), STRING, node);
    var method =
        new BoundMethod(
            node,
            "public",
            List.of(),
            "void",
            "update",
            List.of(),
            List.of(new BoundExpressionStatement(write)));
    var changed =
        new BoundCompilationUnit(
            unit.sourceFile(),
            unit.packageName(),
            unit.imports(),
            List.of(new BoundClass(node, "public", "User", List.of(property, method))));
    String java = emit(changed).files().getFirst().javaText();
    assertTrue(java.contains("user.setName(\" Bob \")"));
    assertFalse(java.contains("raw.getRaw"));
  }

  @Test
  void unsupportedAndTypeMismatchFailClosed() {
    var unit = fixture();
    var property = (BoundProperty) unit.classes().getFirst().members().get(1);
    var node = property.node();
    var integer = new PrimitiveType("int");
    var bad =
        new Assignment(
            new Name("x", STRING, node, Optional.of(property.descriptor().property())),
            new Literal("1", integer, node),
            STRING,
            node);
    var method =
        new BoundMethod(
            node,
            "public",
            List.of(),
            "void",
            "bad",
            List.of(),
            List.of(new BoundExpressionStatement(bad)));
    var changed =
        new BoundCompilationUnit(
            unit.sourceFile(),
            "demo",
            List.of(),
            List.of(new BoundClass(node, "public", "User", List.of(method))));
    assertTrue(
        new EarlyTypeChecker()
            .check(changed).stream().anyMatch(d -> d.code().value().equals("TY-TYP-0001")));
    var compound =
        new Binary(
            "+=",
            new Name("x", integer, node, Optional.of(property.descriptor().property())),
            new Literal("1", integer, node),
            integer,
            node);
    var badMethod =
        new BoundMethod(
            node,
            "public",
            List.of(),
            "void",
            "bad",
            List.of(),
            List.of(new BoundExpressionStatement(compound)));
    var unsupported =
        new BoundCompilationUnit(
            unit.sourceFile(),
            "demo",
            List.of(),
            List.of(new BoundClass(node, "public", "User", List.of(badMethod))));
    assertTrue(
        new EarlyTypeChecker()
            .check(unsupported).stream().anyMatch(d -> d.code().value().equals("TY-DEV-0001")));
    assertFalse(emit(unsupported).diagnostics().isEmpty());
  }
}
