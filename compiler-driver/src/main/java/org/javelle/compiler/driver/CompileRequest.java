/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import java.nio.file.Path;
import java.util.*;
import org.javelle.compiler.core.budget.ResourceBudget;

public record CompileRequest(
    List<SourceInput> javelleSources,
    List<Path> javaSources,
    List<Path> classpath,
    List<Path> modulePath,
    Path generatedRoot,
    Path classOutput,
    int release,
    boolean enableProcessors,
    List<Path> processorPath,
    ResourceBudget budget) {
  public CompileRequest {
    javelleSources = List.copyOf(javelleSources);
    javaSources = List.copyOf(javaSources);
    classpath = List.copyOf(classpath);
    modulePath = List.copyOf(modulePath);
    processorPath = List.copyOf(processorPath);
    Objects.requireNonNull(generatedRoot);
    Objects.requireNonNull(classOutput);
    Objects.requireNonNull(budget);
  }
}
