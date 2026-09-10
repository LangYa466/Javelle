/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.integration.tests;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class IntegrationTestsBoundary {
  public static final String NAME = "integration-tests";
  public static final List<String> ALLOWED_DEPENDENCIES = List.of();

  private IntegrationTestsBoundary() {}
}
