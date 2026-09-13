/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.release.tools;

import java.util.List;

/** Stable build-time declaration of this module's allowed production dependencies. */
public final class ReleaseToolsBoundary {
  public static final String NAME = "release-tools";
  public static final List<String> ALLOWED_DEPENDENCIES = List.of();

  private ReleaseToolsBoundary() {}
}
