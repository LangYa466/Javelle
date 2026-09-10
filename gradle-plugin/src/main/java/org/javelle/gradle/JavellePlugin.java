/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Configures an external compiler boundary without loading compiler internals in the daemon. */
public final class JavellePlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    JavelleExtension extension = project.getExtensions().create("javelle", JavelleExtension.class);
    project
        .getTasks()
        .register(
            "validateJavelleCompiler",
            task -> {
              task.setGroup("verification");
              task.setDescription("Validates the configured isolated Javelle compiler executable.");
              task.doLast(
                  ignored -> {
                    if (!extension.getCompilerExecutable().isPresent()) {
                      throw new GradleException("Javelle compiler executable is not configured");
                    }
                    var executable = extension.getCompilerExecutable().get().getAsFile();
                    if (!executable.isFile() || !executable.canExecute()) {
                      throw new GradleException(
                          "Javelle compiler is not executable: " + executable);
                    }
                  });
            });
  }
}
