/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.testkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Writes byte-identical archives for identical entry maps. */
public final class DeterministicArchive {
  private DeterministicArchive() {}

  public static void write(Path target, Map<String, byte[]> entries) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
      for (var item : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        String name = item.getKey();
        Path normalized = Path.of(name).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || name.indexOf('\\') >= 0)
          throw new IOException("unsafe archive path: " + name);
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(item.getValue());
        output.closeEntry();
      }
    }
  }
}
