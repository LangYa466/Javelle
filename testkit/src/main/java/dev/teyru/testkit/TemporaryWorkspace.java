/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.testkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Owned temporary directory with traversal-safe path resolution and deterministic cleanup. */
public final class TemporaryWorkspace implements AutoCloseable {
  private final Path root;

  private TemporaryWorkspace(Path root) {
    this.root = root;
  }

  public static TemporaryWorkspace create(String prefix) throws IOException {
    return new TemporaryWorkspace(Files.createTempDirectory(prefix));
  }

  public Path root() {
    return root;
  }

  public Path resolve(String relative) {
    Path result = root.resolve(relative).normalize();
    if (!result.startsWith(root)) throw new IllegalArgumentException("path escapes workspace");
    return result;
  }

  @Override
  public void close() throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }
}
