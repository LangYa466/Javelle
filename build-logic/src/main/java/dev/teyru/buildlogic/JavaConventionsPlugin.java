/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.buildlogic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/** Shared, reproducible Java build contract for Teyru modules. */
public final class JavaConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getExtensions().configure(JavaPluginExtension.class, java ->
                java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(25)));
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().setEncoding(StandardCharsets.UTF_8.name());
            task.getOptions().getRelease().set(21);
            task.getOptions().setCompilerArgs(List.of("-Xlint:all", "-Werror"));
        });
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.useJUnitPlatform();
            task.systemProperty("file.encoding", StandardCharsets.UTF_8.name());
            task.systemProperty("user.language", "en");
            task.systemProperty("user.country", "US");
            task.systemProperty("user.timezone", "UTC");
        });
        project.getTasks().withType(Jar.class).configureEach(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
        });
    }
}
