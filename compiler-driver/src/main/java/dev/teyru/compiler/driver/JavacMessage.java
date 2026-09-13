/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import java.util.List;
import dev.teyru.compiler.core.diagnostic.*;
import dev.teyru.compiler.core.source.TextRange;
import dev.teyru.compiler.core.sourcemap.SourceLocation;

public record JavacMessage(
    DiagnosticCode code,
    Severity severity,
    String generatedUri,
    TextRange generatedRange,
    List<SourceLocation> originalLocations,
    String message) {
  public JavacMessage {
    originalLocations = List.copyOf(originalLocations);
  }
}
