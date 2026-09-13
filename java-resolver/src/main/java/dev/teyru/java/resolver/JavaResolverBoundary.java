/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.java.resolver;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class JavaResolverBoundary {
  public static final String NAME = "java-resolver";
  public static final List<String> ALLOWED_DEPENDENCIES =
      List.of("compiler-core", "workspace-model");

  private JavaResolverBoundary() {}
}
