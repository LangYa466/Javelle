/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.intellij.plugin;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/** File type for `.teyru` sources; presentation only, semantics come from the LSP server. */
public final class TeyruFileType extends LanguageFileType {
  public static final TeyruFileType INSTANCE = new TeyruFileType();

  private TeyruFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Teyru";
  }

  @Override
  public @NotNull String getDescription() {
    return "Teyru source file";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "teyru";
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
