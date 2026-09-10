/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle.workspace;

import static org.javelle.workspace.model.WorkspaceModel.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;

/** Thin Gradle-facing capture adapter. It creates neutral input and registers no tasks. */
public final class GradleWorkspaceInputAdapter {
  public WorkspaceExportInput capturePortable(Project root) {
    var trust =
        new TrustPolicy(TrustLevel.UNTRUSTED, false, false, false, false, DecisionSource.DEFAULT);
    var modules = new ArrayList<WorkspaceExportInput.ModuleInput>();
    for (Project project :
        root.getAllprojects().stream().sorted(Comparator.comparing(Project::getPath)).toList()) {
      var java = project.getExtensions().findByType(JavaPluginExtension.class);
      if (java == null) continue;
      var sets = new ArrayList<SourceSet>();
      java.getSourceSets().stream()
          .sorted(Comparator.comparing(org.gradle.api.tasks.SourceSet::getName))
          .forEach(
              sourceSet -> {
                var javaRoots =
                    sourceSet.getJava().getSrcDirs().stream()
                        .sorted()
                        .map(x -> ref(root, x.toPath(), x.isDirectory()))
                        .toList();
                var javelle =
                    ref(
                        root,
                        project.file("src/" + sourceSet.getName() + "/javelle").toPath(),
                        project.file("src/" + sourceSet.getName() + "/javelle").isDirectory());
                var generated =
                    ref(
                        root,
                        project
                            .getLayout()
                            .getBuildDirectory()
                            .dir("generated/sources/javelle/" + sourceSet.getName())
                            .get()
                            .getAsFile()
                            .toPath(),
                        false);
                var classes =
                    ref(
                        root,
                        project
                            .getLayout()
                            .getBuildDirectory()
                            .dir("classes/java/" + sourceSet.getName())
                            .get()
                            .getAsFile()
                            .toPath(),
                        false);
                var paths =
                    new CompilationPaths(List.of(), List.of(), List.of(), List.of(), List.of());
                sets.add(
                    new SourceSet(
                        sourceSet.getName(),
                        javaRoots,
                        List.of(javelle),
                        List.of(generated),
                        List.of(classes),
                        paths,
                        new Releases(21, 21, 21),
                        List.of(),
                        "UTF-8",
                        trust,
                        Optional.of(hash(javaRoots + "|" + javelle.logicalPath()))));
              });
      var executable =
          new PathRef(
              "toolchains/current/bin/java",
              "sha256:" + hash(System.getProperty("java.version")),
              Optional.empty(),
              Optional.empty(),
              PathKind.JDK_EXECUTABLE,
              true,
              SymlinkPolicy.REJECT,
              Optional.of(hash(System.getProperty("java.home"))));
      int major = Runtime.version().feature();
      var tool =
          new Toolchain(
              executable,
              Integer.toString(major),
              System.getProperty("java.vendor"),
              hash(System.getProperty("java.runtime.version")));
      modules.add(
          new WorkspaceExportInput.ModuleInput(
              project.getPath().equals(":")
                  ? "root"
                  : project.getPath().substring(1).replace(':', '.'),
              project.getPath(),
              sets,
              List.of(),
              tool,
              List.of(),
              Map.of()));
    }
    return new WorkspaceExportInput(
        0,
        SerializationKind.PORTABLE,
        new WorkspaceDescriptor(
            root.getName(), ".", Optional.empty(), CaseSensitivity.UNKNOWN, "UTF-8", trust),
        modules,
        List.of(),
        new ModelLimits(16 * 1024 * 1024L, 4096, 4096, 100_000, 10_000, 100_000),
        Map.of());
  }

  private static PathRef ref(Project root, Path path, boolean exists) {
    String logical;
    try {
      logical =
          root.getRootDir()
              .toPath()
              .toAbsolutePath()
              .normalize()
              .relativize(path.toAbsolutePath().normalize())
              .toString()
              .replace('\\', '/');
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("source root outside workspace: " + path, e);
    }
    return new PathRef(
        logical,
        "sha256:" + hash(logical),
        Optional.empty(),
        Optional.empty(),
        PathKind.DIRECTORY,
        exists,
        SymlinkPolicy.REJECT,
        Optional.of(hash(logical + ":" + exists)));
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
