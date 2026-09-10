/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarFile;

/** Reads archive entries without executing archive content. */
public final class ArchiveInspector {
  private ArchiveInspector() {}

  public static List<String> entries(Path archive) throws IOException {
    try (JarFile jar = new JarFile(archive.toFile(), false)) {
      return jar.stream().map(entry -> entry.getName()).sorted().toList();
    }
  }

  /** Validates names and uncompressed sizes before a caller extracts an untrusted archive. */
  public static List<String> validate(Path archive, int maxEntries, long maxUncompressedBytes)
      throws IOException {
    if (maxEntries < 1 || maxUncompressedBytes < 1) throw new IllegalArgumentException("limits");
    if (java.nio.file.Files.isSymbolicLink(archive))
      throw new IOException("symbolic-link archive rejected");
    try (JarFile jar = new JarFile(archive.toFile(), false)) {
      var names = new HashSet<String>();
      long total = 0;
      for (var entry : jar.stream().toList()) {
        String name = entry.getName();
        Path normalized = Path.of(name).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || name.indexOf('\\') >= 0) {
          throw new IOException("unsafe archive path: " + name);
        }
        if (!names.add(name)) throw new IOException("duplicate archive entry: " + name);
        if (entry.getSize() < 0) throw new IOException("unknown archive entry size: " + name);
        total = Math.addExact(total, entry.getSize());
        if (names.size() > maxEntries || total > maxUncompressedBytes) {
          throw new IOException("archive exceeds safety limits");
        }
      }
      return names.stream().sorted().toList();
    } catch (ArithmeticException overflow) {
      throw new IOException("archive size overflow", overflow);
    }
  }
}
