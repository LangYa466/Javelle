/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.buildlogic.architecture;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Enforces source and compiled-bytecode module boundaries without loading inspected classes. */
public final class ArchitectureBoundaryChecker {
    private static final Map<String, List<String>> FORBIDDEN = Map.of(
            "compiler-core", List.of("org.gradle.", "com.intellij.", "org.eclipse.lsp4j.", "org.javelle.workspace."),
            "workspace-model", List.of("org.gradle.", "com.intellij.", "org.eclipse.lsp4j."),
            "language-protocol", List.of("org.javelle.compiler.", "org.javelle.semantic."),
            "language-server", List.of("com.intellij."),
            "intellij-plugin", List.of("org.javelle.compiler.", "org.javelle.resolver."),
            "gradle-plugin", List.of("org.javelle.compiler.", "org.javelle.resolver."));
    private static final Pattern FQN = Pattern.compile("(?:[a-zA-Z_$][\\w$]*\\.){2,}[A-Za-z_$][\\w$]*");

    private ArchitectureBoundaryChecker() {}

    public static List<String> inspect(Path repository) throws IOException {
        List<String> violations = new ArrayList<>();
        for (var entry : FORBIDDEN.entrySet()) {
            Path module = repository.resolve(entry.getKey());
            inspectSources(module.resolve("src/main/java"), entry.getKey(), entry.getValue(), violations);
            inspectClasses(module.resolve("build/classes/java/main"), entry.getKey(), entry.getValue(), violations);
        }
        return violations;
    }

    private static void inspectSources(Path root, String module, List<String> prefixes, List<String> out)
            throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = stripCommentsAndLiterals(Files.readString(path, StandardCharsets.UTF_8));
                Matcher matcher = FQN.matcher(text);
                while (matcher.find()) {
                    String reference = matcher.group();
                    forbiddenPrefix(reference, prefixes).ifPresent(prefix -> out.add(
                            module + ":source:" + root.relativize(path) + ":" + prefix + ":" + reference));
                }
            }
        }
    }

    private static void inspectClasses(Path root, String module, List<String> prefixes, List<String> out)
            throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                for (String reference : constantPoolReferences(path)) {
                    forbiddenPrefix(reference, prefixes).ifPresent(prefix -> out.add(
                            module + ":bytecode:" + root.relativize(path) + ":" + prefix + ":" + reference));
                }
            }
        }
    }

    private static java.util.Optional<String> forbiddenPrefix(String value, List<String> prefixes) {
        String normalized = value.replace('/', '.');
        return prefixes.stream().filter(normalized::contains).findFirst();
    }

    static String stripCommentsAndLiterals(String input) {
        StringBuilder result = new StringBuilder(input.length());
        int state = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i), next = i + 1 < input.length() ? input.charAt(i + 1) : 0;
            if (state == 0 && c == '/' && next == '/') { state = 1; result.append("  "); i++; }
            else if (state == 0 && c == '/' && next == '*') { state = 2; result.append("  "); i++; }
            else if (state == 0 && (c == '"' || c == '\'')) { state = c == '"' ? 3 : 4; result.append(' '); }
            else if (state == 1 && (c == '\n' || c == '\r')) { state = 0; result.append(c); }
            else if (state == 2 && c == '*' && next == '/') { state = 0; result.append("  "); i++; }
            else if ((state == 3 || state == 4) && c == '\\') { result.append("  "); i++; }
            else if (state == 3 && c == '"' || state == 4 && c == '\'') { state = 0; result.append(' '); }
            else result.append(state == 0 ? c : (c == '\n' ? '\n' : ' '));
        }
        return result.toString();
    }

    static List<String> constantPoolReferences(Path classFile) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
            if (in.readInt() != 0xCAFEBABE) throw new IOException("Not a class file: " + classFile);
            in.readUnsignedShort(); in.readUnsignedShort();
            int count = in.readUnsignedShort();
            Map<Integer, String> utf8 = new HashMap<>();
            List<Integer> classNames = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8.put(i, in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> { in.skipBytes(8); i++; }
                    case 7 -> classNames.add(in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException("Unknown constant-pool tag " + tag + " in " + classFile);
                }
            }
            List<String> result = new ArrayList<>(utf8.values()); // descriptors/signatures contain FQNs too
            classNames.stream().map(utf8::get).filter(java.util.Objects::nonNull).forEach(result::add);
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: ArchitectureBoundaryChecker <repository>");
        List<String> violations = inspect(Path.of(args[0]).toAbsolutePath().normalize());
        if (!violations.isEmpty()) throw new IllegalStateException("Forbidden architecture references:\n" + String.join("\n", violations));
        System.out.println("ARCHITECTURE_BOUNDARIES_OK");
    }
}
