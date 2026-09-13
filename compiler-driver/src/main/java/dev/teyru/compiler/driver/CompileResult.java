/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import java.util.*;
import dev.teyru.compiler.core.lowering.GeneratedFile;

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
