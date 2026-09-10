/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import java.util.*;
import org.javelle.compiler.core.lowering.GeneratedFile;

public record CompileResult(
    boolean success,
    int javacExitCode,
    List<GeneratedFile> generatedFiles,
    List<JavacMessage> diagnostics,
    Optional<PublishedOutputs> outputs) {
  public CompileResult {
    generatedFiles = List.copyOf(generatedFiles);
    diagnostics = List.copyOf(diagnostics);
  }
}
