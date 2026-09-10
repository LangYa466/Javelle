/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.cli;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javelle.compiler.driver.*;

public final class JavelleCli {
  private static final String VERSION = "Javelle 0.1.0 (language 1, Java 21-25)";
  private static final Set<String> LATER = Set.of("format", "migrate", "inspect", "javelle-lsp");
  private static final AtomicBoolean CANCELLED = new AtomicBoolean();

  private JavelleCli() {}

  public static void main(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> CANCELLED.set(true), "javelle-cancel"));
    int exit;
    try {
      exit = run(args, System.out, System.err);
    } catch (IOException io) {
      err(System.err, "JV-CLI-IO: " + safe(io.getMessage()));
      exit = 4;
    } catch (RuntimeException defect) {
      err(System.err, "JV-CLI-INTERNAL: internal compiler error");
      exit = 5;
    }
    System.exit(exit);
  }

  static int run(String[] args, PrintStream out, PrintStream error) throws IOException {
    if (args.length == 0) return usage(error, "command required");
    if (args.length > 1024 || Arrays.stream(args).anyMatch(x -> x.length() > 32768))
      return usage(error, "argument limit exceeded");
    if (args.length == 1 && args[0].equals("--version")) {
      out.print(VERSION + "\n");
      return 0;
    }
    if (args.length == 1 && args[0].equals("--help")) {
      out.print(help());
      return 0;
    }
    String command = args[0];
    if (LATER.contains(command)) {
      err(error, "JV-CLI-NOT-AVAILABLE: " + command + " is not available in this version");
      return 6;
    }
    if (!Set.of("doctor", "check", "compile", "emit-java", "explain").contains(command))
      return usage(error, "unknown command: " + command);
    if (args.length == 2 && args[1].equals("--help")) {
      out.print(commandHelp(command));
      return 0;
    }
    var parsed = Arguments.parse(Arrays.copyOfRange(args, 1, args.length));
    if (parsed.error != null) return usage(error, parsed.error);
    if ("1".equals(System.getenv("JAVELLE_INTERNAL_TEST_FAULT")))
      throw new IllegalStateException("injected test fault");
    return switch (command) {
      case "doctor" -> doctor(parsed, out, error);
      case "explain" -> explain(parsed, out, error);
      case "check", "compile", "emit-java" -> compile(command, parsed, out, error);
      default -> throw new AssertionError();
    };
  }

  private static int doctor(Arguments a, PrintStream out, PrintStream error) {
    if (!a.sources.isEmpty() || a.output != null)
      return usage(error, "doctor accepts no sources/output");
    Path javac =
        (a.jdk == null ? Path.of(System.getProperty("java.home")) : Path.of(a.jdk))
            .resolve("bin")
            .resolve(isWindows() ? "javac.exe" : "javac");
    boolean healthy = false;
    String detail = "javac is missing or not executable";
    if (Files.isExecutable(javac)) {
      try {
        var process = new ProcessBuilder(javac.toString(), "-version").start();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
          detail = "javac -version timed out";
        } else {
          byte[] stdout = process.getInputStream().readNBytes(4097);
          byte[] stderr = process.getErrorStream().readNBytes(4097);
          String version =
              (new String(stdout, StandardCharsets.UTF_8)
                      + new String(stderr, StandardCharsets.UTF_8))
                  .trim();
          healthy =
              process.exitValue() == 0
                  && version.matches("javac (21|22|23|24|25)(?:\\.[0-9+._-]+)?");
          detail = healthy ? version : "unsupported or malformed javac version";
        }
      } catch (IOException | InterruptedException failure) {
        Thread.currentThread().interrupt();
        detail = "javac probe failed";
      }
    }
    if (a.json)
      out.print(
          "{\"schemaVersion\":1,\"command\":\"doctor\",\"status\":\""
              + (healthy ? "SUCCESS" : "TOOLCHAIN_OR_IO_ERROR")
              + "\",\"diagnostics\":[],\"summary\":{\"errors\":"
              + (healthy ? 0 : 1)
              + ",\"warnings\":0,\"information\":0,\"hints\":0}}\n");
    else if (healthy) out.print(detail + ": healthy\n");
    else err(error, "JV-CLI-JDK: " + detail);
    return healthy ? 0 : 4;
  }

  private static int explain(Arguments a, PrintStream out, PrintStream error) {
    if (a.sources.size() != 1 || a.output != null)
      return usage(error, "explain requires one diagnostic code");
    String code = a.sources.getFirst();
    String text;
    try {
      text = catalogExplanation(code);
    } catch (IOException brokenCatalog) {
      err(error, "JV-CLI-CATALOG: diagnostic catalog is unavailable");
      return 4;
    }
    if (text == null) return usage(error, "JV-CLI-UNKNOWN-DIAGNOSTIC: " + code);
    if (a.json) out.print("{\"code\":\"" + code + "\",\"explanation\":\"" + escape(text) + "\"}\n");
    else out.print(code + ": " + text + "\n");
    return 0;
  }

  private static String catalogExplanation(String requested) throws IOException {
    try (var stream = JavelleCli.class.getResourceAsStream("/catalog.json")) {
      if (stream == null) throw new IOException("catalog missing");
      for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
        if (!line.contains("\"code\":\"" + requested + "\"")) continue;
        return jsonField(line, "title") + ". " + jsonField(line, "fix");
      }
      return null;
    }
  }

  private static String jsonField(String objectLine, String name) throws IOException {
    String marker = "\"" + name + "\":\"";
    int start = objectLine.indexOf(marker);
    if (start < 0) throw new IOException("catalog field missing");
    start += marker.length();
    int end = objectLine.indexOf('"', start);
    if (end < 0) throw new IOException("catalog field malformed");
    return objectLine.substring(start, end);
  }

  private static int compile(String command, Arguments a, PrintStream out, PrintStream error)
      throws IOException {
    if (a.sources.isEmpty() && a.project == null)
      return usage(error, "at least one .javelle file or --project is required");
    if (!a.sources.isEmpty() && a.project != null)
      return usage(error, "explicit sources and --project are mutually exclusive");
    if (a.sources.stream().anyMatch("-"::equals)) {
      err(error, "JV-CLI-NOT-AVAILABLE: stdin source is unavailable");
      return 6;
    }
    var inputs = new ArrayList<SourceInput>();
    if (a.project != null) {
      try {
        inputs.addAll(new CliCompilerFacade().loadProject(Path.of(a.project)));
      } catch (IllegalArgumentException | IOException invalidModel) {
        err(error, "JV-CLI-MODEL: " + safe(invalidModel.getMessage()));
        return 4;
      }
    }
    for (String value : a.sources) {
      Path path = Path.of(value).toAbsolutePath().normalize();
      if (!value.endsWith(".javelle") || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
        err(error, "JV-CLI-INPUT: invalid source");
        return 4;
      }
      inputs.add(new SourceInput(path.getFileName().toString(), Files.readAllBytes(path)));
    }
    Path output =
        (a.output == null ? Path.of("javelle-out") : Path.of(a.output))
            .toAbsolutePath()
            .normalize();
    for (String value : a.sources)
      if (output.startsWith(Path.of(value).toAbsolutePath().normalize())) {
        err(error, "JV-CLI-OUTPUT: output aliases source");
        return 4;
      }
    Path scratch = Files.createTempDirectory("javelle-cli-");
    var cleanup = new Thread(() -> deleteTree(scratch), "javelle-cli-cleanup");
    Runtime.getRuntime().addShutdownHook(cleanup);
    try {
      if (a.timeoutMillis == 0) {
        err(error, "JV-CLI-TIMEOUT: operation timed out");
        return 124;
      }
      String hold = System.getenv("JAVELLE_SIGNAL_TEST_HOLD_MILLIS");
      if (hold != null) {
        try {
          Thread.sleep(Long.parseLong(hold));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return 130;
        }
      }
      Path generated =
          command.equals("compile") ? output.resolve("generated") : scratch.resolve("generated");
      Path classes =
          command.equals("compile") ? output.resolve("classes") : scratch.resolve("classes");
      long deadline = Long.MAX_VALUE;
      if (a.timeoutMillis > 0) {
        long duration = Math.multiplyExact(a.timeoutMillis, 1_000_000L);
        deadline = Math.addExact(System.nanoTime(), duration);
      }
      var result = new CliCompilerFacade().compile(inputs, generated, classes, a.release, deadline);
      if (CANCELLED.get()) return 130;
      if (!result.success()) {
        diagnostics(command, result, a.json, out, error);
        if (result.diagnostics().stream().anyMatch(x -> x.code().startsWith("JV-RESOURCE-")))
          return 124;
        if (result.diagnostics().stream().anyMatch(x -> x.code().startsWith("JV-TOOL-"))) return 4;
        return 3;
      }
      if (command.equals("emit-java")) {
        if (a.output == null) return usage(error, "emit-java requires --output");
        publishJava(output, result.generatedFiles(), result.inputFingerprint());
        if (!a.json)
          result.generatedFiles().stream()
              .map(x -> output.resolve(x.relativeUri()).toString())
              .sorted()
              .forEach(x -> out.print(x + "\n"));
      } else if (command.equals("check") && !a.json && !a.quiet) out.print("check succeeded\n");
      else if (command.equals("compile") && !a.json) out.print(output + "\n");
      if (a.json) diagnostics(command, result, true, out, error);
      return 0;
    } finally {
      deleteTree(scratch);
      try {
        Runtime.getRuntime().removeShutdownHook(cleanup);
      } catch (IllegalStateException shutdownInProgress) {
        // The hook owns cleanup once JVM shutdown has begun.
      }
    }
  }

  private static void diagnostics(
      String command,
      CliCompilerFacade.Result result,
      boolean json,
      PrintStream out,
      PrintStream error) {
    if (!json) {
      result.diagnostics().forEach(d -> err(error, d.code() + ": " + d.message()));
      return;
    }
    var b =
        new StringBuilder("{\"schemaVersion\":1,\"command\":\"")
            .append(command)
            .append("\",\"status\":\"")
            .append(result.success() ? "SUCCESS" : "COMPILATION_ERROR")
            .append("\",\"diagnostics\":[");
    for (int i = 0; i < result.diagnostics().size(); i++) {
      if (i > 0) b.append(',');
      var d = result.diagnostics().get(i);
      String uri = d.sourceUri();
      String hash = d.sourceSha256();
      int start = d.startRawUtf16(), end = d.endRawUtf16();
      b.append("{\"code\":\"")
          .append(d.code())
          .append("\",\"severity\":\"")
          .append(d.severity())
          .append("\",\"source\":{\"uri\":\"")
          .append(escape(uri))
          .append("\",\"sha256\":\"")
          .append(hash)
          .append("\"},\"range\":{\"unit\":\"RAW_UTF16\",\"start\":")
          .append(start)
          .append(",\"end\":")
          .append(end)
          .append("},\"message\":\"")
          .append(escape(d.message()))
          .append("\",\"relatedInformation\":")
          .append(d.metadata().relatedJson())
          .append(",\"fixes\":")
          .append(d.metadata().fixesJson())
          .append(",\"data\":")
          .append(d.metadata().dataJson())
          .append('}');
    }
    long errors = result.diagnostics().stream().filter(x -> x.severity().equals("ERROR")).count();
    b.append("],\"summary\":{\"errors\":")
        .append(errors)
        .append(",\"warnings\":0,\"information\":0,\"hints\":0}}\n");
    out.print(b);
  }

  private static void publishJava(
      Path output, List<CliCompilerFacade.JavaFile> files, String inputFingerprint)
      throws IOException {
    Path parent = output.getParent();
    if (parent == null) throw new IOException("output requires parent");
    Files.createDirectories(parent);
    String nonce = Long.toUnsignedString(ProcessHandle.current().pid()) + "-" + System.nanoTime();
    Path candidate = parent.resolve("." + output.getFileName() + ".javelle-stage-" + nonce);
    Path backup = parent.resolve("." + output.getFileName() + ".javelle-backup-" + nonce);
    var previous = new TreeMap<String, String>();
    try {
      Files.createDirectories(candidate);
      publishFault("candidate");
      if (Files.exists(output)) {
        rejectSymlinks(output);
        Path manifest = output.resolve("META-INF/javelle/generated-files-v1.json");
        if (!Files.isRegularFile(manifest)) throw new IOException("existing output is not owned");
        previous.putAll(parseManifest(Files.readString(manifest, StandardCharsets.UTF_8)));
        for (var entry : previous.entrySet()) {
          Path owned = safeResolve(output, entry.getKey());
          if (!Files.isRegularFile(owned)
              || !sha(Files.readAllBytes(owned)).equals(entry.getValue()))
            throw new IOException("owned output changed: " + entry.getKey());
        }
        copyTree(output, candidate);
        for (String owned : previous.keySet()) Files.deleteIfExists(safeResolve(candidate, owned));
        Files.deleteIfExists(candidate.resolve("META-INF/javelle/generated-files-v1.json"));
      }
      var entries = new TreeMap<String, OwnedEntry>();
      for (var file : files) {
        writeCandidate(
            candidate, file.relativeUri(), file.javaText().getBytes(StandardCharsets.UTF_8));
        entries.put(file.relativeUri(), new OwnedEntry("JAVA", file.sha256()));
        String mapPath = file.relativeUri() + ".map.json";
        writeCandidate(candidate, mapPath, file.sourceMapJson().getBytes(StandardCharsets.UTF_8));
        entries.put(mapPath, new OwnedEntry("SOURCE_MAP", file.mapSha256()));
      }
      publishFault("write");
      Path manifest = candidate.resolve("META-INF/javelle/generated-files-v1.json");
      Files.createDirectories(manifest.getParent());
      Files.writeString(
          manifest,
          manifest(entries, inputFingerprint),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
      publishFault("manifest");
      forceTree(candidate);
      forceDirectory(parent);
      publishFault("fsync");
      if (!Files.exists(output)) Files.move(candidate, output, StandardCopyOption.ATOMIC_MOVE);
      else {
        Files.move(output, backup, StandardCopyOption.ATOMIC_MOVE);
        try {
          publishFault("move");
          Files.move(candidate, output, StandardCopyOption.ATOMIC_MOVE);
          forceDirectory(parent);
          deleteTree(backup);
        } catch (IOException failure) {
          Files.move(backup, output, StandardCopyOption.ATOMIC_MOVE);
          throw failure;
        }
      }
    } finally {
      deleteTree(candidate);
      deleteTree(backup);
    }
  }

  private static void writeCandidate(Path root, String relative, byte[] bytes) throws IOException {
    Path target = safeResolve(root, relative);
    if (Files.exists(target)) throw new IOException("foreign output collision: " + relative);
    Files.createDirectories(target.getParent());
    Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
  }

  private static Path safeResolve(Path root, String relative) throws IOException {
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root) || Path.of(relative).isAbsolute())
      throw new IOException("output traversal");
    return target;
  }

  private static void rejectSymlinks(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      if (paths.anyMatch(Files::isSymbolicLink)) throw new IOException("output symlink rejected");
    }
  }

  private static void copyTree(Path source, Path target) throws IOException {
    try (var paths = Files.walk(source)) {
      for (Path path : paths.toList()) {
        Path copy = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) Files.createDirectories(copy);
        else {
          Files.createDirectories(copy.getParent());
          Files.copy(path, copy);
        }
      }
    }
  }

  private static Map<String, String> parseManifest(String json) throws IOException {
    if (!json.startsWith("{\"schemaVersion\":1,")) throw new IOException("corrupt manifest");
    var result = new TreeMap<String, String>();
    int cursor = 0;
    String pathKey = "\"relativePath\":\"", hashKey = "\"sha256\":\"";
    while ((cursor = json.indexOf(pathKey, cursor)) >= 0) {
      int pathStart = cursor + pathKey.length(), pathEnd = json.indexOf('"', pathStart);
      int hashStart = json.indexOf(hashKey, pathEnd);
      if (pathEnd < 0 || hashStart < 0) throw new IOException("corrupt manifest");
      hashStart += hashKey.length();
      int hashEnd = json.indexOf('"', hashStart);
      String path = json.substring(pathStart, pathEnd), hash = json.substring(hashStart, hashEnd);
      if (!hash.matches("[0-9a-f]{64}") || result.put(path, hash) != null)
        throw new IOException("corrupt manifest");
      cursor = hashEnd;
    }
    if (result.isEmpty()) throw new IOException("corrupt manifest");
    return result;
  }

  private static String manifest(Map<String, OwnedEntry> entries, String inputFingerprint) {
    var out =
        new StringBuilder(
                "{\"schemaVersion\":1,\"generatorVersion\":\"0.1.0\",\"inputFingerprint\":\"")
            .append(inputFingerprint)
            .append("\",\"files\":[");
    int i = 0;
    for (var entry : entries.entrySet()) {
      if (i++ > 0) out.append(',');
      out.append("{\"relativePath\":\"")
          .append(escape(entry.getKey()))
          .append("\",\"sha256\":\"")
          .append(entry.getValue().sha256)
          .append("\",\"kind\":\"")
          .append(entry.getValue().kind)
          .append("\"}");
    }
    return out.append("]}\n").toString();
  }

  private static String sha(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private record OwnedEntry(String kind, String sha256) {}

  private static void publishFault(String phase) throws IOException {
    if (phase.equals(System.getenv("JAVELLE_PUBLISH_TEST_FAULT")))
      throw new IOException("injected publication failure");
  }

  private static void forceTree(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (Files.isRegularFile(path)) {
          try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
          }
        } else forceDirectory(path);
      }
    }
  }

  private static void forceDirectory(Path directory) throws IOException {
    try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private static String help() {
    return "Usage: javelle <command> [options]\nCommands: doctor, check, compile, emit-java, explain\n";
  }

  private static String commandHelp(String c) {
    return "Usage: javelle "
        + c
        + (c.equals("explain")
            ? " <diagnostic-code>"
            : c.equals("doctor")
                ? " [--format human|json]"
                : " <file.javelle> [--diagnostics human|json] [--output <dir>]")
        + "\n";
  }

  private static int usage(PrintStream e, String m) {
    err(e, "JV-CLI-USAGE: " + m);
    return 2;
  }

  private static void err(PrintStream e, String m) {
    e.print(m.replace('\r', ' ').replace('\n', ' ') + "\n");
  }

  private static String safe(String m) {
    return m == null ? "I/O failure" : m.replace(System.getProperty("user.home"), "<home>");
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private static void deleteTree(Path root) {
    try {
      if (Files.exists(root))
        try (var s = Files.walk(root)) {
          for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    } catch (IOException ignored) {
    }
  }

  private static final class Arguments {
    final List<String> sources = new ArrayList<>();
    String output, project, jdk, error;
    int release = 21;
    long timeoutMillis = -1;
    boolean json, quiet;

    static Arguments parse(String[] xs) {
      var a = new Arguments();
      boolean options = true;
      for (int i = 0; i < xs.length; i++) {
        String x = xs[i];
        if (options && x.equals("--")) {
          options = false;
          continue;
        }
        if (options && x.startsWith("--")) {
          switch (x) {
            case "--quiet", "--no-color" -> {
              if (x.equals("--quiet")) a.quiet = true;
            }
            case "--diagnostics", "--format" -> {
              if (++i >= xs.length) {
                a.error = "missing value for " + x;
                return a;
              }
              String v = xs[i];
              if (!Set.of("human", "json").contains(v)) {
                a.error = "invalid format";
                return a;
              }
              a.json = v.equals("json");
            }
            case "--output" -> {
              if (a.output != null || ++i >= xs.length) {
                a.error = "duplicate or missing --output";
                return a;
              }
              a.output = xs[i];
            }
            case "--jdk" -> {
              if (a.jdk != null || ++i >= xs.length) {
                a.error = "duplicate or missing --jdk";
                return a;
              }
              a.jdk = xs[i];
            }
            case "--release" -> {
              if (++i >= xs.length) {
                a.error = "missing --release";
                return a;
              }
              try {
                a.release = Integer.parseInt(xs[i]);
              } catch (NumberFormatException badRelease) {
                a.error = "invalid --release";
                return a;
              }
            }
            case "--timeout-ms" -> {
              if (++i >= xs.length) {
                a.error = "missing --timeout-ms";
                return a;
              }
              try {
                a.timeoutMillis = Long.parseLong(xs[i]);
              } catch (NumberFormatException badTimeout) {
                a.error = "invalid --timeout-ms";
                return a;
              }
              if (a.timeoutMillis < 0) {
                a.error = "invalid --timeout-ms";
                return a;
              }
            }
            case "--project" -> {
              if (a.project != null || ++i >= xs.length) {
                a.error = "duplicate or missing --project";
                return a;
              }
              a.project = xs[i];
            }
            default -> {
              a.error = "unknown option: " + x;
              return a;
            }
          }
        } else a.sources.add(x);
      }
      return a;
    }
  }
}
