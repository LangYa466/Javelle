/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import java.nio.file.Path;
import java.util.Optional;

public record ValidationEnvironment(
    Optional<Path> resolvedWorkspaceRoot, boolean inspectFileSystem) {
  public ValidationEnvironment {
    resolvedWorkspaceRoot = resolvedWorkspaceRoot.map(p -> p.toAbsolutePath().normalize());
  }

  public static ValidationEnvironment noIo() {
    return new ValidationEnvironment(Optional.empty(), false);
  }
}
