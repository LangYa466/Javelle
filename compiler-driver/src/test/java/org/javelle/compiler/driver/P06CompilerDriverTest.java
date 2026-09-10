/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.javelle.compiler.core.budget.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class P06CompilerDriverTest {
  @TempDir Path root;

  private ResourceBudget budget() {
    return new ResourceBudget(2_000_000, 20_000, 20_000, 100, 200, Long.MAX_VALUE);
  }

  private CompileRequest request(String javelle, List<Path> java, Path generated, Path classes) {
    return new CompileRequest(
        List.of(new SourceInput("demo/User.javelle", javelle.getBytes(StandardCharsets.UTF_8))),
        java,
        List.of(),
        List.of(),
        generated,
        classes,
        21,
        false,
        List.of(),
        budget());
  }

  private String userSource() {
    return "package demo\nclass User {\nString name {\nget\nset(value) {\nfield = value.trim()\n}\n}\nString greet(String who) {\nreturn who\n}\n}\n";
  }

  @Test
  void frontendRawUtf16DiagnosticIsConvertedToCodePointLocation() {
    String bad = "// 😀\r\nclass Bad {\r\nint x = 1;\r\n}\r\n";
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(bad, List.of(), root.resolve("generated"), root.resolve("classes")),
                CancellationToken.none());
    assertFalse(result.success());
    JavacMessage message = result.diagnostics().getFirst();
    assertEquals("JV-SYN-0001", message.code().value());
    assertEquals(
        org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT,
        message.originalLocations().getFirst().range().unit());
    assertEquals(
        bad.codePointCount(0, bad.indexOf(';')),
        message.originalLocations().getFirst().range().startOffset());
  }

  @Test
  void realMixedCompilationPublishesManifestAndRunsInSeparateJvm() throws Exception {
    Path consumer = root.resolve("src/demo/Consumer.java");
    Files.createDirectories(consumer.getParent());
    Files.writeString(
        consumer,
        "package demo; public class Consumer { public static void main(String[] a) { User u=new User(); u.setName(\" Ada \" ); System.out.print(u.greet(u.getName())); } }");
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(consumer), generated, classes),
                CancellationToken.none());
    assertTrue(result.success(), result.diagnostics().toString());
    assertEquals(0, result.javacExitCode());
    assertTrue(Files.isRegularFile(generated.resolve("demo/User.java")));
    assertTrue(Files.isRegularFile(classes.resolve("demo/User.class")));
    String manifest = Files.readString(result.outputs().orElseThrow().ownershipManifest());
    assertTrue(manifest.contains("\"schemaVersion\":1"));
    assertTrue(manifest.contains("demo/User.java"));
    assertFalse(Files.readString(generated.resolve("demo/User.java")).contains(root.toString()));
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
        "Ada", new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  @Test
  @Tag("native-jdk21")
  void nativeJdk21RunsTheRealDriverPipeline() throws Exception {
    assertEquals(21, Runtime.version().feature());
    realMixedCompilationPublishesManifestAndRunsInSeparateJvm();
  }

  @Test
  void javacFailureIsStructuredAndDoesNotPublishCandidate() throws Exception {
    Path bad = root.resolve("src/demo/Bad.java");
    Files.createDirectories(bad.getParent());
    Files.writeString(bad, "package demo; class Bad { String x = new User().name; }");
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(bad), generated, classes), CancellationToken.none());
    assertFalse(result.success());
    assertEquals(1, result.javacExitCode(), result.diagnostics().toString());
    assertTrue(result.outputs().isEmpty());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(m -> m.severity() == org.javelle.compiler.core.diagnostic.Severity.ERROR));
    JavacMessage javaOnly = result.diagnostics().getFirst();
    assertEquals("Bad.java", javaOnly.generatedUri());
    assertTrue(javaOnly.originalLocations().isEmpty());
    assertFalse(Files.exists(generated.resolve("demo/User.java")));
    assertFalse(Files.exists(classes.resolve("demo/User.class")));
  }

  @Test
  void cancellationReleaseAndProcessorGuardsFailBeforePublication() {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    CompileRequest valid = request(userSource(), List.of(), generated, classes);
    assertFalse(new DefaultCompilerDriver().compile(valid, () -> true).success());
    var processors =
        new CompileRequest(
            valid.javelleSources(),
            List.of(),
            List.of(),
            List.of(),
            generated,
            classes,
            21,
            true,
            List.of(),
            budget());
    assertFalse(
        new DefaultCompilerDriver().compile(processors, CancellationToken.none()).success());
    var future =
        new CompileRequest(
            valid.javelleSources(),
            List.of(),
            List.of(),
            List.of(),
            generated,
            classes,
            99,
            false,
            List.of(),
            budget());
    assertFalse(new DefaultCompilerDriver().compile(future, CancellationToken.none()).success());
    assertFalse(Files.exists(generated));
    assertFalse(Files.exists(classes));
  }

  @Test
  void staleOwnedFilesAreRemovedWhileForeignFilesSurvive() throws Exception {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    var driver = new DefaultCompilerDriver();
    assertTrue(
        driver
            .compile(request(userSource(), List.of(), generated, classes), CancellationToken.none())
            .success());
    Path foreign = generated.resolve("keep.txt");
    Files.writeString(foreign, "foreign");
    String other = "package demo\nclass Other {\nString value\n}\n";
    var second =
        new CompileRequest(
            List.of(new SourceInput("demo/Other.javelle", other.getBytes(StandardCharsets.UTF_8))),
            List.of(),
            List.of(),
            List.of(),
            generated,
            classes,
            21,
            false,
            List.of(),
            budget());
    CompileResult result = driver.compile(second, CancellationToken.none());
    assertTrue(result.success(), result.diagnostics().toString());
    assertFalse(Files.exists(generated.resolve("demo/User.java")));
    assertFalse(Files.exists(classes.resolve("demo/User.class")));
    assertEquals("foreign", Files.readString(foreign));
  }

  @Test
  void foreignCollisionAndNestedOutputsAreRejected() throws Exception {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    Files.createDirectories(generated.resolve("demo"));
    Files.writeString(generated.resolve("demo/User.java"), "foreign");
    CompileResult collision =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(), generated, classes), CancellationToken.none());
    assertFalse(collision.success());
    assertEquals("foreign", Files.readString(generated.resolve("demo/User.java")));
    CompileResult nested =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(), generated, generated.resolve("classes")),
                CancellationToken.none());
    assertFalse(nested.success());
  }

  @Test
  void release25AndRepeatedCompilationAreDeterministic() throws Exception {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    CompileRequest base = request(userSource(), List.of(), generated, classes);
    var release25 =
        new CompileRequest(
            base.javelleSources(),
            List.of(),
            List.of(),
            List.of(),
            generated,
            classes,
            25,
            false,
            List.of(),
            budget());
    var driver = new DefaultCompilerDriver();
    CompileResult first = driver.compile(release25, CancellationToken.none());
    assertTrue(first.success(), first.diagnostics().toString());
    byte[] java = Files.readAllBytes(generated.resolve("demo/User.java"));
    String manifest = Files.readString(first.outputs().orElseThrow().ownershipManifest());
    CompileResult second = driver.compile(release25, CancellationToken.none());
    assertTrue(second.success(), second.diagnostics().toString());
    assertArrayEquals(java, Files.readAllBytes(generated.resolve("demo/User.java")));
    assertEquals(manifest, Files.readString(second.outputs().orElseThrow().ownershipManifest()));
    try (var children = Files.list(root)) {
      assertTrue(
          children.noneMatch(
              path -> path.getFileName().toString().startsWith(".javelle-compile-")));
    }
  }

  @Test
  void generatedJavacErrorMapsBackToJavelleOrigin() {
    String invalidJavaName = "class Bad {\nvoid name {\nget\n}\n}\n";
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(
                    invalidJavaName, List.of(), root.resolve("generated"), root.resolve("classes")),
                CancellationToken.none());
    assertFalse(result.success());
    assertEquals(1, result.javacExitCode(), result.diagnostics().toString());
    JavacMessage javac =
        result.diagnostics().stream()
            .filter(message -> message.code().value().startsWith("JV-JAVAC-"))
            .findFirst()
            .orElseThrow();
    assertEquals("Bad.java", javac.generatedUri());
    assertFalse(javac.originalLocations().isEmpty(), javac.toString());
    assertEquals(
        "demo/User.javelle", javac.originalLocations().getFirst().source().workspaceRelativeUri());
    assertTrue(result.outputs().isEmpty());
  }

  @Test
  void publishFaultRollsBackBothRootsAndManifest() throws Exception {
    for (String phase :
        List.of(
            "after-stale-cleanup",
            "after-generated-move",
            "after-classes-move",
            "before-manifest",
            "after-manifest")) {
      Path transaction = root.resolve(phase);
      Path generated = transaction.resolve("generated");
      Path classes = transaction.resolve("classes");
      var normal = new DefaultCompilerDriver();
      CompileRequest original = request(userSource(), List.of(), generated, classes);
      assertTrue(normal.compile(original, CancellationToken.none()).success());
      byte[] oldJava = Files.readAllBytes(generated.resolve("demo/User.java"));
      byte[] oldClass = Files.readAllBytes(classes.resolve("demo/User.class"));
      String oldManifest =
          Files.readString(classes.resolve("META-INF/javelle/generated-files-v1.json"));
      String other = "package demo\nclass Other {\nString value\n}\n";
      CompileRequest replacement =
          new CompileRequest(
              List.of(
                  new SourceInput("demo/Other.javelle", other.getBytes(StandardCharsets.UTF_8))),
              List.of(),
              List.of(),
              List.of(),
              generated,
              classes,
              21,
              false,
              List.of(),
              budget());
      CompileResult failed =
          new DefaultCompilerDriver(
                  step -> {
                    if (step.equals(phase)) throw new IllegalStateException("fault " + phase);
                  })
              .compile(replacement, CancellationToken.none());
      assertFalse(failed.success(), phase);
      assertArrayEquals(oldJava, Files.readAllBytes(generated.resolve("demo/User.java")), phase);
      assertArrayEquals(oldClass, Files.readAllBytes(classes.resolve("demo/User.class")), phase);
      assertEquals(
          oldManifest,
          Files.readString(classes.resolve("META-INF/javelle/generated-files-v1.json")),
          phase);
      assertFalse(Files.exists(generated.resolve("demo/Other.java")), phase);
      assertFalse(Files.exists(classes.resolve("demo/Other.class")), phase);
      try (var children = Files.list(transaction)) {
        assertTrue(
            children.noneMatch(
                path -> path.getFileName().toString().startsWith(".javelle-compile-")),
            phase);
      }
    }
  }

  @Test
  void corruptManifestAndDescendantSymlinkFailClosed() throws Exception {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    Files.createDirectories(classes.resolve("META-INF/javelle"));
    Files.writeString(classes.resolve("META-INF/javelle/generated-files-v1.json"), "corrupt");
    CompileResult corrupt =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(), generated, classes), CancellationToken.none());
    assertFalse(corrupt.success());
    assertEquals(
        "corrupt", Files.readString(classes.resolve("META-INF/javelle/generated-files-v1.json")));

    Files.delete(classes.resolve("META-INF/javelle/generated-files-v1.json"));
    Path outside = root.resolve("outside");
    Files.createDirectories(outside);
    Files.createDirectories(generated);
    Files.createSymbolicLink(generated.resolve("demo"), outside);
    CompileResult symlink =
        new DefaultCompilerDriver()
            .compile(
                request(userSource(), List.of(), generated, classes), CancellationToken.none());
    assertFalse(symlink.success());
    assertTrue(Files.list(outside).findAny().isEmpty());
  }

  @Test
  void changedOwnedOutputAndOutputBudgetFailWithoutPublication() throws Exception {
    Path generated = root.resolve("generated");
    Path classes = root.resolve("classes");
    var driver = new DefaultCompilerDriver();
    CompileRequest normal = request(userSource(), List.of(), generated, classes);
    assertTrue(driver.compile(normal, CancellationToken.none()).success());
    Path ownedJava = generated.resolve("demo/User.java");
    Files.writeString(ownedJava, "changed by user");
    CompileResult changed = driver.compile(normal, CancellationToken.none());
    assertFalse(changed.success());
    assertEquals("changed by user", Files.readString(ownedJava));

    Path limitedGenerated = root.resolve("limited-generated");
    Path limitedClasses = root.resolve("limited-classes");
    ResourceBudget limited = new ResourceBudget(800, 20_000, 20_000, 100, 200, Long.MAX_VALUE);
    CompileRequest capped =
        new CompileRequest(
            normal.javelleSources(),
            List.of(),
            List.of(),
            List.of(),
            limitedGenerated,
            limitedClasses,
            21,
            false,
            List.of(),
            limited);
    CompileResult exceeded = driver.compile(capped, CancellationToken.none());
    assertFalse(exceeded.success());
    assertTrue(
        exceeded.diagnostics().stream()
            .anyMatch(message -> message.code().value().equals("JV-RESOURCE-0001")));
    assertFalse(Files.exists(limitedGenerated.resolve("demo/User.java")));
    assertFalse(Files.exists(limitedClasses.resolve("demo/User.class")));
  }

  @Test
  void multipleJavacDiagnosticsMapAcrossCrLfEmojiAndUnicodeEscape() {
    String source =
        "class Bad {\r\nString emoji = \"😀\"\r\nvoid first {\r\nget\r\n}\r\nvoid \\u0073econd {\r\nget\r\n}\r\n}\r\n";
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(source, List.of(), root.resolve("generated"), root.resolve("classes")),
                CancellationToken.none());
    assertFalse(result.success());
    assertEquals(1, result.javacExitCode(), result.diagnostics().toString());
    var mapped =
        result.diagnostics().stream()
            .filter(message -> message.code().value().startsWith("JV-JAVAC-"))
            .filter(message -> !message.originalLocations().isEmpty())
            .toList();
    assertTrue(mapped.size() >= 2, result.diagnostics().toString());
    assertEquals(
        List.of(
            new org.javelle.compiler.core.source.TextRange(
                org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT, 180, 198),
            new org.javelle.compiler.core.source.TextRange(
                org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT, 259, 278)),
        mapped.stream().map(JavacMessage::generatedRange).toList());
    assertEquals(
        List.of(
            new org.javelle.compiler.core.source.TextRange(
                org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT, 33, 53),
            new org.javelle.compiler.core.source.TextRange(
                org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT, 55, 81)),
        mapped.stream().map(message -> message.originalLocations().getFirst().range()).toList());
    assertTrue(
        mapped.stream()
                .flatMap(message -> message.originalLocations().stream())
                .map(location -> location.range().startOffset())
                .distinct()
                .count()
            >= 2,
        mapped.toString());
    for (JavacMessage message : mapped) {
      assertEquals("Bad.java", message.generatedUri());
      assertEquals(
          org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT,
          message.generatedRange().unit());
      assertTrue(message.generatedRange().endOffset() >= message.generatedRange().startOffset());
      assertEquals(
          org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT,
          message.originalLocations().getFirst().range().unit());
      assertEquals(
          "demo/User.javelle",
          message.originalLocations().getFirst().source().workspaceRelativeUri());
    }
  }

  @Test
  void missingTypeAndSyntheticClassDiagnosticsUseMappedOwners() {
    for (String source :
        List.of("class Bad {\nMissing value\n}\n", "class _ {\nString value\n}\n")) {
      CompileResult result =
          new DefaultCompilerDriver()
              .compile(
                  request(
                      source,
                      List.of(),
                      root.resolve("generated-" + source.hashCode()),
                      root.resolve("classes-" + source.hashCode())),
                  CancellationToken.none());
      assertFalse(result.success());
      assertEquals(1, result.javacExitCode(), result.diagnostics().toString());
      JavacMessage mapped =
          result.diagnostics().stream()
              .filter(message -> message.code().value().equals("JV-JAVAC-ERROR"))
              .filter(message -> !message.originalLocations().isEmpty())
              .findFirst()
              .orElseThrow(() -> new AssertionError(result.diagnostics().toString()));
      assertEquals(
          "demo/User.javelle",
          mapped.originalLocations().getFirst().source().workspaceRelativeUri());
      assertEquals(
          org.javelle.compiler.core.source.OffsetUnit.UNICODE_CODE_POINT,
          mapped.originalLocations().getFirst().range().unit());
    }
  }

  @Test
  void missingSymbolInReturnedExpressionMapsToExactExpressionRange() {
    String source = "class Bad {\nMissing make() {\nreturn new Missing()\n}\n}\n";
    CompileResult result =
        new DefaultCompilerDriver()
            .compile(
                request(source, List.of(), root.resolve("generated"), root.resolve("classes")),
                CancellationToken.none());
    assertFalse(result.success());
    assertEquals(1, result.javacExitCode(), result.diagnostics().toString());
    int expressionStart = source.codePointCount(0, source.indexOf("new Missing()"));
    int expressionEnd =
        expressionStart + "new Missing()".codePointCount(0, "new Missing()".length());
    assertTrue(
        result.diagnostics().stream()
            .flatMap(message -> message.originalLocations().stream())
            .anyMatch(
                location ->
                    location.range().startOffset() == expressionStart
                        && location.range().endOffset() == expressionEnd),
        result.diagnostics().toString());
  }
}
