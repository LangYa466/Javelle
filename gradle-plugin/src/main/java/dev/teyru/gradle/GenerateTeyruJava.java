/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.workers.WorkerExecutor;

/** Incremental, isolated Teyru-to-Java generation task. */
public abstract class GenerateTeyruJava extends DefaultTask {
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getTeyruSources();

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getJavaAnalysisSources();

  @Classpath
  public abstract ConfigurableFileCollection getCompileClasspath();

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getCompilerExecutable();

  @Input
  public abstract Property<Integer> getTargetRelease();

  @Input
  public abstract Property<String> getJavaHome();

  @Input
  public abstract ListProperty<String> getCompilerOptions();

  @Input
  public abstract Property<String> getLanguageVersion();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getSourceMapDirectory();

  @Inject
  protected abstract WorkerExecutor getWorkerExecutor();

  @TaskAction
  public void generate() {
    var executable = getCompilerExecutable().get().getAsFile();
    if (!executable.isFile() || !executable.canExecute())
      throw new org.gradle.api.GradleException("Teyru compiler is not executable: " + executable);
    if (getTargetRelease().get() != 21 && getTargetRelease().get() != 25)
      throw new org.gradle.api.GradleException("Teyru targetRelease must be 21 or 25");
    getWorkerExecutor()
        .processIsolation()
        .submit(
            GenerateTeyruWork.class,
            p -> {
              p.getCompilerExecutable().set(getCompilerExecutable());
              p.getSources().from(getTeyruSources());
              p.getJavaSources().from(getJavaAnalysisSources());
              p.getClasspath().from(getCompileClasspath());
              p.getOutputDirectory().set(getOutputDirectory());
              p.getSourceMapDirectory().set(getSourceMapDirectory());
              p.getTargetRelease().set(getTargetRelease());
              p.getJavaHome().set(getJavaHome());
              p.getCompilerOptions().set(getCompilerOptions());
            });
  }
}
