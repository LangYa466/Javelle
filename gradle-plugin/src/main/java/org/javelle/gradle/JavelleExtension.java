/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.gradle;

import org.gradle.api.file.RegularFileProperty;

/** Location of the isolated Javelle compiler executable used by the Gradle plugin. */
public abstract class JavelleExtension {
  public abstract RegularFileProperty getCompilerExecutable();
}
