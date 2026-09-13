/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Location of the isolated Teyru compiler executable used by the Gradle plugin. */
public abstract class TeyruExtension {
  public abstract RegularFileProperty getCompilerExecutable();

  public abstract Property<String> getLanguageVersion();

  public abstract Property<Integer> getTargetRelease();

  public abstract ListProperty<String> getCompilerOptions();

  public abstract Property<String> getDiagnosticsFormat();

  public abstract Property<Boolean> getTrustProcessors();
}
