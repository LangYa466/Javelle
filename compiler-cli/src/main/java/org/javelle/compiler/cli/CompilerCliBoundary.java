/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.cli;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class CompilerCliBoundary {
  public static final String NAME = "compiler-cli";
  public static final List<String> ALLOWED_DEPENDENCIES = List.of("compiler-driver");

  private CompilerCliBoundary() {}
}
