/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.language.server;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class LanguageServerBoundary {
  public static final String NAME = "language-server";
  public static final List<String> ALLOWED_DEPENDENCIES =
      List.of("language-tooling", "language-protocol", "workspace-model");

  private LanguageServerBoundary() {}
}
