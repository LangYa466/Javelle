/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import com.intellij.platform.lsp.api.LspServerSupportProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

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

  public void testLspServerSupportProviderOffersDescriptorForJavelleFiles() {
    var file = myFixture.configureByText("Use.javelle", "class Use {}");
    var provider = new JavelleLspServerSupportProvider();
    var accepted = new boolean[] {false};
    provider.fileOpened(
        getProject(),
        file.getVirtualFile(),
        (LspServerSupportProvider.LspServerStarter)
            descriptor -> accepted[0] = descriptor.isSupportedFile(file.getVirtualFile()));
    assertTrue(
        "provider must offer a descriptor that supports the opened .javelle file", accepted[0]);
  }
}
