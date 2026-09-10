/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/** Compiles and executes a Java source as a real external consumer. */
public final class JavaConsumer {
  private JavaConsumer() {}

  public static ProcessResult compileAndRun(String className, String source) throws Exception {
    try (TemporaryWorkspace workspace = TemporaryWorkspace.create("javelle-consumer-")) {
      var sourceFile = workspace.resolve(className + ".java");
      Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
      String bin = java.nio.file.Path.of(System.getProperty("java.home"), "bin").toString();
      ProcessResult compile =
          ProcessRunner.run(
              List.of(bin + "/javac", "-d", workspace.root().toString(), sourceFile.toString()),
              Duration.ofSeconds(10),
              64 * 1024);
      if (compile.exitCode() != 0) return compile;
      return ProcessRunner.run(
          List.of(bin + "/java", "-cp", workspace.root().toString(), className),
          Duration.ofSeconds(10),
          64 * 1024);
    }
  }
}
