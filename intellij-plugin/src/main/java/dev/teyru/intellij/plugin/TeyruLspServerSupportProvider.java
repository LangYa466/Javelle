/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerSupportProvider;

/** Starts the bundled Teyru LSP server whenever a `.teyru` file is opened. */
@SuppressWarnings("deprecation")
public final class TeyruLspServerSupportProvider implements LspServerSupportProvider {
  @Override
  public void fileOpened(Project project, VirtualFile file, LspServerStarter starter) {
    if (!"teyru".equals(file.getExtension())) return;
    starter.ensureServerStarted(new TeyruLspServerDescriptor(project, file));
  }
}
