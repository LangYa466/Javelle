/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class P07BlackBoxTest {
  @TempDir Path root;
  Path launcher;

  @BeforeEach
  void launcherExists() {
    launcher = Path.of(System.getProperty("javelle.launcher"));
    assertTrue(Files.isExecutable(launcher));
  }

  @Test
  void packagedVersionHelpDoctorAndUnavailableAreStable() throws Exception {
    assertResult(0, "Javelle 0.1.0 (language 1, Java 21-25)\n", "", run("--version"));
    assertTrue(run("--help").out.contains("emit-java"));
    var doctor = run("doctor", "--format", "json");
    assertEquals(0, doctor.exit);
    assertTrue(doctor.out.startsWith("{\"schemaVersion\":1,\"command\":\"doctor\""));
    assertFalse(doctor.out.contains("\u001b["));
    var explanation = run("explain", "JV-COMP-0002");
    assertEquals(0, explanation.exit, explanation.err);
    assertTrue(explanation.out.contains("Compilation/processor rounds did not converge"));
    assertResult(
        6, "", "JV-CLI-NOT-AVAILABLE: migrate is not available in this version\n", run("migrate"));
  }

  @Test
  void checkCompileAndEmitJavaUsePackagedDriver() throws Exception {
    Path source = root.resolve("來源 空間 😀/User.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(source, validSource());
    assertResult(0, "check succeeded\n", "", run("check", source.toString()));
    Path compiled = root.resolve("compiled");
    var compile = run("compile", source.toString(), "--output", compiled.toString());
    assertEquals(0, compile.exit, compile.err);
    assertTrue(Files.isRegularFile(compiled.resolve("generated/demo/User.java")));
    assertTrue(Files.isRegularFile(compiled.resolve("classes/demo/User.class")));
    Path emitted = root.resolve("emitted");
    var emit = run("emit-java", source.toString(), "--output", emitted.toString());
    assertEquals(0, emit.exit, emit.err);
    assertTrue(Files.isRegularFile(emitted.resolve("demo/User.java")));
    assertTrue(Files.isRegularFile(emitted.resolve("demo/User.java.map.json")));
    assertTrue(Files.isRegularFile(emitted.resolve("META-INF/javelle/generated-files-v1.json")));
    assertFalse(Files.exists(emitted.resolve("demo/User.class")));
    Path jsonOutput = root.resolve("json-emitted");
    var jsonEmit =
        run(
            "emit-java",
            source.toString(),
            "--output",
            jsonOutput.toString(),
            "--diagnostics",
            "json");
    assertEquals(0, jsonEmit.exit, jsonEmit.err);
    assertTrue(jsonEmit.out.startsWith("{\"schemaVersion\":1"));
    assertEquals(1, jsonEmit.out.lines().count());
    assertFalse(jsonEmit.out.contains(root.toString()));
    validateDiagnosticEnvelope(jsonEmit.out);
  }

  @Test
  void usageInputAndCompilationFailuresHaveDistinctCodes() throws Exception {
    assertEquals(2, run("unknown").exit);
    assertEquals(4, run("check", root.resolve("missing.javelle").toString()).exit);
    Path bad = root.resolve("Bad.javelle");
    Files.writeString(bad, "class Bad {\nint x = 1;\n}\n");
    var failure = run("check", bad.toString(), "--diagnostics", "json");
    assertEquals(3, failure.exit, failure.err + failure.out);
    assertTrue(failure.out.contains("JV-SYN-0001"));
    assertTrue(failure.out.contains("\"replacement\":\"\""));
    assertTrue(failure.out.endsWith("\n"));
    validateDiagnosticEnvelope(failure.out);
    var relocatedEnvironment =
        runEnv(
            Map.of("NO_COLOR", "1", "TZ", "Pacific/Honolulu", "LANG", "C"),
            "check",
            bad.toString(),
            "--diagnostics",
            "json",
            "--quiet");
    assertEquals(failure.out, relocatedEnvironment.out);
    assertEquals("", relocatedEnvironment.err);
  }

  @Test
  void projectAndStdinRemainExplicitlyUnavailable() throws Exception {
    assertEquals(4, run("check", "--project", "workspace.json").exit);
    assertEquals(6, run("check", "-").exit);
  }

  @Test
  void acceptedP08ProjectModelIsConsumedWithoutBuildEvaluation() throws Exception {
    Path project = root.resolve("model-project");
    Path source = project.resolve("src/main/javelle/User.javelle");
    Files.createDirectories(source.getParent());
    Files.writeString(source, validSource());
    Path model = project.resolve("workspace.json");
    try (var fixture =
        Objects.requireNonNull(getClass().getResourceAsStream("/workspace-portable-v1.json"))) {
      Files.copy(fixture, model);
    }
    var result = run("check", "--project", model.toString());
    assertEquals(0, result.exit, result.err + result.out);
  }

  @Test
  void timeoutInternalToolchainAndSignalUseFrozenExitCodesWithoutResidue() throws Exception {
    Path source = root.resolve("Signal.javelle");
    Files.writeString(source, "class Signal {}\n");
    assertEquals(124, run("check", source.toString(), "--timeout-ms", "0").exit);
    assertEquals(124, run("check", source.toString(), "--timeout-ms", "1").exit);
    assertEquals(4, run("check", source.toString(), "--release", "99").exit);
    assertEquals(4, run("doctor", "--jdk", root.resolve("missing-jdk").toString()).exit);
    var internal = runEnv(Map.of("JAVELLE_INTERNAL_TEST_FAULT", "1"), "check", source.toString());
    assertEquals(5, internal.exit);
    assertEquals("JV-CLI-INTERNAL: internal compiler error\n", internal.err);
    assertFalse(internal.err.contains("Exception"));

    Path tmp = root.resolve("signal-tmp");
    Files.createDirectories(tmp);
    var command = List.of(launcher.toString(), "check", source.toString());
    var builder = new ProcessBuilder(command).directory(root.toFile());
    builder.environment().put("JAVELLE_SIGNAL_TEST_HOLD_MILLIS", "60000");
    builder.environment().put("JAVA_OPTS", "-Djava.io.tmpdir=" + tmp);
    var process = builder.start();
    Thread.sleep(200);
    var killer = new ProcessBuilder("/bin/kill", "-INT", Long.toString(process.pid())).start();
    assertTrue(killer.waitFor(5, TimeUnit.SECONDS));
    assertEquals(0, killer.exitValue());
    assertTrue(process.waitFor(5, TimeUnit.SECONDS));
    assertEquals(130, process.exitValue());
    try (var children = Files.list(tmp)) {
      assertTrue(children.noneMatch(x -> x.getFileName().toString().startsWith("javelle-cli-")));
    }
  }

  @Test
  void failedReplacementPreservesAcceptedOutput() throws Exception {
    Path good = root.resolve("Good.javelle");
    Files.writeString(good, validSource());
    Path output = root.resolve("atomic-output");
    assertEquals(0, run("compile", good.toString(), "--output", output.toString()).exit);
    Path generated = output.resolve("generated/demo/User.java");
    byte[] accepted = Files.readAllBytes(generated);
    Path bad = root.resolve("Broken.javelle");
    Files.writeString(bad, "class Broken {\nint x = 1;\n}\n");
    assertEquals(3, run("compile", bad.toString(), "--output", output.toString()).exit);
    assertArrayEquals(accepted, Files.readAllBytes(generated));

    Path emitted = root.resolve("owned-emitted");
    assertEquals(0, run("emit-java", good.toString(), "--output", emitted.toString()).exit);
    Path foreign = emitted.resolve("keep.txt");
    Files.writeString(foreign, "foreign");
    assertEquals(0, run("emit-java", good.toString(), "--output", emitted.toString()).exit);
    assertEquals("foreign", Files.readString(foreign));
    Path symlink = root.resolve("symlink-output");
    Files.createSymbolicLink(symlink, emitted);
    assertEquals(4, run("emit-java", good.toString(), "--output", symlink.toString()).exit);
    assertFalse(Files.exists(root.resolve(".symlink-output.javelle-stage")));
    assertEquals(4, run("emit-java", good.toString(), "--output", "/proc/javelle-unwritable").exit);
    Path owned = emitted.resolve("demo/User.java");
    Files.writeString(owned, "changed");
    var refused = run("emit-java", good.toString(), "--output", emitted.toString());
    assertEquals(4, refused.exit);
    assertEquals("changed", Files.readString(owned));
    assertEquals("foreign", Files.readString(foreign));
  }

  @Test
  void doctorExecutesSelectedJavacAndRejectsFakeGarbageAndHang() throws Exception {
    Path good = fakeJdk("good", "echo 'javac 25.0.1' >&2\nexit 0");
    assertEquals(0, run("doctor", "--jdk", good.toString()).exit);
    for (String body : List.of("echo 'javac 99.0.0' >&2\nexit 0", "echo garbage\nexit 0")) {
      Path fake = fakeJdk(UUID.randomUUID().toString(), body);
      var result = run("doctor", "--jdk", fake.toString(), "--format", "json");
      assertEquals(4, result.exit);
      validateDiagnosticEnvelope(result.out);
    }
    Path hanging = fakeJdk("hang", "sleep 30\necho 'javac 25' >&2");
    long start = System.nanoTime();
    assertEquals(4, run("doctor", "--jdk", hanging.toString()).exit);
    assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 9);
  }

  @Test
  void manifestFingerprintFsyncFaultRollbackAndWindowsScriptAreInspectable() throws Exception {
    Path source = root.resolve("Publish.javelle");
    Files.writeString(source, validSource());
    Path output = root.resolve("published");
    assertEquals(0, run("emit-java", source.toString(), "--output", output.toString()).exit);
    Path manifest = output.resolve("META-INF/javelle/generated-files-v1.json");
    String acceptedManifest = Files.readString(manifest);
    assertTrue(acceptedManifest.matches("(?s).*\\\"inputFingerprint\\\":\\\"[0-9a-f]{64}\\\".*"));
    assertTrue(acceptedManifest.contains("\"kind\":\"SOURCE_MAP\""));
    byte[] acceptedJava = Files.readAllBytes(output.resolve("demo/User.java"));
    for (String phase : List.of("candidate", "write", "manifest", "fsync", "move")) {
      var failure =
          runEnv(
              Map.of("JAVELLE_PUBLISH_TEST_FAULT", phase),
              "emit-java",
              source.toString(),
              "--output",
              output.toString());
      assertEquals(4, failure.exit, phase + failure.err);
      assertEquals(acceptedManifest, Files.readString(manifest));
      assertArrayEquals(acceptedJava, Files.readAllBytes(output.resolve("demo/User.java")));
      try (var siblings = Files.list(output.getParent())) {
        assertTrue(
            siblings.noneMatch(
                p ->
                    p.getFileName().toString().contains(".javelle-stage-")
                        || p.getFileName().toString().contains(".javelle-backup-")));
      }
    }
    Path bat = launcher.resolveSibling("javelle.bat");
    assertTrue(Files.isRegularFile(bat));
    String script = Files.readString(bat);
    assertTrue(script.contains("org.javelle.compiler.cli.JavelleCli %*"));
    assertTrue(script.contains("-classpath \"%CLASSPATH%\""));
    assertFalse(script.toLowerCase(Locale.ROOT).contains("enabledelayedexpansion"));
    assertTrue(
        List.of("%", "!", "^", "&", "|", "<", ">").stream()
            .allMatch(x -> script.contains("%*") && !x.isEmpty()));
  }

  private Result run(String... args) throws Exception {
    return runEnv(Map.of(), args);
  }

  private Path fakeJdk(String name, String body) throws Exception {
    Path jdk = root.resolve("fake-jdk-" + name);
    Path javac = jdk.resolve("bin/javac");
    Files.createDirectories(javac.getParent());
    Files.writeString(javac, "#!/bin/sh\n" + body + "\n");
    assertTrue(javac.toFile().setExecutable(true));
    return jdk;
  }

  private void validateDiagnosticEnvelope(String json) throws Exception {
    Path document = root.resolve("diagnostics.json");
    Files.writeString(document, json);
    Path schema = root.resolve("diagnostics-v1.schema.json");
    Path validator = root.resolve("validate-json-schema.py");
    try (var input =
        Objects.requireNonNull(getClass().getResourceAsStream("/diagnostics-v1.schema.json"))) {
      Files.copy(input, schema, StandardCopyOption.REPLACE_EXISTING);
    }
    try (var input =
        Objects.requireNonNull(getClass().getResourceAsStream("/validate-json-schema.py"))) {
      Files.copy(input, validator, StandardCopyOption.REPLACE_EXISTING);
    }
    var process =
        new ProcessBuilder("python3", validator.toString(), schema.toString(), document.toString())
            .start();
    assertTrue(process.waitFor(10, TimeUnit.SECONDS));
    assertEquals(
        0,
        process.exitValue(),
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    for (String invalid :
        List.of(
            json.replaceFirst("\\\"schemaVersion\\\":1", "\"schemaVersion\":2"),
            json.replaceFirst("\\\"errors\\\":([0-9]+)", "\"errors\":\"$1\""),
            json.replaceFirst("\\\"command\\\":", "\"command\":\"check\",\"command\":"))) {
      Files.writeString(document, invalid);
      var rejected =
          new ProcessBuilder(
                  "python3", validator.toString(), schema.toString(), document.toString())
              .start();
      assertTrue(rejected.waitFor(10, TimeUnit.SECONDS));
      assertNotEquals(0, rejected.exitValue());
    }
  }

  private Result runEnv(Map<String, String> environment, String... args) throws Exception {
    var command = new ArrayList<String>();
    command.add(launcher.toString());
    command.addAll(List.of(args));
    var builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(false);
    builder.environment().putAll(environment);
    var process = builder.start();
    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
    return new Result(
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  private void assertResult(int exit, String out, String err, Result actual) {
    assertEquals(exit, actual.exit);
    assertEquals(out, actual.out);
    assertEquals(err, actual.err);
  }

  private String validSource() {
    return "package demo\nclass User {\nString name = \"\" {\nget\nset(value) {\nfield = value.trim()\n}\n}\n}\n";
  }

  private record Result(int exit, String out, String err) {}
}
