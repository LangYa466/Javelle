/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/** File type for `.javelle` sources; presentation only, semantics come from the LSP server. */
public final class JavelleFileType extends LanguageFileType {
  public static final JavelleFileType INSTANCE = new JavelleFileType();

  private JavelleFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Javelle";
  }

  @Override
  public @NotNull String getDescription() {
    return "Javelle source file";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "javelle";
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
