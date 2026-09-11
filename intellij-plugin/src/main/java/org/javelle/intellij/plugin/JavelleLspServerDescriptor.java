/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerDescriptor;
import java.nio.file.Path;

/** Launches the bundled P10 `javelle-lsp` stdio server for `.javelle` files. */
@SuppressWarnings("deprecation")
final class JavelleLspServerDescriptor extends LspServerDescriptor {
  private static final String PLUGIN_ID = "org.javelle.ide";

  JavelleLspServerDescriptor(Project project, VirtualFile... roots) {
    super(project, "Javelle", roots);
  }

  @Override
  public boolean isSupportedFile(VirtualFile file) {
    return "javelle".equals(file.getExtension());
  }

  @Override
  public GeneralCommandLine createCommandLine() {
    return new GeneralCommandLine(launcherPath().toString(), "--stdio");
  }

  static Path launcherPath() {
    var plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
    if (plugin == null) throw new IllegalStateException("Javelle plugin descriptor not found");
    return plugin.getPluginPath().resolve("javelle-lsp").resolve("bin").resolve("javelle-lsp");
  }
}
