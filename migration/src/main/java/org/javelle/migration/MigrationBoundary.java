/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.migration;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class MigrationBoundary {
  public static final String NAME = "migration";
  public static final List<String> ALLOWED_DEPENDENCIES =
      List.of("compiler-core", "compiler-driver");

  private MigrationBoundary() {}
}
