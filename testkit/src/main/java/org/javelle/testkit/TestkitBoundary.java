/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class TestkitBoundary {
  public static final String NAME = "testkit";
  public static final List<String> ALLOWED_DEPENDENCIES = List.of();

  private TestkitBoundary() {}
}
