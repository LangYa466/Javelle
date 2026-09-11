/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.ArrayList;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("p09-testkit")
class P09GradlePluginTest {
  @TempDir Path project;

  @Test
  void packagedPluginGeneratesCompilesJarsAndIsUpToDate() throws Exception {
    writeSettings("consumer");
    String executable = System.getProperty("javelleExecutable").replace("\\", "\\\\");
    Path marker = project.resolve("compiler-ran.marker");
    Path wrapper = project.resolve("javelle-wrapper");
    Files.writeString(
        wrapper, "#!/bin/sh\nprintf ran > '" + marker + "'\nexec '" + executable + "' \"$@\"\n");
    wrapper.toFile().setExecutable(true);
    String junitFiles =
        java.util.Arrays.stream(
                System.getProperty("javelleJUnitFiles").split(java.io.File.pathSeparator))
            .map(x -> "file(\"" + x.replace("\\", "\\\\") + "\")")
            .collect(java.util.stream.Collectors.joining(","));
    Files.writeString(
        project.resolve("build.gradle.kts"),
        """
        plugins { java; application; id("org.javelle") }
        java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
        javelle { compilerExecutable.set(file("%s")); targetRelease.set(21) }
        application { mainClass.set("demo.Main") }
        repositories { mavenCentral() }
        dependencies { testImplementation(files(%s)) }
        tasks.test { useJUnitPlatform() }
        """
            .formatted(wrapper.toString(), junitFiles));
    runner("help").build();
    assertFalse(Files.exists(marker), "configuration must not launch the compiler");
    Path source = project.resolve("src/main/javelle/demo/User.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source, "package demo\nclass User {\nHelper echo(Helper helper) {\nreturn helper\n}\n}\n");
    Path extra = project.resolve("src/main/javelle/demo/Extra.javelle");
    Files.writeString(extra, "package demo\nclass Extra {\nString value\n}\n");
    Path main = project.resolve("src/main/java/demo/Main.java");
    Files.createDirectories(main.getParent());
    Files.writeString(
        main,
        "package demo; public class Main { public static void main(String[] a) { System.out.print(new User().echo(new Helper()).value()); } }");
    Files.writeString(
        main.getParent().resolve("Helper.java"),
        "package demo; public final class Helper { public String value() { return \"OK\"; } }");
    Path testSource = project.resolve("src/test/javelle/demo/TestOnly.javelle");
    Files.createDirectories(testSource.getParent());
    Files.writeString(testSource, "package demo\nclass TestOnly {\nString value\n}\n");
    Path javaTest = project.resolve("src/test/java/demo/UserTest.java");
    Files.createDirectories(javaTest.getParent());
    Files.writeString(
        javaTest,
        "package demo; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; class UserTest { @Test void joint() { assertEquals(\"OK\", new User().echo(new Helper()).value()); } }");

    var first = runner("jar", "test", "run", "--configuration-cache").build();
    assertEquals(TaskOutcome.SUCCESS, first.task(":generateJavelleJava").getOutcome());
    assertEquals(TaskOutcome.SUCCESS, first.task(":generateTestJavelleJava").getOutcome());
    assertTrue(first.getOutput().contains("OK"));
    assertTrue(Files.isRegularFile(marker));
    assertTrue(
        Files.isRegularFile(
            project.resolve("build/generated/sources/javelle/main/demo/User.java")));
    assertTrue(Files.isRegularFile(project.resolve("build/javelle/workspace/main.json")));
    String mainModel = Files.readString(project.resolve("build/javelle/workspace/main.json"));
    String testModel = Files.readString(project.resolve("build/javelle/workspace/test.json"));
    assertTrue(mainModel.contains("\"javaVersion\":\"21\""));
    assertFalse(mainModel.contains(System.getProperty("user.dir")));
    assertFalse(testModel.contains(System.getProperty("user.dir")));
    assertTrue(
        Files.isRegularFile(
            project.resolve("build/javelle/source-maps/main/demo/User.java.map.json")));
    assertTrue(Files.isRegularFile(project.resolve("build/libs/consumer.jar")));
    assertFalse(Files.exists(project.resolve("src/main/javelle/demo/User.java")));
    var external =
        new ProcessBuilder(
                Path.of(System.getProperty("javelleJdk21Home"), "bin", "java").toString(),
                "-cp",
                project.resolve("build/classes/java/main").toString(),
                "demo.Main")
            .redirectErrorStream(true)
            .start();
    assertTrue(external.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(0, external.exitValue());
    assertEquals("OK", new String(external.getInputStream().readAllBytes()).trim());

    var second = runner("jar", "test", "run", "--configuration-cache").build();
    assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateJavelleJava").getOutcome());
    assertTrue(second.getOutput().contains("Reusing configuration cache"));

    Files.writeString(
        source,
        "package demo\nclass User {\nHelper echo(Helper helper) {\nreturn helper\n}\nString marker\n}\n");
    var changed = runner("jar").build();
    assertEquals(TaskOutcome.SUCCESS, changed.task(":generateJavelleJava").getOutcome());
    Path foreign = project.resolve("build/generated/sources/javelle/main/keep.foreign");
    Files.writeString(foreign, "keep");
    Files.delete(extra);
    runner("jar").build();
    assertFalse(
        Files.exists(project.resolve("build/generated/sources/javelle/main/demo/Extra.java")));
    assertEquals("keep", Files.readString(foreign));
    assertFalse(Files.exists(project.resolve("build/classes/java/main/demo/Extra.class")));
    Files.delete(source);
    runner("generateJavelleJava").build();
    assertFalse(
        Files.exists(project.resolve("build/generated/sources/javelle/main/demo/User.java")));
    assertFalse(
        Files.exists(project.resolve("build/javelle/source-maps/main/demo/User.java.map.json")));
    assertEquals("keep", Files.readString(foreign));
  }

  @Test
  void selectedJdk25LauncherAndRelease25AreRecorded() throws Exception {
    writeSettings("jdk25");
    String executable = System.getProperty("javelleExecutable").replace("\\", "\\\\");
    Files.writeString(
        project.resolve("build.gradle.kts"),
        "plugins { java; id(\"org.javelle\") }\n"
            + "java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }\n"
            + "javelle { compilerExecutable.set(file(\""
            + executable
            + "\")); targetRelease.set(25) }\n");
    Path source = project.resolve("src/main/javelle/Jdk25.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "class Jdk25 {\nString value\n}\n");
    runner("generateJavelleJava").build();
    String model = Files.readString(project.resolve("build/javelle/workspace/main.json"));
    assertTrue(model.contains("\"javaVersion\":\"25\""));
    assertTrue(model.contains("\"release\":25"));
  }

  @Test
  void compilationFailureAndOwnershipAttacksPreserveAcceptedOutputs() throws Exception {
    writeSettings("negative-matrix");
    String executable = System.getProperty("javelleExecutable").replace("\\", "\\\\");
    Files.writeString(
        project.resolve("build.gradle.kts"),
        "plugins { java; id(\"org.javelle\") }\n"
            + "javelle { compilerExecutable.set(file(\""
            + executable
            + "\")); targetRelease.set(21) }\n");
    Path source = project.resolve("src/main/javelle/p/A.javelle");
    Files.createDirectories(source.getParent());
    String good = "package p\nclass A {\nString value\n}\n";
    Files.writeString(source, good);
    runner("classes").build();
    Path generated = project.resolve("build/generated/sources/javelle/main/p/A.java");
    byte[] accepted = Files.readAllBytes(generated);

    Files.writeString(source, "package p\nclass A {\nint value = 1;\n}\n");
    var syntax = runner("generateJavelleJava").buildAndFail();
    assertTrue(syntax.getOutput().contains("A.javelle"));
    assertTrue(syntax.getOutput().contains("JV-SYN"));
    assertArrayEquals(accepted, Files.readAllBytes(generated));

    Files.writeString(source, good + "\n");
    Files.writeString(generated, "foreign modification", StandardOpenOption.TRUNCATE_EXISTING);
    var modified = runner("generateJavelleJava").buildAndFail();
    assertTrue(modified.getOutput().contains("owned output changed"));
    assertEquals("foreign modification", Files.readString(generated));

    deleteTree(project.resolve("build/generated/sources/javelle/main"));
    deleteTree(project.resolve("build/javelle/source-maps/main"));
    runner("generateJavelleJava").build();
    Path link = project.resolve("build/generated/sources/javelle/main/link");
    Files.createSymbolicLink(link, project.resolve("outside"));
    Files.writeString(source, good + "\n\n");
    var symlink = runner("generateJavelleJava").buildAndFail();
    assertTrue(symlink.getOutput().contains("symlink rejected"));
    assertTrue(Files.isSymbolicLink(link));

    Files.delete(link);
    Path collision = project.resolve("build/generated/sources/javelle/main/p/B.java");
    Files.writeString(collision, "foreign");
    Files.writeString(
        source.getParent().resolve("B.javelle"), "package p\nclass B {\nString x\n}\n");
    var collided = runner("generateJavelleJava").buildAndFail();
    assertTrue(collided.getOutput().contains("foreign output collision"));
    assertEquals("foreign", Files.readString(collision));
  }

  @Test
  void everyDeclaredCompilerInputInvalidatesGeneration() throws Exception {
    writeSettings("invalidation");
    String real = System.getProperty("javelleExecutable");
    Path compiler = project.resolve("compiler");
    Files.writeString(compiler, "#!/bin/sh\nexec '" + real + "' \"$@\"\n");
    compiler.toFile().setExecutable(true);
    Path jar = project.resolve("lib/input.jar");
    Files.createDirectories(jar.getParent());
    writeJar(jar, "one");
    Path java = project.resolve("src/main/java/p/Marker.java");
    Files.createDirectories(java.getParent());
    Files.writeString(java, "package p; class Marker {}\n");
    Path source = project.resolve("src/main/javelle/p/A.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "package p\nclass A {\nString value\n}\n");
    writeInvalidationBuild(compiler, 21, "--no-color");
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    assertEquals(
        TaskOutcome.UP_TO_DATE,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    Files.writeString(java, "package p; class Marker { int changed; }\n");
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    writeJar(jar, "two");
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    Files.writeString(compiler, "# changed\n", StandardOpenOption.APPEND);
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    writeInvalidationBuild(compiler, 25, "--no-color");
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
    writeInvalidationBuild(compiler, 25, "--quiet");
    assertEquals(
        TaskOutcome.SUCCESS,
        runner("generateJavelleJava").build().task(":generateJavelleJava").getOutcome());
  }

  @Test
  void missingCompilerFailsBeforePublishing() throws Exception {
    writeSettings("negative");
    Files.writeString(
        project.resolve("build.gradle.kts"),
        "plugins { java; id(\"org.javelle\") }\njavelle { compilerExecutable.set(file(\"missing\")) }\n");
    Path source = project.resolve("src/main/javelle/Broken.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "class Broken {}\n");
    var failed = runner("generateJavelleJava").buildAndFail();
    assertTrue(
        failed.getOutput().contains("Javelle compiler is not executable")
            || failed.getOutput().contains("Input file does not exist"));
    assertFalse(Files.exists(project.resolve("build/generated/sources/javelle/main")));
  }

  private GradleRunner runner(String... arguments) {
    var args = new ArrayList<>(java.util.List.of(arguments));
    args.add("--offline");
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withArguments(args)
        .withTestKitDir(Path.of(System.getProperty("user.home"), ".gradle", "testkit").toFile())
        .forwardOutput();
  }

  private void writeSettings(String name) throws Exception {
    String root = System.getProperty("javelleRepoRoot").replace("\\", "\\\\");
    Files.writeString(
        project.resolve("settings.gradle.kts"),
        "pluginManagement { includeBuild(\"" + root + "\") }\nrootProject.name=\"" + name + "\"\n");
    Files.writeString(
        project.resolve("gradle.properties"),
        "org.gradle.java.installations.paths="
            + System.getProperty("javelleJdk21Home")
            + ","
            + System.getProperty("javelleJdk25Home")
            + "\norg.gradle.java.installations.auto-download=false\n");
  }

  private void writeInvalidationBuild(Path compiler, int release, String option) throws Exception {
    Files.writeString(
        project.resolve("build.gradle.kts"),
        "plugins { java; id(\"org.javelle\") }\n"
            + "java { toolchain.languageVersion.set(JavaLanguageVersion.of("
            + (release == 25 ? 25 : 21)
            + ")) }\n"
            + "dependencies { implementation(files(\"lib/input.jar\")) }\n"
            + "javelle { compilerExecutable.set(file(\""
            + compiler
            + "\")); targetRelease.set("
            + release
            + "); compilerOptions.set(listOf(\""
            + option
            + "\")) }\n");
  }

  private static void writeJar(Path jar, String value) throws Exception {
    try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
      var entry = new java.util.jar.JarEntry("value.txt");
      entry.setTime(0);
      out.putNextEntry(entry);
      out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static void deleteTree(Path root) throws Exception {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }
}
