/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.buildlogic.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** Standalone negative-fixture runner; build owner should wire this into Gradle's test lifecycle. */
public final class ArchitectureBoundaryCheckerTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("javelle-architecture-");
        expectClean(root);
        expectSourceFailure(root, "compiler-core", "class Bad { com.intellij.psi.PsiFile field; }");
        expectSourceFailure(root, "compiler-core", "import org.gradle.api.Project; class Bad {}");
        expectSourceFailure(root, "compiler-core", "class Bad { org.eclipse.lsp4j.Position p; }");
        expectSourceFailure(root, "compiler-core", "class Bad { org.javelle.workspace.Model p; }");
        expectSourceFailure(root, "language-server", "class Bad { com.intellij.openapi.project.Project p; }");
        expectSourceFailure(root, "workspace-model", "class Bad { org.gradle.api.Project p; }");
        expectSourceFailure(root, "workspace-model", "class Bad { com.intellij.psi.PsiFile p; }");
        expectSourceFailure(root, "workspace-model", "class Bad { org.eclipse.lsp4j.Position p; }");
        expectSourceFailure(root, "language-protocol", "class Bad { org.javelle.compiler.Driver p; }");
        expectSourceFailure(root, "intellij-plugin", "class Bad { org.javelle.compiler.Driver p; }");
        expectSourceFailure(root, "gradle-plugin", "class Bad { org.javelle.resolver.JavaResolver p; }");
        expectCommentsAndStringsIgnored(root);
        expectBytecodeFailure(root);
        System.out.println("ARCHITECTURE_NEGATIVES_OK");
    }

    private static void expectCommentsAndStringsIgnored(Path root) throws Exception {
        Path file = root.resolve("compiler-core/src/main/java/Good.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Good { String text = \"com.intellij.psi.PsiFile\"; /* org.gradle.api.Project */ }");
        check(ArchitectureBoundaryChecker.inspect(root).isEmpty(), "comments and literals are not dependencies");
        Files.delete(file);
    }

    private static void expectClean(Path root) throws Exception {
        check(ArchitectureBoundaryChecker.inspect(root).isEmpty(), "empty repository must pass");
    }

    private static void expectSourceFailure(Path root, String module, String source) throws Exception {
        Path file = root.resolve(module + "/src/main/java/Bad.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        check(!ArchitectureBoundaryChecker.inspect(root).isEmpty(), module + " forbidden source must fail");
        Files.delete(file);
    }

    private static void expectBytecodeFailure(Path root) throws Exception {
        Path sources = root.resolve("fixture");
        Path classes = root.resolve("compiler-core/build/classes/java/main");
        Files.createDirectories(sources.resolve("com/intellij/psi"));
        Files.createDirectories(classes);
        Path api = sources.resolve("com/intellij/psi/PsiFile.java");
        Path bad = sources.resolve("Bad.java");
        Files.writeString(api, "package com.intellij.psi; public interface PsiFile {}");
        Files.writeString(bad, "public class Bad { public com.intellij.psi.PsiFile leaked() { return null; } }");
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), api.toString(), bad.toString());
        check(exit == 0, "fixture compilation failed");
        check(ArchitectureBoundaryChecker.inspect(root).stream().anyMatch(v -> v.contains(":bytecode:")),
                "descriptor-only bytecode reference must fail");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
