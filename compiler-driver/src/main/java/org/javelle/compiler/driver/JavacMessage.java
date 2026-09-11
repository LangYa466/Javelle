/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import java.util.List;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.source.TextRange;
import org.javelle.compiler.core.sourcemap.SourceLocation;

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
