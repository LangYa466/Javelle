/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle.workspace;

import static dev.teyru.workspace.model.WorkspaceModel.*;

import java.util.*;
import dev.teyru.workspace.model.*;

/** Converts an already captured build snapshot without evaluating a Gradle build. */
public final class WorkspaceModelExporter {
  public WorkspaceModel export(WorkspaceExportInput input) {
    Objects.requireNonNull(input);
    var modules =
        input.modules().stream()
            .map(
                x ->
                    new WorkspaceModule(
                        x.id(),
                        x.logicalProjectPath(),
                        x.sourceSets(),
                        x.dependencies(),
                        x.toolchain(),
                        x.propertyMetadata(),
                        x.extensions()))
            .toList();
    var zero = "0".repeat(64);
    var provisional =
        new WorkspaceModel(
            new SchemaVersion(1, input.schemaMinor()),
            input.serializationKind(),
            input.workspace(),
            modules,
            input.overlays(),
            new ModelFingerprints("SHA-256", zero, zero, zero, zero, zero),
            input.limits(),
            input.extensions());
    var model = WorkspaceModelFingerprinter.refresh(provisional);
    var diagnostics =
        new DefaultWorkspaceModelValidator().validate(model, ValidationEnvironment.noIo());
    if (!diagnostics.isEmpty())
      throw new IllegalArgumentException("invalid workspace export: " + diagnostics);
    return model;
  }
}
