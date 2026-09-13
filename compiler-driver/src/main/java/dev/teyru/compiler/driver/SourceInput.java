/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import java.util.Objects;

public record SourceInput(String relativeUri, byte[] utf8) {
  public SourceInput {
    Objects.requireNonNull(relativeUri);
    utf8 = Objects.requireNonNull(utf8).clone();
  }

  @Override
  public byte[] utf8() {
    return utf8.clone();
  }
}
