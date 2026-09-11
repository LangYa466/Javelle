/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import com.intellij.platform.lsp.api.LspServerSupportProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Headless proof (no display) that opening a `.javelle` file resolves to the registered file type
 * and that the LSP server support machinery offers a descriptor for it, wired to the bundled
 * launcher — without needing a rendered UI.
 */
@SuppressWarnings("deprecation")
public class P11FileTypeAndLspWiringTest extends BasePlatformTestCase {
  public void testJavelleFileResolvesToRegisteredFileType() {
    var file = myFixture.configureByText("Use.javelle", "class Use {}");
    assertEquals("Javelle", file.getFileType().getName());
  }

  public void testLspServerSupportProviderOffersDescriptorForJavelleFiles() throws Exception {
    var file = myFixture.configureByText("Use.javelle", "class Use {}");
    var provider = new JavelleLspServerSupportProvider();
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
        "provider must offer a descriptor that supports the opened .javelle file", accepted[0]);
    assertNotNull("descriptor must produce a command line", commandLine[0]);
    assertEquals(JavelleLspServerDescriptor.launcherPath().toString(), commandLine[0].getExePath());
    assertTrue(
        "command line must invoke the bundled launcher in stdio mode",
        commandLine[0].getParametersList().getParameters().contains("--stdio"));
  }

  public void testInitializationOptionsAreEmptyWithoutAnExportedWorkspaceModel() {
    var file = myFixture.configureByText("Use.javelle", "class Use {}");
    var descriptor = new JavelleLspServerDescriptor(getProject(), file.getVirtualFile());
    assertNull(descriptor.createInitializationOptions());
  }

  public void testInitializationOptionsReferenceAnExportedWorkspaceModel() throws Exception {
    var file = myFixture.configureByText("Use.javelle", "class Use {}");
    String basePath = getProject().getBasePath();
    assertNotNull(basePath);
    Path model = Path.of(basePath, "build", "javelle", "workspace", "main.json");
    Files.createDirectories(model.getParent());
    Files.writeString(model, "{}");
    try {
      var descriptor = new JavelleLspServerDescriptor(getProject(), file.getVirtualFile());
      var options = descriptor.createInitializationOptions();
      assertNotNull(options);
      @SuppressWarnings("unchecked")
      var javelle = (Map<String, Object>) ((Map<String, Object>) options).get("javelle");
      assertEquals(model.toUri().toString(), javelle.get("workspaceModelUri"));
    } finally {
      Files.deleteIfExists(model);
    }
  }
}
