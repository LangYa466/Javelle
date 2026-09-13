/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import java.util.List;

public interface WorkspaceModelValidator {
  List<WorkspaceDiagnostic> validate(WorkspaceModel model, ValidationEnvironment environment);
}
