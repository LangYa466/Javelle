/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.intellij.plugin;

import com.intellij.platform.lsp.api.LspServerSupportProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Headless proof (no display) that opening a `.teyru` file resolves to the registered file type
 * and that the LSP server support machinery offers a descriptor for it, wired to the bundled
 * launcher — without needing a rendered UI.
 */
@SuppressWarnings("deprecation")
public class P11FileTypeAndLspWiringTest extends BasePlatformTestCase {
  public void testTeyruFileResolvesToRegisteredFileType() {
    var file = myFixture.configureByText("Use.teyru", "class Use {}");
    assertEquals("Teyru", file.getFileType().getName());
  }

  public void testLspServerSupportProviderOffersDescriptorForTeyruFiles() throws Exception {
    var file = myFixture.configureByText("Use.teyru", "class Use {}");
    var provider = new TeyruLspServerSupportProvider();
    var accepted = new boolean[] {false};
    var commandLine = new com.intellij.execution.configurations.GeneralCommandLine[1];
    provider.fileOpened(
        getProject(),
        file.getVirtualFile(),
        (LspServerSupportProvider.LspServerStarter)
            descriptor -> {
              accepted[0] = descriptor.isSupportedFile(file.getVirtualFile());
              try {
                commandLine[0] = descriptor.createCommandLine();
              } catch (com.intellij.execution.ExecutionException e) {
                throw new RuntimeException(e);
              }
            });
    assertTrue(
        "provider must offer a descriptor that supports the opened .teyru file", accepted[0]);
    assertNotNull("descriptor must produce a command line", commandLine[0]);
    assertEquals(TeyruLspServerDescriptor.launcherPath().toString(), commandLine[0].getExePath());
    assertTrue(
        "command line must invoke the bundled launcher in stdio mode",
        commandLine[0].getParametersList().getParameters().contains("--stdio"));
  }

  public void testInitializationOptionsAreEmptyWithoutAnExportedWorkspaceModel() {
    var file = myFixture.configureByText("Use.teyru", "class Use {}");
    var descriptor = new TeyruLspServerDescriptor(getProject(), file.getVirtualFile());
    assertNull(descriptor.createInitializationOptions());
  }

  public void testInitializationOptionsReferenceAnExportedWorkspaceModel() throws Exception {
    var file = myFixture.configureByText("Use.teyru", "class Use {}");
    String basePath = getProject().getBasePath();
    assertNotNull(basePath);
    Path model = Path.of(basePath, "build", "teyru", "workspace", "main.json");
    Files.createDirectories(model.getParent());
    Files.writeString(model, "{}");
    try {
      var descriptor = new TeyruLspServerDescriptor(getProject(), file.getVirtualFile());
      var options = descriptor.createInitializationOptions();
      assertNotNull(options);
      @SuppressWarnings("unchecked")
      var teyru = (Map<String, Object>) ((Map<String, Object>) options).get("teyru");
      assertEquals(model.toUri().toString(), teyru.get("workspaceModelUri"));
    } finally {
      Files.deleteIfExists(model);
    }
  }
}
