package dev.teyru.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.diagnostic.*;
import dev.teyru.compiler.core.lowering.*;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.sourcemap.*;
import dev.teyru.compiler.core.symbol.*;
import dev.teyru.compiler.core.syntax.*;
import org.junit.jupiter.api.Test;

class CoreModelTest {
  private static ResourceBudget budget() {
    return new ResourceBudget(
        1_000_000, 1000, 1000, 100, 100, System.nanoTime() + Duration.ofSeconds(30).toNanos());
  }

  private static SourceId id(String name, String text) {
    return SourceId.forContent(name, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void sourceDefensivelyCopiesAndFingerprintIsStable() {
    byte[] b = "中文☕".getBytes(StandardCharsets.UTF_8);
    var s = SourceFile.decode(id("a.teyru", "中文☕"), b, budget());
    b[0] = 0;
    assertEquals("中文☕", s.rawText());
    assertNotSame(s.utf8(), s.utf8());
  }

  @Test
  void lineMapRoundTripsLfCrCrLfAndEof() {
    var s =
        SourceFile.decode(
            id("a.teyru", "a\r\nb\rc\n"),
            "a\r\nb\rc\n".getBytes(StandardCharsets.UTF_8),
            budget());
    for (int o : new int[] {0, 1, 3, 4, 5, 6, 7})
      assertEquals(o, s.rawLineMap().offsetOf(s.rawLineMap().positionOf(o)));
    assertThrows(IllegalArgumentException.class, () -> s.rawLineMap().positionOf(2));
  }

  @Test
  void rejectsMiddleOfSurrogate() {
    var s =
        SourceFile.decode(id("a.teyru", "😀"), "😀".getBytes(StandardCharsets.UTF_8), budget());
    assertThrows(IllegalArgumentException.class, () -> s.rawLineMap().positionOf(1));
  }

  @Test
  void unicodeEscapeMapsRawAndTranslated() {
    var s =
        SourceFile.decode(
            id("a.teyru", "a\\u000ab"), "a\\u000ab".getBytes(StandardCharsets.UTF_8), budget());
    assertEquals("a\nb", s.translatedText());
    assertEquals(7, s.unicodeMap().rawBoundary(2, Bias.END));
    assertEquals(1, s.unicodeMap().translatedBoundary(2, Bias.START));
  }

  @Test
  void coordinateGoldensCoverBomTextBlocksBytesCodePointsAndEscapedSurrogates() {
    String text = "\ufeffString block = \"\"\"\r\n中文😀\r\n\"\"\"\n";
    var source =
        SourceFile.decode(
            id("coordinates.teyru", text), text.getBytes(StandardCharsets.UTF_8), budget());
    for (int codePoint = 0; codePoint <= text.codePointCount(0, text.length()); codePoint++) {
      int bytes =
          source.convertBoundary(
              codePoint, OffsetUnit.UNICODE_CODE_POINT, OffsetUnit.UTF8_BYTE, Bias.START);
      assertEquals(
          codePoint,
          source.convertBoundary(
              bytes, OffsetUnit.UTF8_BYTE, OffsetUnit.UNICODE_CODE_POINT, Bias.START));
    }
    assertEquals(0, source.rawLineMap().positionOf(0).line());
    assertTrue(source.rawText().startsWith("\ufeff"));
    var escaped =
        SourceFile.decode(
            id("escaped.teyru", "\\uD83D\\uDE00"),
            "\\uD83D\\uDE00".getBytes(StandardCharsets.UTF_8),
            budget());
    assertEquals("😀", escaped.translatedText());
    assertThrows(
        IllegalArgumentException.class, () -> escaped.unicodeMap().rawBoundary(1, Bias.START));
    assertThrows(
        IllegalArgumentException.class,
        () -> source.convertBoundary(1, OffsetUnit.UTF8_BYTE, OffsetUnit.RAW_UTF16, Bias.START));
  }

  @Test
  void invalidUtf8AndBudgetFail() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SourceFile.decode(
                SourceId.forContent("a", new byte[] {(byte) 0xc0}),
                new byte[] {(byte) 0xc0},
                budget()));
    assertThrows(
        ResourceLimitException.class,
        () ->
            SourceFile.decode(
                SourceId.forContent("a", new byte[2]),
                new byte[2],
                new ResourceBudget(1, 1, 1, 1, 1, 1)));
  }

  @Test
  void uriTraversalCorpusRejected() {
    for (String x :
        List.of("../x", "%2e%2e/x", "/x", "C:/x", "a\\b", "a!/../b", "a?b", "a#b", "//host/x"))
      assertThrows(IllegalArgumentException.class, () -> new SourceId(x, "0".repeat(64)), x);
  }

  @Test
  void rangesCheckUnitsAndOverflow() {
    assertThrows(IllegalArgumentException.class, () -> new TextRange(OffsetUnit.RAW_UTF16, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TextRange(OffsetUnit.RAW_UTF16, 2, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TextRange(OffsetUnit.RAW_UTF16, 0, 1)
                .intersects(new TextRange(OffsetUnit.UTF8_BYTE, 0, 1)));
  }

  @Test
  void tokenCollectionsAndNodeIndexAreImmutable() {
    var source = id("a", "x");
    var r = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    var n = new NodeId(source, "Name", r, 0);
    var token =
        new Token(
            TokenKind.IDENTIFIER,
            r,
            new TextRange(OffsetUnit.TRANSLATED_UTF16, 0, 1),
            "x",
            "x",
            new ArrayList<>(),
            List.of(),
            false);
    var node = new CstToken(n, r, token, Optional.empty());
    var index = new NodeIndex(List.of(node));
    assertSame(node, index.find(n).orElseThrow());
    assertThrows(UnsupportedOperationException.class, () -> index.ordered().add(node));
  }

  @Test
  void missingTokenMustBeZeroWidth() {
    var r = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> new Token(TokenKind.ERROR, r, r, "", "", List.of(), List.of(), true));
  }

  @Test
  void diagnosticSerializationAndDataOrderDeterministic() {
    var source = id("a", "x");
    var r = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    var a =
        new Diagnostic(
            1,
            new DiagnosticCode("JVL-TEST"),
            Severity.ERROR,
            source,
            r,
            "localized ignored",
            List.of(),
            Map.of("z", "1", "a", "2"));
    assertEquals(a.toCanonicalJson(), a.toCanonicalJson());
    assertEquals(List.of("a", "z"), new ArrayList<>(a.data().keySet()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Diagnostic(2, a.code(), a.severity(), source, r, "x", List.of(), Map.of()));
  }

  @Test
  void symbolPropertyAndOriginInvariants() {
    var source = id("a", "x");
    var r = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    var node = new NodeId(source, "Property", r, 0);
    var symbol = new SymbolId("m", "p.C", SymbolKind.PROPERTY, "x", "Ljava/lang/String;", 0);
    var origin =
        new GeneratedMemberOrigin(
            source, node, GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION, "property");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PropertyDescriptor(
                symbol,
                new DeclaredType("String", List.of()),
                PropertyDescriptor.PropertyKind.STORED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                origin,
                1));
  }

  @Test
  void sourceMapFindsSeparatePropertyExpansions() {
    var source = id("a", "property");
    var original = new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 8);
    var node = new NodeId(source, "Property", new TextRange(OffsetUnit.RAW_UTF16, 0, 8), 0);
    var location = new SourceLocation(source, original);
    var segments =
        List.of(
            propertySegment("C.java", 0, 3, location, node, "property.field"),
            propertySegment("C.java", 10, 15, location, node, "property.getter"),
            propertySegment("C.java", 20, 25, location, node, "property.setter"));
    var map = new SourceMap(segments);
    assertEquals(
        "property.field",
        map.generatedAt("C.java", 1).getFirst().memberOrigin().orElseThrow().featureId());
    assertEquals(
        "property.getter",
        map.generatedAt("C.java", 11).getFirst().memberOrigin().orElseThrow().featureId());
    assertEquals(
        "property.setter",
        map.generatedAt("C.java", 21).getFirst().memberOrigin().orElseThrow().featureId());
    assertEquals(3, map.originalAt(location).size());
    byte[] encoded = SourceMapCodec.write(map);
    assertArrayEquals(encoded, SourceMapCodec.write(map));
    try {
      var decoded = SourceMapCodec.read(encoded, 3);
      assertEquals(map.segments(), decoded.segments());
      assertEquals(map.schemaVersion(), decoded.schemaVersion());
    } catch (java.io.IOException e) {
      fail(e);
    }
  }

  @Test
  void syntheticOriginRequiresReason() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SourceOrigin(MappingKind.SYNTHETIC, List.of(), ""));
  }

  @Test
  void cancellationAndDeadlineAreStable() {
    assertThrows(CancellationException.class, ((CancellationToken) () -> true)::throwIfCancelled);
    assertThrows(
        ResourceLimitException.class, () -> new ResourceBudget(1, 1, 1, 1, 1, 1).checkDeadline(2));
  }

  @Test
  void everyResourceCounterFailsClosed() {
    var b = new ResourceBudget(1, 1, 1, 1, 1, Long.MAX_VALUE);
    var t = new ResourceTracker(b, CancellationToken.none(), () -> 0L);
    t.addBytes(1);
    t.token();
    t.node();
    t.diagnostic();
    t.nesting(1);
    assertThrows(ResourceLimitException.class, t::token);
    assertThrows(ResourceLimitException.class, t::node);
    assertThrows(ResourceLimitException.class, t::diagnostic);
    assertThrows(ResourceLimitException.class, () -> t.nesting(2));
  }

  @Test
  void sourceMapCompositionAndLargeIndexAreFunctional() {
    var original = id("source.teyru", "x");
    var intermediate = id("Middle.java", "x");
    var node = new NodeId(original, "X", new TextRange(OffsetUnit.RAW_UTF16, 0, 1), 0);
    var first =
        new SourceMap(
            1,
            Map.of("Middle.java", intermediate.contentSha256()),
            List.of(
                new SourceMapSegment(
                    new GeneratedRange(
                        "Middle.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 10)),
                    List.of(
                        new SourceLocation(
                            original, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 1))),
                    MappingKind.DIRECT,
                    node,
                    Optional.empty(),
                    0,
                    "")));
    var second =
        new SourceMap(
            List.of(
                new SourceMapSegment(
                    new GeneratedRange(
                        "Final.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 100, 110)),
                    List.of(
                        new SourceLocation(
                            intermediate, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 2, 3))),
                    MappingKind.EXPANDED,
                    node,
                    Optional.of(
                        new GeneratedMemberOrigin(
                            original,
                            node,
                            GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION,
                            "property")),
                    0,
                    "")));
    var result = first.compose(second);
    assertEquals(
        original, result.generatedAt("Final.java", 105).getFirst().originals().getFirst().source());
    assertEquals(MappingKind.EXPANDED, result.segments().getFirst().kind());
    var many = new ArrayList<SourceMapSegment>();
    for (int i = 0; i < 10_000; i++)
      many.add(
          segment(
              "Large.java",
              i * 2,
              i * 2 + 1,
              new SourceLocation(original, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 1)),
              node));
    assertEquals(1, new SourceMap(many).generatedAt("Large.java", 1234).size());
  }

  @Test
  void sourceMapOverlapOrderingIsDeterministic() {
    var source = id("source.teyru", "abcdefghij");
    var node = new NodeId(source, "X", new TextRange(OffsetUnit.RAW_UTF16, 0, 1), 0);
    var location = new SourceLocation(source, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 1));
    var member =
        new GeneratedMemberOrigin(
            source, node, GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION, "p");
    var direct =
        new SourceMapSegment(
            new GeneratedRange("G.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 10)),
            List.of(location),
            MappingKind.DIRECT,
            node,
            Optional.empty(),
            1,
            "");
    var expanded =
        new SourceMapSegment(
            new GeneratedRange("G.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 2, 8)),
            List.of(location),
            MappingKind.EXPANDED,
            node,
            Optional.of(member),
            1,
            "expanded");
    var higherPriority =
        new SourceMapSegment(
            new GeneratedRange("G.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 3, 4)),
            List.of(location),
            MappingKind.DIRECT,
            node,
            Optional.empty(),
            0,
            "");
    var map = new SourceMap(List.of(expanded, direct, higherPriority));
    assertEquals(List.of(higherPriority, direct, expanded), map.generatedAt("G.java", 3));
    assertEquals(
        List.of(higherPriority, direct, expanded),
        map.generatedOverlapping(
            new GeneratedRange("G.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 3, 4))));
    assertThrows(
        java.io.IOException.class, () -> SourceMapCodec.read(SourceMapCodec.write(map), 2));
  }

  @Test
  void sourceMapReverseIndexZeroWidthAndCompositionIdentityFailClosed() {
    var original = id("source.teyru", "origin");
    var intermediate = id("Middle.java", "middle");
    var node = new NodeId(original, "Anchor", new TextRange(OffsetUnit.RAW_UTF16, 0, 0), 0);
    var anchor = new SourceLocation(original, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 4, 4));
    var member =
        new GeneratedMemberOrigin(
            original, node, GeneratedMemberOrigin.OriginKind.COMPILER_SYNTHETIC, "helper");
    var first =
        new SourceMap(
            1,
            Map.of("Middle.java", intermediate.contentSha256()),
            List.of(
                new SourceMapSegment(
                    new GeneratedRange(
                        "Middle.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 2, 2)),
                    List.of(anchor),
                    MappingKind.SYNTHETIC,
                    node,
                    Optional.of(member),
                    0,
                    "synthetic helper")));
    assertEquals(1, first.originalAt(anchor).size());
    var wrongIntermediate = id("Middle.java", "wrong");
    var second =
        new SourceMap(
            List.of(
                new SourceMapSegment(
                    new GeneratedRange(
                        "Final.java", new TextRange(OffsetUnit.UNICODE_CODE_POINT, 1, 2)),
                    List.of(
                        new SourceLocation(
                            wrongIntermediate, new TextRange(OffsetUnit.UNICODE_CODE_POINT, 2, 3))),
                    MappingKind.DIRECT,
                    node,
                    Optional.empty(),
                    0,
                    "")));
    assertThrows(IllegalArgumentException.class, () -> first.compose(second));
    assertThrows(IllegalArgumentException.class, () -> new SourceMap(2, Map.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceMapSegment(
                new GeneratedRange("Final.java", new TextRange(OffsetUnit.RAW_UTF16, 0, 1)),
                List.of(anchor),
                MappingKind.DIRECT,
                node,
                Optional.empty(),
                0,
                ""));
  }

  @Test
  void publicApiDoesNotExposePlatformTypes() {
    for (var type :
        List.of(
            SourceFile.class,
            NodeIndex.class,
            Diagnostic.class,
            AbiProjection.class,
            SourceMap.class,
            ResourceTracker.class))
      for (var method : type.getMethods()) {
        String signature = method.toGenericString();
        assertFalse(
            signature.contains("org.gradle")
                || signature.contains("com.intellij")
                || signature.contains("org.eclipse.lsp4j"),
            signature);
      }
  }

  @Test
  void diagnosticJsonRoundTripsAndRejectsHostileInputs() {
    var source = id("a.teyru", "abc");
    var range = new TextRange(OffsetUnit.RAW_UTF16, 0, 1);
    var edit = new TextEdit(source, range, "z");
    var fix = new Fix("f", "fix", List.of(edit));
    var d =
        new Diagnostic(
            1,
            new DiagnosticCode("JVL-TEST"),
            Severity.ERROR,
            source,
            range,
            "m\n",
            List.of(new RelatedInformation(source, range, "r")),
            List.of(fix),
            Map.of("z", "v", "a", "b"));
    byte[] json = DiagnosticJson.write(d);
    assertEquals(d, DiagnosticJson.read(json, 10_000, 20, Map.of(source, 3)));
    String withUnknown =
        new String(json, StandardCharsets.UTF_8).replaceFirst("\\{", "{\"future\":true,");
    assertEquals(
        d,
        DiagnosticJson.read(
            withUnknown.getBytes(StandardCharsets.UTF_8), 10_000, 20, Map.of(source, 3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticJson.read("{}".getBytes(StandardCharsets.UTF_8), 10, 4, Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DiagnosticJson.read(
                "{\"schemaVersion\":1,\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8),
                100,
                4,
                Map.of()));
    assertThrows(
        IllegalArgumentException.class, () -> DiagnosticJson.read(json, 2, 20, Map.of(source, 3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticJson.read(json, 10_000, 1, Map.of(source, 3)));
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticJson.read(json, 10_000, 20, Map.of(source, 0)));
  }

  @Test
  void classfileConstantPoolAndApiGoldenGate() throws Exception {
    Path classes =
        Path.of(
            Objects.requireNonNull(SourceFile.class.getProtectionDomain().getCodeSource())
                .getLocation()
                .toURI());
    var api = new TreeSet<String>();
    try (var files = Files.walk(classes.resolve("dev/teyru/compiler/core"))) {
      for (Path classFile : files.filter(path -> path.toString().endsWith(".class")).toList()) {
        byte[] bytes = Files.readAllBytes(classFile);
        dev.teyru.compiler.core.analysis.ClassfilePolicy.rejectPlatformReferences(bytes);
        String name =
            classes
                .relativize(classFile)
                .toString()
                .replace('/', '.')
                .replace('\\', '.')
                .replaceFirst("\\.class$", "");
        Class<?> type = Class.forName(name, false, getClass().getClassLoader());
        if (!Modifier.isPublic(type.getModifiers())) continue;
        api.add("T " + type.toGenericString());
        Arrays.stream(type.getDeclaredConstructors())
            .filter(
                c -> Modifier.isPublic(c.getModifiers()) || Modifier.isProtected(c.getModifiers()))
            .map(c -> "C " + c.toGenericString())
            .forEach(api::add);
        Arrays.stream(type.getDeclaredMethods())
            .filter(
                m -> Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()))
            .map(m -> "M " + m.toGenericString())
            .forEach(api::add);
        Arrays.stream(type.getDeclaredFields())
            .filter(
                f -> Modifier.isPublic(f.getModifiers()) || Modifier.isProtected(f.getModifiers()))
            .map(f -> "F " + f.toGenericString())
            .forEach(api::add);
      }
    }
    String canonical = String.join("\n", api) + "\n";
    String actualHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    String golden =
        new String(
                Objects.requireNonNull(getClass().getResourceAsStream("/public-api-v1.txt"))
                    .readAllBytes(),
                StandardCharsets.UTF_8)
            .trim();
    assertEquals(
        golden,
        "sha256=" + actualHash + "\nentries=" + api.size(),
        "complete public API changed (addition, deletion, or signature change)");
    byte[] bytes =
        Files.readAllBytes(classes.resolve("dev/teyru/compiler/core/source/SourceFile.class"));
    byte[] corrupt = bytes.clone();
    corrupt[0] = 0;
    assertThrows(
        java.io.IOException.class,
        () -> dev.teyru.compiler.core.analysis.ClassfilePolicy.utf8Constants(corrupt));
  }

  private static SourceMapSegment segment(String u, int a, int b, SourceLocation l, NodeId n) {
    return new SourceMapSegment(
        new GeneratedRange(u, new TextRange(OffsetUnit.UNICODE_CODE_POINT, a, b)),
        List.of(l),
        MappingKind.EXPANDED,
        n,
        Optional.of(
            new GeneratedMemberOrigin(
                l.source(), n, GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION, "property")),
        0,
        "property");
  }

  private static SourceMapSegment propertySegment(
      String uri, int start, int end, SourceLocation location, NodeId node, String featureId) {
    return new SourceMapSegment(
        new GeneratedRange(uri, new TextRange(OffsetUnit.UNICODE_CODE_POINT, start, end)),
        List.of(location),
        MappingKind.EXPANDED,
        node,
        Optional.of(
            new GeneratedMemberOrigin(
                location.source(),
                node,
                GeneratedMemberOrigin.OriginKind.PROPERTY_EXPANSION,
                featureId)),
        0,
        featureId);
  }
}
