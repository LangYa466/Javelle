/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.testkit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestkitUtilitiesTest {
  @Test
  void workspaceRejectsTraversalAndCleansUp() throws Exception {
    java.nio.file.Path root;
    try (var workspace = TemporaryWorkspace.create("testkit-")) {
      root = workspace.root();
      Files.writeString(workspace.resolve("nested.txt"), "ok");
      assertThrows(IllegalArgumentException.class, () -> workspace.resolve("../escape"));
    }
    assertFalse(Files.exists(root));
  }

  @Test
  void deterministicArchiveHasStableBytesAndSortedEntries() throws Exception {
    try (var workspace = TemporaryWorkspace.create("archive-")) {
      var first = workspace.resolve("a.jar");
      var second = workspace.resolve("b.jar");
      Map<String, byte[]> entries = new LinkedHashMap<>();
      entries.put("z.txt", "z".getBytes(StandardCharsets.UTF_8));
      entries.put("a.txt", "a".getBytes(StandardCharsets.UTF_8));
      DeterministicArchive.write(first, entries);
      DeterministicArchive.write(second, entries);
      assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
      assertEquals(java.util.List.of("a.txt", "z.txt"), ArchiveInspector.validate(first, 2, 2));
    }
  }

  @Test
  void archiveRejectsTraversalEntry() throws Exception {
    try (var workspace = TemporaryWorkspace.create("archive-")) {
      assertThrows(
          IOException.class,
          () ->
              DeterministicArchive.write(
                  workspace.resolve("bad.jar"), Map.of("../escape", new byte[] {1})));
    }
  }

  @Test
  void archiveRejectsEntryAndExpansionLimits() throws Exception {
    try (var workspace = TemporaryWorkspace.create("archive-")) {
      var jar = workspace.resolve("limits.jar");
      DeterministicArchive.write(jar, Map.of("a", new byte[16], "b", new byte[16]));
      assertThrows(IOException.class, () -> ArchiveInspector.validate(jar, 1, 100));
      assertThrows(IOException.class, () -> ArchiveInspector.validate(jar, 5, 20));
    }
  }

  @Test
  void archiveRejectsSymlinkContainer() throws Exception {
    try (var workspace = TemporaryWorkspace.create("archive-")) {
      var real = workspace.resolve("real.jar");
      DeterministicArchive.write(real, Map.of("a", new byte[] {1}));
      var link = workspace.resolve("link.jar");
      try {
        Files.createSymbolicLink(link, real);
      } catch (UnsupportedOperationException | IOException denied) {
        return;
      }
      assertThrows(IOException.class, () -> ArchiveInspector.validate(link, 5, 5));
    }
  }

  @Test
  void reflectionContractAcceptsAndRejects() throws Exception {
    ClassInspector.requirePublicMethod(String.class, "length", int.class);
    assertThrows(
        AssertionError.class,
        () -> ClassInspector.requirePublicMethod(String.class, "length", long.class));
    assertThrows(
        NoSuchMethodException.class,
        () -> ClassInspector.requirePublicMethod(String.class, "missing", int.class));
  }

  @Test
  void realConsumerCompilesAndRunsUtf8() throws Exception {
    var result =
        JavaConsumer.compileAndRun(
            "Consumer",
            "public class Consumer { public static void main(String[] a) { System.out.print(\"成功☕\"); } }");
    assertEquals(0, result.exitCode(), result.stderr());
    assertEquals("成功☕", result.stdout());
  }

  @Test
  void brokenConsumerReturnsRealCompilerDiagnostic() throws Exception {
    var result = JavaConsumer.compileAndRun("Broken", "public class Broken { nope }");
    assertNotEquals(0, result.exitCode());
    assertTrue(result.stderr().contains("error:"), result.stderr());
  }
}
