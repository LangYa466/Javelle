/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.toolchain.JavaToolchainService;

/** Configures an external compiler boundary without loading compiler internals in the daemon. */
public final class TeyruPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    TeyruExtension extension = project.getExtensions().create("teyru", TeyruExtension.class);
    extension.getLanguageVersion().convention("0.1");
    extension.getTargetRelease().convention(21);
    extension.getCompilerOptions().convention(java.util.List.of("--no-color"));
    extension.getDiagnosticsFormat().convention("json");
    extension.getTrustProcessors().convention(false);
    project
        .getTasks()
        .register(
            "validateTeyruCompiler",
            task -> {
              task.setGroup("verification");
              task.setDescription("Validates the configured isolated Teyru compiler executable.");
              task.doLast(
                  ignored -> {
                    if (!extension.getCompilerExecutable().isPresent()) {
                      throw new GradleException("Teyru compiler executable is not configured");
                    }
                    var executable = extension.getCompilerExecutable().get().getAsFile();
                    if (!executable.isFile() || !executable.canExecute()) {
                      throw new GradleException(
                          "Teyru compiler is not executable: " + executable);
                    }
                  });
            });
    project.getPluginManager().apply("java");
    project.getPluginManager().withPlugin("java", ignored -> configureJava(project, extension));
  }

  private static void configureJava(Project project, TeyruExtension extension) {
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
      var teyru =
          project
              .getObjects()
              .sourceDirectorySet(
                  sourceSet.getName() + "Teyru", sourceSet.getName() + " Teyru sources");
      teyru.srcDir("src/" + sourceSet.getName() + "/teyru");
      teyru.getFilter().include("**/*.teyru");
      sourceSet.getExtensions().add("teyru", teyru);

      var export =
          project
              .getTasks()
              .register(
                  "export" + cap + "TeyruWorkspaceModel",
                  ExportTeyruWorkspaceModel.class,
                  task -> {
                    task.setGroup("teyru");
                    task.getProjectName().set(project.getName());
                    task.getProjectPath().set(project.getPath());
                    task.getSourceSetName().set(sourceSet.getName());
                    task.getJavaRoots()
                        .set(java.util.List.of("src/" + sourceSet.getName() + "/java"));
                    task.getTeyruRoots()
                        .set(java.util.List.of("src/" + sourceSet.getName() + "/teyru"));
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
                                .file("teyru/workspace/" + sourceSet.getName() + ".json"));
                  });
      var generate =
          project
              .getTasks()
              .register(
                  "generate" + cap + "TeyruJava",
                  GenerateTeyruJava.class,
                  task -> {
                    task.setGroup("teyru");
                    task.dependsOn(export);
                    task.getTeyruSources().from(teyru);
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
                                .dir("generated/sources/teyru/" + sourceSet.getName()));
                    task.getSourceMapDirectory()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .dir("teyru/source-maps/" + sourceSet.getName()));
                  });
      sourceSet.getJava().srcDir(generate);
    }
  }
}
