/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class IntellijPluginBoundary {
  public static final String NAME = "intellij-plugin";
  public static final List<String> ALLOWED_DEPENDENCIES = List.of("language-protocol");

  private IntellijPluginBoundary() {}
}
