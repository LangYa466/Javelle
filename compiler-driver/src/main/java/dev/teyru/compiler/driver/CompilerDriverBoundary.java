/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class CompilerDriverBoundary {
  public static final String NAME = "compiler-driver";
  public static final List<String> ALLOWED_DEPENDENCIES =
      List.of("compiler-core", "java-resolver", "workspace-model");

  private CompilerDriverBoundary() {}
}
