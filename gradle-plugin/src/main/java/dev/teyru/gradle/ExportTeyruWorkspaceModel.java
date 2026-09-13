/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle;

import static dev.teyru.workspace.model.WorkspaceModel.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import dev.teyru.gradle.workspace.*;
import dev.teyru.workspace.model.DefaultWorkspaceModelCodec;

/** Writes the accepted P08 portable workspace schema from declared, neutral inputs. */
public abstract class ExportTeyruWorkspaceModel extends DefaultTask {
  @Input
  public abstract Property<String> getProjectName();

  @Input
  public abstract Property<String> getProjectPath();

  @Input
  public abstract Property<String> getSourceSetName();

  @Input
  public abstract ListProperty<String> getJavaRoots();

  @Input
  public abstract ListProperty<String> getTeyruRoots();

  @Input
  public abstract Property<Integer> getTargetRelease();

  @Input
  public abstract ListProperty<String> getCompilerOptions();

  @Input
  public abstract Property<String> getToolchainJavaHome();

  @Input
  public abstract Property<String> getToolchainJavaVersion();

  @Input
  public abstract Property<String> getToolchainVendor();

  @OutputFile
  public abstract RegularFileProperty getOutputFile();

  @TaskAction
  public void write() throws Exception {
    var trust =
        new TrustPolicy(TrustLevel.UNTRUSTED, false, false, false, false, DecisionSource.DEFAULT);
    var sourceSet =
        new SourceSet(
            getSourceSetName().get(),
            refs(getJavaRoots().get()),
            refs(getTeyruRoots().get()),
            List.of(ref("build/generated/sources/teyru/" + getSourceSetName().get())),
            List.of(ref("build/classes/java/" + getSourceSetName().get())),
            new CompilationPaths(List.of(), List.of(), List.of(), List.of(), List.of()),
            new Releases(
                getTargetRelease().get(), getTargetRelease().get(), getTargetRelease().get()),
            getCompilerOptions().get(),
            "UTF-8",
            trust,
            Optional.of(
                hash(
                    String.join("\0", getJavaRoots().get())
                        + String.join("\0", getTeyruRoots().get()))));
    var tool =
        new Toolchain(
            new PathRef(
                "toolchains/selected/bin/java",
                "sha256:" + hash(getToolchainJavaVersion().get()),
                Optional.empty(),
                Optional.empty(),
                PathKind.JDK_EXECUTABLE,
                true,
                SymlinkPolicy.REJECT,
                Optional.of(hash(getToolchainJavaHome().get()))),
            getToolchainJavaVersion().get(),
            getToolchainVendor().get(),
            hash(getToolchainJavaVersion().get() + "\0" + getToolchainVendor().get()));
    var input =
        new WorkspaceExportInput(
            0,
            SerializationKind.PORTABLE,
            new WorkspaceDescriptor(
                getProjectName().get(),
                ".",
                Optional.empty(),
                CaseSensitivity.UNKNOWN,
                "UTF-8",
                trust),
            List.of(
                new WorkspaceExportInput.ModuleInput(
                    getProjectPath().get().equals(":")
                        ? "root"
                        : getProjectPath().get().substring(1).replace(':', '.'),
                    getProjectPath().get(),
                    List.of(sourceSet),
                    List.of(),
                    tool,
                    List.of(),
                    Map.of())),
            List.of(),
            new ModelLimits(16 * 1024 * 1024L, 4096, 4096, 100_000, 10_000, 100_000),
            Map.of());
    byte[] json =
        new DefaultWorkspaceModelCodec().writePortable(new WorkspaceModelExporter().export(input));
    var file = getOutputFile().get().getAsFile().toPath();
    Files.createDirectories(file.getParent());
    Files.write(file, json);
  }

  private static List<PathRef> refs(List<String> paths) {
    return paths.stream().sorted().map(ExportTeyruWorkspaceModel::ref).toList();
  }

  private static PathRef ref(String path) {
    String normalized = path.replace('\\', '/');
    return new PathRef(
        normalized,
        "sha256:" + hash(normalized),
        Optional.empty(),
        Optional.empty(),
        PathKind.DIRECTORY,
        false,
        SymlinkPolicy.REJECT,
        Optional.of(hash(normalized + ":false")));
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
