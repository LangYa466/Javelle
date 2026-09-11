/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

import java.util.*;

public record WorkspaceReadResult(
    Optional<WorkspaceModel> model,
    java.util.List<WorkspaceDiagnostic> diagnostics,
    JsonValue.ObjectValue rawDocument) {
  public WorkspaceReadResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean success() {
    return model.isPresent() && diagnostics.isEmpty();
  }
}
