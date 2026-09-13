/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.intellij.plugin;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspServerDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Launches the bundled P10 `teyru-lsp` stdio server for `.teyru` files. */
@SuppressWarnings("deprecation")
final class TeyruLspServerDescriptor extends LspServerDescriptor {
  private static final String PLUGIN_ID = "dev.teyru.ide";

  TeyruLspServerDescriptor(Project project, VirtualFile... roots) {
    super(project, "Teyru", roots);
  }

  @Override
  public boolean isSupportedFile(VirtualFile file) {
    return "teyru".equals(file.getExtension());
  }

  @Override
  public GeneralCommandLine createCommandLine() {
    return new GeneralCommandLine(launcherPath().toString(), "--stdio");
  }

  @Override
  public Object createInitializationOptions() {
    String basePath = getProject().getBasePath();
    if (basePath == null) return null;
    Path model = Path.of(basePath, "build", "teyru", "workspace", "main.json");
    if (!Files.isRegularFile(model)) return null;
    return Map.of("teyru", Map.of("workspaceModelUri", model.toUri().toString()));
  }

  static Path launcherPath() {
    var plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
    if (plugin == null) throw new IllegalStateException("Teyru plugin descriptor not found");
    return plugin.getPluginPath().resolve("teyru-lsp").resolve("bin").resolve("teyru-lsp");
  }
}
