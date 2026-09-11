/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

public interface WorkspaceModelCodec {
  byte[] writePortable(WorkspaceModel model);

  byte[] writeResolved(WorkspaceModel model);

  WorkspaceReadResult read(byte[] utf8, WorkspaceReadOptions options);
}
