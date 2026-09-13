/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;

/** Runs the compiler executable from an isolated worker process. */
public abstract class GenerateTeyruWork implements WorkAction<GenerateTeyruParameters> {
  @Override
  public void execute() {
    var p = getParameters();
    var sources =
        p.getSources().getFiles().stream()
            .filter(x -> x.isFile() && x.getName().endsWith(".teyru"))
            .sorted(Comparator.comparing(java.io.File::getAbsolutePath))
            .toList();
    var output = p.getOutputDirectory().get().getAsFile().toPath();
    try {
      if (java.nio.file.Files.isDirectory(output)) {
        try (var entries = java.nio.file.Files.list(output)) {
          if (entries.findAny().isEmpty()) java.nio.file.Files.delete(output);
        }
      }
    } catch (IOException e) {
      throw new GradleException("Cannot prepare Teyru output directory", e);
    }
    var command = new ArrayList<String>();
    command.add(p.getCompilerExecutable().get().getAsFile().getAbsolutePath());
    command.add("emit-java");
    sources.forEach(x -> command.add(x.getAbsolutePath()));
    p.getJavaSources().getFiles().stream()
        .filter(x -> x.isFile() && x.getName().endsWith(".java"))
        .sorted(Comparator.comparing(java.io.File::getAbsolutePath))
        .forEach(
            x -> {
              command.add("--java-source");
              command.add(x.getAbsolutePath());
            });
    p.getClasspath().getFiles().stream()
        .filter(java.io.File::exists)
        .sorted(Comparator.comparing(java.io.File::getAbsolutePath))
        .forEach(
            x -> {
              command.add("--classpath");
              command.add(x.getAbsolutePath());
            });
    command.add("--output");
    command.add(output.toAbsolutePath().toString());
    command.add("--release");
    command.add(Integer.toString(p.getTargetRelease().get()));
    command.add("--diagnostics");
    command.add("json");
    command.addAll(p.getCompilerOptions().get());
    try {
      var builder = new ProcessBuilder(command).redirectErrorStream(false);
      builder.environment().put("JAVA_HOME", p.getJavaHome().get());
      var process = builder.start();
      boolean done = process.waitFor(5, TimeUnit.MINUTES);
      if (!done) {
        process.destroyForcibly();
        throw new GradleException("Teyru compiler timed out after 5 minutes");
      }
      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.exitValue() != 0)
        throw new GradleException(
            "Teyru compiler failed (exit " + process.exitValue() + "): " + stderr + stdout);
      mirrorSourceMaps(output, p.getSourceMapDirectory().get().getAsFile().toPath());
    } catch (IOException e) {
      throw new GradleException("Cannot start configured Teyru compiler", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GradleException("Teyru compiler interrupted", e);
    }
  }

  private static void mirrorSourceMaps(java.nio.file.Path output, java.nio.file.Path maps)
      throws IOException {
    var parent = maps.toAbsolutePath().normalize().getParent();
    if (parent == null) throw new IOException("source-map output requires parent");
    java.nio.file.Files.createDirectories(parent);
    var stage = java.nio.file.Files.createTempDirectory(parent, ".teyru-maps-stage-");
    var backup = parent.resolve(".teyru-maps-backup-" + ProcessHandle.current().pid());
    var manifest = maps.resolve(".teyru-source-maps-v1");
    try {
      if (java.nio.file.Files.exists(maps)) copyTree(maps, stage);
      if (java.nio.file.Files.isRegularFile(manifest)) {
        for (String line : java.nio.file.Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
          if (line.isBlank()) continue;
          String[] fields = line.split("\\t", -1);
          if (fields.length != 2 || !fields[1].matches("[0-9a-f]{64}"))
            throw new IOException("corrupt source-map ownership manifest");
          var owned = maps.resolve(fields[0]).normalize();
          if (!owned.startsWith(maps)) throw new IOException("source-map manifest traversal");
          if (java.nio.file.Files.exists(owned)
              && !sha(java.nio.file.Files.readAllBytes(owned)).equals(fields[1]))
            throw new IOException("owned source map changed: " + fields[0]);
          java.nio.file.Files.deleteIfExists(stage.resolve(fields[0]).normalize());
        }
      }
      var entries = new java.util.TreeMap<String, String>();
      if (java.nio.file.Files.exists(output))
        try (var paths = java.nio.file.Files.walk(output)) {
          for (var source :
              paths
                  .filter(java.nio.file.Files::isRegularFile)
                  .filter(x -> x.getFileName().toString().endsWith(".map.json"))
                  .toList()) {
            var relative = output.relativize(source).toString();
            var target = stage.resolve(relative).normalize();
            if (!target.startsWith(stage)) throw new IOException("source-map traversal");
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(
                source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            entries.put(relative.replace('\\', '/'), sha(java.nio.file.Files.readAllBytes(target)));
          }
        }
      var text = new StringBuilder();
      entries.forEach((path, hash) -> text.append(path).append('\t').append(hash).append('\n'));
      java.nio.file.Files.writeString(
          stage.resolve(".teyru-source-maps-v1"), text, StandardCharsets.UTF_8);
      if (java.nio.file.Files.exists(maps))
        java.nio.file.Files.move(maps, backup, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      try {
        java.nio.file.Files.move(stage, maps, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        deleteTree(backup);
      } catch (IOException failure) {
        if (java.nio.file.Files.exists(backup))
          java.nio.file.Files.move(backup, maps, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        throw failure;
      }
    } finally {
      deleteTree(stage);
      deleteTree(backup);
    }
  }

  private static void copyTree(java.nio.file.Path source, java.nio.file.Path target)
      throws IOException {
    try (var paths = java.nio.file.Files.walk(source)) {
      for (var path : paths.toList()) {
        var copy = target.resolve(source.relativize(path).toString());
        if (java.nio.file.Files.isSymbolicLink(path))
          throw new IOException("source-map symlink rejected");
        if (java.nio.file.Files.isDirectory(path)) java.nio.file.Files.createDirectories(copy);
        else java.nio.file.Files.copy(path, copy);
      }
    }
  }

  private static void deleteTree(java.nio.file.Path root) throws IOException {
    if (!java.nio.file.Files.exists(root)) return;
    try (var paths = java.nio.file.Files.walk(root)) {
      for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        java.nio.file.Files.deleteIfExists(path);
    }
  }

  private static String sha(byte[] bytes) {
    try {
      return java.util.HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
