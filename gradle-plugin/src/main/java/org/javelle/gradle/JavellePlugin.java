/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaToolchainService;

/** Configures an external compiler boundary without loading compiler internals in the daemon. */
public final class JavellePlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    JavelleExtension extension = project.getExtensions().create("javelle", JavelleExtension.class);
    extension.getLanguageVersion().convention("0.1");
    extension.getTargetRelease().convention(21);
    extension.getCompilerOptions().convention(java.util.List.of("--no-color"));
    extension.getDiagnosticsFormat().convention("json");
    extension.getTrustProcessors().convention(false);
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
    project.getPluginManager().apply("java");
    project.getPluginManager().withPlugin("java", ignored -> configureJava(project, extension));
  }

  private static void configureJava(Project project, JavelleExtension extension) {
    var javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
    var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
    javaExtension
        .getToolchain()
        .getLanguageVersion()
        .convention(org.gradle.jvm.toolchain.JavaLanguageVersion.of(Runtime.version().feature()));
    var selectedLauncher = toolchains.launcherFor(javaExtension.getToolchain());
    for (SourceSet sourceSet : javaExtension.getSourceSets()) {
      String cap =
          sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)
              ? ""
              : Character.toUpperCase(sourceSet.getName().charAt(0))
                  + sourceSet.getName().substring(1);
      var javelle =
          project
              .getObjects()
              .sourceDirectorySet(
                  sourceSet.getName() + "Javelle", sourceSet.getName() + " Javelle sources");
      javelle.srcDir("src/" + sourceSet.getName() + "/javelle");
      javelle.getFilter().include("**/*.javelle");
      sourceSet.getExtensions().add("javelle", javelle);

      var export =
          project
              .getTasks()
              .register(
                  "export" + cap + "JavelleWorkspaceModel",
                  ExportJavelleWorkspaceModel.class,
                  task -> {
                    task.setGroup("javelle");
                    task.getProjectName().set(project.getName());
                    task.getProjectPath().set(project.getPath());
                    task.getSourceSetName().set(sourceSet.getName());
                    task.getJavaRoots()
                        .set(java.util.List.of("src/" + sourceSet.getName() + "/java"));
                    task.getJavelleRoots()
                        .set(java.util.List.of("src/" + sourceSet.getName() + "/javelle"));
                    task.getTargetRelease().set(extension.getTargetRelease());
                    task.getToolchainJavaHome()
                        .set(
                            selectedLauncher.map(
                                x ->
                                    x.getMetadata()
                                        .getInstallationPath()
                                        .getAsFile()
                                        .getAbsolutePath()));
                    task.getToolchainJavaVersion()
                        .set(
                            selectedLauncher.map(
                                x -> x.getMetadata().getLanguageVersion().toString()));
                    task.getToolchainVendor()
                        .set(selectedLauncher.map(x -> x.getMetadata().getVendor().toString()));
                    task.getCompilerOptions().set(extension.getCompilerOptions());
                    task.getOutputFile()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .file("javelle/workspace/" + sourceSet.getName() + ".json"));
                  });
      var generate =
          project
              .getTasks()
              .register(
                  "generate" + cap + "JavelleJava",
                  GenerateJavelleJava.class,
                  task -> {
                    task.setGroup("javelle");
                    task.dependsOn(export);
                    task.getJavelleSources().from(javelle);
                    // Deliberately exclude the generated Java provider to prevent generate ->
                    // compile cycles.
                    task.getJavaAnalysisSources()
                        .from(project.fileTree("src/" + sourceSet.getName() + "/java"));
                    task.getCompileClasspath().from(sourceSet.getCompileClasspath());
                    task.getCompilerExecutable().set(extension.getCompilerExecutable());
                    task.getTargetRelease().set(extension.getTargetRelease());
                    task.getJavaHome()
                        .set(
                            selectedLauncher.map(
                                launcher ->
                                    launcher
                                        .getMetadata()
                                        .getInstallationPath()
                                        .getAsFile()
                                        .getAbsolutePath()));
                    task.getCompilerOptions().set(extension.getCompilerOptions());
                    task.getLanguageVersion().set(extension.getLanguageVersion());
                    task.getOutputDirectory()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .dir("generated/sources/javelle/" + sourceSet.getName()));
                    task.getSourceMapDirectory()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .dir("javelle/source-maps/" + sourceSet.getName()));
                  });
      sourceSet.getJava().srcDir(generate);
    }
  }
}
