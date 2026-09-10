/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

public record WorkspaceReadOptions(long maxBytes, int maxDepth, int maxEntries) {
  public WorkspaceReadOptions {
    if (maxBytes < 1 || maxDepth < 1 || maxEntries < 1)
      throw new IllegalArgumentException("positive limits required");
  }

  public static WorkspaceReadOptions defaults() {
    return new WorkspaceReadOptions(67_108_864, 128, 1_000_000);
  }
}
