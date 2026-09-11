/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

public interface GenerateJavelleParameters extends WorkParameters {
  RegularFileProperty getCompilerExecutable();

  ConfigurableFileCollection getSources();

  ConfigurableFileCollection getJavaSources();

  ConfigurableFileCollection getClasspath();

  DirectoryProperty getOutputDirectory();

  DirectoryProperty getSourceMapDirectory();

  Property<Integer> getTargetRelease();

  Property<String> getJavaHome();

  ListProperty<String> getCompilerOptions();
}
