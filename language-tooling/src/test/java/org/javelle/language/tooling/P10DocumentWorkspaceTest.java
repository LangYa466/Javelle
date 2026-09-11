/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.language.tooling;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.javelle.workspace.model.*;
import org.junit.jupiter.api.Test;

class P10DocumentWorkspaceTest {
  @Test
  void resolvedRootsClasspathAndToolchainDriveSymbols() throws Exception {
    Path temp = Files.createTempDirectory("p10-workspace-");
    Path root = Files.createDirectories(temp.resolve("src"));
    Files.writeString(root.resolve("RootSymbol.java"), "public class RootSymbol {}\n");
    Path depSource = temp.resolve("ExternalOnly.java");
    Files.writeString(depSource, "public class ExternalOnly { public void fromJar() {} }\n");
    Path depClasses = Files.createDirectories(temp.resolve("dep-classes"));
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "--release",
                "21",
                "-d",
                depClasses.toString(),
                depSource.toString()));
    Path jar = temp.resolve("external.jar");
    try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
      out.putNextEntry(new JarEntry("ExternalOnly.class"));
      out.write(Files.readAllBytes(depClasses.resolve("ExternalOnly.class")));
      out.closeEntry();
    }
    var model = resolvedModel(temp, root, jar);
    var validation =
        new DefaultWorkspaceModelValidator().validate(model, ValidationEnvironment.noIo());
    assertTrue(validation.isEmpty(), validation.toString());
    try (var docs = new DocumentWorkspace()) {
      docs.configure(model);
      String uri = temp.resolve("Use.javelle").toUri().toASCIIString();
      String text = "class Use { RootSymbol root ExternalOnly ext }";
      docs.analyze(docs.open(uri, 1, text));
      var symbols = docs.completion(uri, new DocumentWorkspace.Position(0, 12));
      assertTrue(symbols.contains("RootSymbol"));
      assertTrue(symbols.contains("ExternalOnly"), symbols.toString());
      assertEquals(
          "RootSymbol `RootSymbol`",
          docs.hover(uri, new DocumentWorkspace.Position(0, 15)).orElseThrow());
      assertTrue(symbols.contains("fromJar"));
    }
    var set = model.modules().getFirst().sourceSets().getFirst();
    var withoutClasspath =
        replace(
            model,
            new WorkspaceModel.SourceSet(
                set.name(),
                set.javaRoots(),
                set.javelleRoots(),
                set.generatedJavaRoots(),
                set.generatedClassRoots(),
                new WorkspaceModel.CompilationPaths(
                    List.of(), List.of(), List.of(), List.of(), List.of()),
                set.releases(),
                set.compilerOptions(),
                set.encoding(),
                set.trust(),
                set.onDiskSnapshotFingerprint()),
            model.modules().getFirst().toolchain());
    assertSymbols(withoutClasspath, temp, true, false);
    var withoutRoot =
        replace(
            model,
            new WorkspaceModel.SourceSet(
                set.name(),
                List.of(),
                set.javelleRoots(),
                set.generatedJavaRoots(),
                set.generatedClassRoots(),
                set.paths(),
                set.releases(),
                set.compilerOptions(),
                set.encoding(),
                set.trust(),
                set.onDiskSnapshotFingerprint()),
            model.modules().getFirst().toolchain());
    assertSymbols(withoutRoot, temp, false, true);
    var incompatibleToolchain =
        new WorkspaceModel.Toolchain(
            model.modules().getFirst().toolchain().executable(), "17.0.1", "test", "b".repeat(64));
    try (var docs = new DocumentWorkspace()) {
      docs.configure(replace(model, set, incompatibleToolchain));
      var snapshot =
          docs.open(temp.resolve("BadTool.javelle").toUri().toString(), 1, "class BadTool {} ");
      assertThrows(IllegalArgumentException.class, () -> docs.analyze(snapshot));
    }
  }

  @Test
  void validatedWorkspacePropertyMetadataSeedsSemanticIndexWithoutFabrication() throws Exception {
    var decoded =
        new DefaultWorkspaceModelCodec()
            .read(
                Files.readAllBytes(
                    Path.of("../workspace-model/src/test/resources/workspace-portable-v1.json")),
                WorkspaceReadOptions.defaults());
    assertTrue(decoded.success(), decoded.diagnostics().toString());
    try (var docs = new DocumentWorkspace()) {
      docs.configure(decoded.model().orElseThrow());
      String uri = "file:///workspace/Use.javelle";
      docs.analyze(docs.open(uri, 1, "class Use { String name }"));
      assertTrue(docs.completion(uri, new DocumentWorkspace.Position(0, 19)).contains("name"));
      assertEquals(
          "Ljava/lang/String; property `demo.User.name`",
          docs.hover(uri, new DocumentWorkspace.Position(0, 20)).orElseThrow());
      assertTrue(docs.hover(uri, new DocumentWorkspace.Position(0, 10)).isEmpty());
    }
  }

  @Test
  void cooperativeCancellationStopsAtInjectedCompilerStageCheckpoint() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    try (var docs =
        new DocumentWorkspace(
            () -> {
              if (calls.incrementAndGet() == 2) Thread.currentThread().interrupt();
            })) {
      var snapshot = docs.open("file:///workspace/Cancel.javelle", 1, "class Cancel {} ");
      assertThrows(CancellationException.class, () -> docs.analyze(snapshot));
      assertTrue(Thread.interrupted());
    }
  }

  @Test
  void compilerBackedSyntaxDiagnosticUsesRawUtf16() {
    try (var docs = new DocumentWorkspace()) {
      var snapshot =
          docs.open(
              "file:///workspace/Fix.javelle", 1, "class A {\r\n  String s = \"😀\";\r\n}\r\n");
      var messages = docs.analyze(snapshot);
      assertFalse(messages.isEmpty());
      assertEquals(1, messages.getFirst().range().start().line());
    }
  }

  @Test
  void utf16CrLfIncrementalChangeIsAtomicAndRejectsSurrogateInterior() {
    try (var docs = new DocumentWorkspace()) {
      String uri = "file:///workspace/Test.javelle", text = "a😀b\r\nsecond";
      docs.open(uri, 1, text);
      assertEquals(4, DocumentWorkspace.offset(text, new DocumentWorkspace.Position(0, 4)));
      assertEquals(6, DocumentWorkspace.offset(text, new DocumentWorkspace.Position(1, 0)));
      assertThrows(
          IllegalArgumentException.class,
          () -> DocumentWorkspace.offset(text, new DocumentWorkspace.Position(0, 2)));
      var changed =
          docs.change(
                  uri,
                  2,
                  List.of(
                      new DocumentWorkspace.Change(
                          Optional.of(
                              new DocumentWorkspace.Range(
                                  new DocumentWorkspace.Position(0, 1),
                                  new DocumentWorkspace.Position(0, 3))),
                          "X")))
              .orElseThrow();
      assertEquals("aXb\r\nsecond", changed.text());
      assertTrue(
          docs.change(uri, 1, List.of(new DocumentWorkspace.Change(Optional.empty(), "stale")))
              .isEmpty());
      assertEquals(changed, docs.current(uri).orElseThrow());
      assertEquals(changed, docs.close(uri).orElseThrow());
      assertTrue(docs.current(uri).isEmpty());
    }
  }

  private static WorkspaceModel resolvedModel(Path base, Path sourceRoot, Path jar)
      throws Exception {
    var decoded =
        new DefaultWorkspaceModelCodec()
            .read(
                Files.readAllBytes(
                    Path.of("../workspace-model/src/test/resources/workspace-portable-v1.json")),
                WorkspaceReadOptions.defaults())
            .model()
            .orElseThrow();
    var oldModule = decoded.modules().getFirst();
    var oldSet = oldModule.sourceSets().getFirst();
    var rootRef =
        resolved(oldSet.javaRoots().getFirst(), sourceRoot, WorkspaceModel.PathKind.DIRECTORY);
    var jarRef =
        resolved(oldSet.paths().compileClasspath().getFirst(), jar, WorkspaceModel.PathKind.JAR);
    var paths =
        new WorkspaceModel.CompilationPaths(
            List.of(jarRef), List.of(), List.of(), List.of(), List.of());
    int release = Math.min(21, Runtime.version().feature());
    var set =
        new WorkspaceModel.SourceSet(
            oldSet.name(),
            List.of(rootRef),
            List.of(),
            List.of(),
            List.of(),
            paths,
            new WorkspaceModel.Releases(release, release, release),
            List.of(),
            "UTF-8",
            oldSet.trust(),
            Optional.empty());
    var executable =
        resolved(
            oldModule.toolchain().executable(),
            Path.of(System.getProperty("java.home"), "bin", "java"),
            WorkspaceModel.PathKind.JDK_EXECUTABLE);
    var tool =
        new WorkspaceModel.Toolchain(
            executable,
            Runtime.version().feature() + ".0",
            System.getProperty("java.vendor"),
            "a".repeat(64));
    var module =
        new WorkspaceModel.WorkspaceModule(
            oldModule.id(),
            oldModule.logicalProjectPath(),
            List.of(set),
            List.of(),
            tool,
            List.of(),
            Map.of());
    var root =
        resolved(oldModule.toolchain().executable(), base, WorkspaceModel.PathKind.DIRECTORY);
    var descriptor =
        new WorkspaceModel.WorkspaceDescriptor(
            "test",
            ".",
            Optional.of(root),
            WorkspaceModel.CaseSensitivity.SENSITIVE,
            "UTF-8",
            decoded.workspace().trust());
    var preliminary =
        new WorkspaceModel(
            decoded.schemaVersion(),
            WorkspaceModel.SerializationKind.RESOLVED,
            descriptor,
            List.of(module),
            List.of(),
            decoded.fingerprints(),
            decoded.limits(),
            Map.of());
    return new WorkspaceModel(
        preliminary.schemaVersion(),
        preliminary.serializationKind(),
        preliminary.workspace(),
        preliminary.modules(),
        preliminary.overlays(),
        WorkspaceModelFingerprinter.compute(preliminary),
        preliminary.limits(),
        preliminary.extensions());
  }

  private static WorkspaceModel.PathRef resolved(
      WorkspaceModel.PathRef old, Path path, WorkspaceModel.PathKind kind) throws Exception {
    String uri = path.toRealPath().toUri().toASCIIString();
    return new WorkspaceModel.PathRef(
        old.logicalPath(),
        old.relocatableKey(),
        Optional.of(uri),
        Optional.of(uri),
        kind,
        true,
        WorkspaceModel.SymlinkPolicy.REJECT,
        Optional.empty());
  }

  private static WorkspaceModel replace(
      WorkspaceModel base, WorkspaceModel.SourceSet set, WorkspaceModel.Toolchain toolchain) {
    var old = base.modules().getFirst();
    var module =
        new WorkspaceModel.WorkspaceModule(
            old.id(),
            old.logicalProjectPath(),
            List.of(set),
            old.dependencies(),
            toolchain,
            old.propertyMetadata(),
            old.extensions());
    var preliminary =
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            base.workspace(),
            List.of(module),
            base.overlays(),
            base.fingerprints(),
            base.limits(),
            base.extensions());
    return new WorkspaceModel(
        preliminary.schemaVersion(),
        preliminary.serializationKind(),
        preliminary.workspace(),
        preliminary.modules(),
        preliminary.overlays(),
        WorkspaceModelFingerprinter.compute(preliminary),
        preliminary.limits(),
        preliminary.extensions());
  }

  private static void assertSymbols(
      WorkspaceModel model, Path temp, boolean rootExpected, boolean externalExpected) {
    try (var docs = new DocumentWorkspace()) {
      docs.configure(model);
      String uri = temp.resolve(UUID.randomUUID() + ".javelle").toUri().toString();
      docs.analyze(docs.open(uri, 1, "class Use { RootSymbol root ExternalOnly ext }"));
      var symbols = docs.completion(uri, new DocumentWorkspace.Position(0, 1));
      assertEquals(rootExpected, symbols.contains("RootSymbol"));
      assertEquals(externalExpected, symbols.contains("ExternalOnly"));
    }
  }
}
