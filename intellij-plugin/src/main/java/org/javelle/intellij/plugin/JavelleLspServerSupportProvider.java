/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerSupportProvider;

/** Starts the bundled Javelle LSP server whenever a `.javelle` file is opened. */
@SuppressWarnings("deprecation")
public final class JavelleLspServerSupportProvider implements LspServerSupportProvider {
  @Override
  public void fileOpened(Project project, VirtualFile file, LspServerStarter starter) {
    if (!"javelle".equals(file.getExtension())) return;
    starter.ensureServerStarted(new JavelleLspServerDescriptor(project, file));
  }
}
