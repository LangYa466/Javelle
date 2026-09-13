/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import java.util.*;

public record WorkspaceModel(
    SchemaVersion schemaVersion,
    SerializationKind serializationKind,
    WorkspaceDescriptor workspace,
    List<WorkspaceModule> modules,
    List<DocumentOverlay> overlays,
    ModelFingerprints fingerprints,
    ModelLimits limits,
    Map<String, JsonValue> extensions) {
  public WorkspaceModel {
    Objects.requireNonNull(schemaVersion);
    Objects.requireNonNull(serializationKind);
    Objects.requireNonNull(workspace);
    Objects.requireNonNull(fingerprints);
    Objects.requireNonNull(limits);
    modules = List.copyOf(modules);
    overlays = List.copyOf(overlays);
    extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
  }

  public record SchemaVersion(int major, int minor) {}

  public enum SerializationKind {
    PORTABLE,
    RESOLVED
  }

  public enum CaseSensitivity {
    SENSITIVE,
    INSENSITIVE,
    UNKNOWN
  }

  public enum PathKind {
    DIRECTORY,
    FILE,
    JAR,
    JDK_EXECUTABLE
  }

  public enum SymlinkPolicy {
    REJECT,
    RESOLVE_WITHIN_ROOT,
    ALLOW_DECLARED_EXTERNAL
  }

  public enum TrustLevel {
    UNTRUSTED,
    TRUSTED_BUILD,
    TRUSTED_PROCESSORS
  }

  public enum DecisionSource {
    DEFAULT,
    USER,
    POLICY
  }

  public enum DependencyScope {
    COMPILE,
    RUNTIME,
    TEST_COMPILE,
    TEST_RUNTIME,
    PROCESSOR
  }

  public enum DependencyKind {
    BUILD_ORDER,
    SOURCE_VISIBILITY,
    RUNTIME_ONLY
  }

  public enum OverlayState {
    DIRTY,
    SAVED,
    DELETED
  }

  public record WorkspaceDescriptor(
      String logicalName,
      String logicalRoot,
      Optional<PathRef> resolvedRoot,
      CaseSensitivity caseSensitivity,
      String defaultEncoding,
      TrustPolicy trust) {
    public WorkspaceDescriptor {
      resolvedRoot = Objects.requireNonNull(resolvedRoot);
    }
  }

  public record PathRef(
      String logicalPath,
      String relocatableKey,
      Optional<String> fileUri,
      Optional<String> resolvedAbsoluteUri,
      PathKind kind,
      boolean exists,
      SymlinkPolicy symlinkPolicy,
      Optional<String> contentSha256) {
    public PathRef {
      fileUri = Objects.requireNonNull(fileUri);
      resolvedAbsoluteUri = Objects.requireNonNull(resolvedAbsoluteUri);
      contentSha256 = Objects.requireNonNull(contentSha256);
    }
  }

  public record TrustPolicy(
      TrustLevel level,
      boolean allowBuildEvaluation,
      boolean allowProcessors,
      boolean allowNetwork,
      boolean allowUserCode,
      DecisionSource decisionSource) {}

  public record Releases(int source, int target, int release) {}

  public record CompilationPaths(
      List<PathRef> compileClasspath,
      List<PathRef> runtimeClasspath,
      List<PathRef> processorPath,
      List<PathRef> modulePath,
      List<PathRef> sourceJars) {
    public CompilationPaths {
      compileClasspath = List.copyOf(compileClasspath);
      runtimeClasspath = List.copyOf(runtimeClasspath);
      processorPath = List.copyOf(processorPath);
      modulePath = List.copyOf(modulePath);
      sourceJars = List.copyOf(sourceJars);
    }
  }

  public record SourceSet(
      String name,
      List<PathRef> javaRoots,
      List<PathRef> teyruRoots,
      List<PathRef> generatedJavaRoots,
      List<PathRef> generatedClassRoots,
      CompilationPaths paths,
      Releases releases,
      List<String> compilerOptions,
      String encoding,
      TrustPolicy trust,
      Optional<String> onDiskSnapshotFingerprint) {
    public SourceSet {
      javaRoots = List.copyOf(javaRoots);
      teyruRoots = List.copyOf(teyruRoots);
      generatedJavaRoots = List.copyOf(generatedJavaRoots);
      generatedClassRoots = List.copyOf(generatedClassRoots);
      compilerOptions = List.copyOf(compilerOptions);
      onDiskSnapshotFingerprint = Objects.requireNonNull(onDiskSnapshotFingerprint);
    }
  }

  public record Toolchain(
      PathRef executable, String javaVersion, String vendor, String runtimeFingerprint) {}

  public record ModuleDependency(
      String targetModuleId,
      DependencyScope scope,
      DependencyKind kind,
      Optional<String> targetSourceSet) {
    public ModuleDependency {
      targetSourceSet = Objects.requireNonNull(targetSourceSet);
    }
  }

  public record PropertyMetadata(
      String ownerBinaryName,
      String propertyName,
      String typeDescriptor,
      Optional<String> getterName,
      Optional<String> setterName,
      boolean readable,
      boolean writable,
      int metadataVersion,
      String originFingerprint) {
    public PropertyMetadata {
      getterName = Objects.requireNonNull(getterName);
      setterName = Objects.requireNonNull(setterName);
    }
  }

  public record WorkspaceModule(
      String id,
      String logicalProjectPath,
      List<SourceSet> sourceSets,
      List<ModuleDependency> dependencies,
      Toolchain toolchain,
      List<PropertyMetadata> propertyMetadata,
      Map<String, JsonValue> extensions) {
    public WorkspaceModule {
      sourceSets = List.copyOf(sourceSets);
      dependencies = List.copyOf(dependencies);
      propertyMetadata = List.copyOf(propertyMetadata);
      extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
    }
  }

  public record DocumentOverlay(
      String documentUri,
      long version,
      String contentSha256,
      String baseOnDiskFingerprint,
      OverlayState state,
      Optional<String> contentUtf8) {
    public DocumentOverlay {
      contentUtf8 = Objects.requireNonNull(contentUtf8);
    }
  }

  public record ModelFingerprints(
      String algorithm,
      String model,
      String configuration,
      String classpath,
      String toolchain,
      String options) {}

  public record ModelLimits(
      long maxDocumentBytes,
      int maxModules,
      int maxSourceSets,
      int maxPaths,
      int maxOverlays,
      int maxDependencyEdges) {}
}
