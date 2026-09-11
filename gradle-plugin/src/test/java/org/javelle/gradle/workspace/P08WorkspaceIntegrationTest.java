/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle.workspace;

import static org.javelle.workspace.model.WorkspaceModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.javelle.workspace.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P08WorkspaceIntegrationTest {
  private static final String H = "a".repeat(64);

  @TempDir Path temporary;

  @Test
  void capturesRealGradleMainTestAndMissingJavelleRootsWithoutTaskWiring() {
    var root =
        ProjectBuilder.builder()
            .withName("fixture")
            .withProjectDir(temporary.resolve("fixture").toFile())
            .build();
    var app =
        ProjectBuilder.builder()
            .withName("app")
            .withParent(root)
            .withProjectDir(temporary.resolve("fixture/app").toFile())
            .build();
    var lib =
        ProjectBuilder.builder()
            .withName("lib")
            .withParent(root)
            .withProjectDir(temporary.resolve("fixture/lib").toFile())
            .build();
    app.getPluginManager().apply("java");
    lib.getPluginManager().apply("java");
    var input = new GradleWorkspaceInputAdapter().capturePortable(root);
    var model = new WorkspaceModelExporter().export(input);
    assertEquals(List.of("app", "lib"), model.modules().stream().map(WorkspaceModule::id).toList());
    assertEquals(
        List.of("main", "test"),
        model.modules().getFirst().sourceSets().stream().map(SourceSet::name).toList());
    assertTrue(
        model.modules().stream()
            .flatMap(x -> x.sourceSets().stream())
            .flatMap(x -> x.javelleRoots().stream())
            .noneMatch(PathRef::exists));
    assertDoesNotThrow(() -> new DefaultWorkspaceModelCodec().writePortable(model));
  }

  @Test
  void exportsPortableMultiModuleMainTestEmptyAndMissingRoots() {
    WorkspaceModel model = new WorkspaceModelExporter().export(fixture());
    assertEquals(List.of("app", "lib"), model.modules().stream().map(WorkspaceModule::id).toList());
    assertEquals(
        List.of("main", "test"),
        model.modules().getFirst().sourceSets().stream().map(SourceSet::name).toList());
    assertTrue(model.modules().get(1).sourceSets().getFirst().javaRoots().isEmpty());
    assertFalse(
        model.modules().getFirst().sourceSets().getFirst().javelleRoots().getFirst().exists());
    var bytes = new DefaultWorkspaceModelCodec().writePortable(model);
    var result = new DefaultWorkspaceModelCodec().read(bytes, WorkspaceReadOptions.defaults());
    assertTrue(result.success(), result.diagnostics().toString());
    assertEquals(model, result.model().orElseThrow());
  }

  @Test
  void builtJarSupportsIndependentCompileAndSeparateProcessRoundTrip() throws Exception {
    Path jar = Path.of(System.getProperty("workspaceModelJar"));
    assertTrue(Files.isRegularFile(jar), jar.toString());
    byte[] json =
        new DefaultWorkspaceModelCodec()
            .writePortable(new WorkspaceModelExporter().export(fixture()));
    Path input = temporary.resolve("workspace.json");
    Files.write(input, json);
    Path source = temporary.resolve("ExternalWorkspaceClient.java");
    Files.writeString(
        source,
        """
        import java.nio.file.*;
        import org.javelle.workspace.model.*;
        public class ExternalWorkspaceClient {
          public static void main(String[] args) throws Exception {
            byte[] input = Files.readAllBytes(Path.of(args[0]));
            var codec = new DefaultWorkspaceModelCodec();
            var result = codec.read(input, WorkspaceReadOptions.defaults());
            if (!result.success()) throw new AssertionError(result.diagnostics());
            if (result.model().orElseThrow().modules().size() != 2) throw new AssertionError();
            if (!java.util.Arrays.equals(input, codec.writePortable(result.model().orElseThrow())))
              throw new AssertionError("round-trip changed bytes");
            System.out.print("EXTERNAL_WORKSPACE_OK");
          }
        }
        """,
        StandardCharsets.UTF_8);
    Path classes = Files.createDirectory(temporary.resolve("classes"));
    int javac =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "--release",
                "21",
                "-cp",
                jar.toString(),
                "-d",
                classes.toString(),
                source.toString());
    assertEquals(0, javac);
    var process =
        new ProcessBuilder(
                javaExecutable(),
                "-cp",
                classes + System.getProperty("path.separator") + jar,
                "ExternalWorkspaceClient",
                input.toString())
            .redirectErrorStream(true)
            .start();
    assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.exitValue(), output);
    assertEquals("EXTERNAL_WORKSPACE_OK", output);
  }

  private static WorkspaceExportInput fixture() {
    var trust =
        new TrustPolicy(TrustLevel.UNTRUSTED, false, false, false, false, DecisionSource.DEFAULT);
    var emptyPaths = new CompilationPaths(List.of(), List.of(), List.of(), List.of(), List.of());
    var main =
        new SourceSet(
            "main",
            List.of(path("app/src/main/java", false)),
            List.of(path("app/src/main/javelle", false)),
            List.of(path("app/build/generated", false)),
            List.of(path("app/build/classes", false)),
            emptyPaths,
            new Releases(21, 21, 21),
            List.of("-Xlint:all"),
            "UTF-8",
            trust,
            Optional.of(H));
    var test =
        new SourceSet(
            "test",
            List.of(path("app/src/test/java", false)),
            List.of(),
            List.of(),
            List.of(path("app/build/test-classes", false)),
            emptyPaths,
            new Releases(21, 21, 21),
            List.of(),
            "UTF-8",
            trust,
            Optional.of(H));
    var empty =
        new SourceSet(
            "main",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            emptyPaths,
            new Releases(21, 21, 21),
            List.of(),
            "UTF-8",
            trust,
            Optional.of(H));
    var tool =
        new Toolchain(
            new PathRef(
                "toolchains/jdk21/bin/java",
                "sha256:" + H,
                Optional.empty(),
                Optional.empty(),
                PathKind.JDK_EXECUTABLE,
                false,
                SymlinkPolicy.REJECT,
                Optional.of(H)),
            "21.0.8",
            "fixture",
            H);
    var app =
        new WorkspaceExportInput.ModuleInput(
            "app",
            ":app",
            List.of(main, test),
            List.of(
                new ModuleDependency(
                    "lib",
                    DependencyScope.COMPILE,
                    DependencyKind.BUILD_ORDER,
                    Optional.of("main"))),
            tool,
            List.of(),
            Map.of());
    var lib =
        new WorkspaceExportInput.ModuleInput(
            "lib", ":lib", List.of(empty), List.of(), tool, List.of(), Map.of());
    return new WorkspaceExportInput(
        0,
        SerializationKind.PORTABLE,
        new WorkspaceDescriptor(
            "fixture", ".", Optional.empty(), CaseSensitivity.SENSITIVE, "UTF-8", trust),
        List.of(app, lib),
        List.of(),
        new ModelLimits(1_000_000, 10, 10, 100, 10, 100),
        Map.of());
  }

  private static PathRef path(String logical, boolean exists) {
    return new PathRef(
        logical,
        "sha256:" + H,
        Optional.empty(),
        Optional.empty(),
        PathKind.DIRECTORY,
        exists,
        SymlinkPolicy.REJECT,
        Optional.of(H));
  }

  private static String javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }
}
