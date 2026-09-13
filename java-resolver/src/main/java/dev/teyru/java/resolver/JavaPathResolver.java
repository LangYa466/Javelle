/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.java.resolver;

import java.nio.file.*;
import java.util.*;

/**
 * Validates and deterministically normalizes explicit javac paths without ambient classpath use.
 */
public final class JavaPathResolver {
  public List<Path> resolveExisting(List<Path> paths) {
    var result = new TreeSet<Path>();
    for (Path path : paths) {
      Path normalized = path.toAbsolutePath().normalize();
      if (!Files.exists(normalized))
        throw new IllegalArgumentException("missing Java path: " + path);
      result.add(normalized);
    }
    return List.copyOf(result);
  }
}
