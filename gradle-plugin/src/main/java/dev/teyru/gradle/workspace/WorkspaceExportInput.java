/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle.workspace;

import static dev.teyru.workspace.model.WorkspaceModel.*;

import java.util.*;
import dev.teyru.workspace.model.JsonValue;

/** Immutable, Gradle-API-free snapshot captured by a trusted build adapter. */
public record WorkspaceExportInput(
    int schemaMinor,
    SerializationKind serializationKind,
    WorkspaceDescriptor workspace,
    List<ModuleInput> modules,
    List<DocumentOverlay> overlays,
    ModelLimits limits,
    Map<String, JsonValue> extensions) {
  public WorkspaceExportInput {
    Objects.requireNonNull(serializationKind);
    Objects.requireNonNull(workspace);
    modules = List.copyOf(modules);
    overlays = List.copyOf(overlays);
    extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
  }

  public record ModuleInput(
      String id,
      String logicalProjectPath,
      List<SourceSet> sourceSets,
      List<ModuleDependency> dependencies,
      Toolchain toolchain,
      List<PropertyMetadata> propertyMetadata,
      Map<String, JsonValue> extensions) {
    public ModuleInput {
      sourceSets = List.copyOf(sourceSets);
      dependencies = List.copyOf(dependencies);
      propertyMetadata = List.copyOf(propertyMetadata);
      extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
    }
  }
}
