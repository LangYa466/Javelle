/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

import static org.javelle.workspace.model.WorkspaceModel.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class WorkspaceModelFingerprinter {
  private WorkspaceModelFingerprinter() {}

  public static ModelFingerprints compute(WorkspaceModel m) {
    StringBuilder config = new StringBuilder(),
        cp = new StringBuilder(),
        tc = new StringBuilder(),
        opts = new StringBuilder();
    config
        .append(m.schemaVersion().major())
        .append('|')
        .append(m.workspace().logicalRoot())
        .append('|')
        .append(m.workspace().caseSensitivity())
        .append('|')
        .append(m.workspace().trust());
    m.modules().stream()
        .sorted(Comparator.comparing(WorkspaceModule::id))
        .forEach(
            mod -> {
              config.append('|').append(mod.id()).append('|').append(mod.logicalProjectPath());
              tc.append('|')
                  .append(pathKey(mod.toolchain().executable()))
                  .append('|')
                  .append(mod.toolchain().javaVersion())
                  .append('|')
                  .append(mod.toolchain().vendor())
                  .append('|')
                  .append(mod.toolchain().runtimeFingerprint());
              mod.dependencies().stream()
                  .sorted(
                      Comparator.comparing(ModuleDependency::targetModuleId)
                          .thenComparing(d -> d.scope().name())
                          .thenComparing(d -> d.kind().name()))
                  .forEach(config::append);
              mod.propertyMetadata().stream()
                  .sorted(
                      Comparator.comparing(PropertyMetadata::ownerBinaryName)
                          .thenComparing(PropertyMetadata::propertyName))
                  .forEach(config::append);
              mod.sourceSets().stream()
                  .sorted(Comparator.comparing(SourceSet::name))
                  .forEach(
                      s -> {
                        config
                            .append('|')
                            .append(s.name())
                            .append('|')
                            .append(s.releases())
                            .append('|')
                            .append(s.encoding())
                            .append('|')
                            .append(s.trust())
                            .append('|')
                            .append(s.onDiskSnapshotFingerprint().orElse(""));
                        roots(s).stream()
                            .sorted(Comparator.comparing(WorkspaceModelFingerprinter::pathKey))
                            .forEach(p -> config.append('|').append(pathKey(p)));
                        paths(s.paths()).forEach(p -> cp.append('|').append(pathKey(p)));
                        s.compilerOptions().forEach(o -> opts.append('|').append(o));
                      });
            });
    String configuration = sha(config.toString()),
        classpath = sha(cp.toString()),
        toolchain = sha(tc.toString()),
        options = sha(opts.toString());
    return new ModelFingerprints(
        "SHA-256",
        sha(configuration + classpath + toolchain + options),
        configuration,
        classpath,
        toolchain,
        options);
  }

  public static WorkspaceModel refresh(WorkspaceModel m) {
    return new WorkspaceModel(
        m.schemaVersion(),
        m.serializationKind(),
        m.workspace(),
        m.modules(),
        m.overlays(),
        compute(m),
        m.limits(),
        m.extensions());
  }

  /** Fingerprint for an analysis view. Inline content is represented by its declared hash. */
  public static String analysisFingerprint(WorkspaceModel m) {
    var overlays = new StringBuilder(compute(m).model());
    m.overlays().stream()
        .sorted(Comparator.comparing(DocumentOverlay::documentUri))
        .forEach(
            o ->
                overlays
                    .append('|')
                    .append(o.documentUri())
                    .append('|')
                    .append(o.version())
                    .append('|')
                    .append(o.contentSha256())
                    .append('|')
                    .append(o.baseOnDiskFingerprint())
                    .append('|')
                    .append(o.state()));
    return sha(overlays.toString());
  }

  private static String pathKey(PathRef p) {
    return p.kind()
        + ":"
        + p.logicalPath()
        + ":"
        + p.relocatableKey()
        + ":"
        + p.contentSha256().orElse("");
  }

  private static List<PathRef> roots(SourceSet s) {
    var x = new ArrayList<PathRef>();
    x.addAll(s.javaRoots());
    x.addAll(s.javelleRoots());
    x.addAll(s.generatedJavaRoots());
    x.addAll(s.generatedClassRoots());
    return x;
  }

  private static List<PathRef> paths(CompilationPaths p) {
    var x = new ArrayList<PathRef>();
    x.addAll(p.compileClasspath());
    x.addAll(p.runtimeClasspath());
    x.addAll(p.processorPath());
    x.addAll(p.modulePath());
    x.addAll(p.sourceJars());
    return x;
  }

  private static String sha(String x) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
