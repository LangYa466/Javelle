/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.language.tooling;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class LanguageToolingBoundary {
  public static final String NAME = "language-tooling";
  public static final List<String> ALLOWED_DEPENDENCIES =
      List.of("compiler-driver", "workspace-model");

  private LanguageToolingBoundary() {}
}
