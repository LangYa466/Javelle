/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import java.nio.file.Path;

public record PublishedOutputs(Path generatedRoot, Path classOutput, Path ownershipManifest) {}
