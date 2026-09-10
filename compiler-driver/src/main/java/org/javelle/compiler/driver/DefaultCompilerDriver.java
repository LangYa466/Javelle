/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.tools.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.emitter.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.lowering.GeneratedFile;
import org.javelle.compiler.core.semantic.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.sourcemap.*;
import org.javelle.java.resolver.JavaPathResolver;

/** Public-JDK compiler pipeline with explicit inputs and publish-on-success output ownership. */
public final class DefaultCompilerDriver implements CompilerDriver {
  private static final String MANIFEST = "META-INF/javelle/generated-files-v1.json";
  private final Consumer<String> publishFault;

  public DefaultCompilerDriver() {
    this(step -> {});
  }

  DefaultCompilerDriver(Consumer<String> publishFault) {
    this.publishFault = Objects.requireNonNull(publishFault);
  }

  @Override
  public CompileResult compile(CompileRequest request, CancellationToken cancellation) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(cancellation);
    var generated = new ArrayList<GeneratedFile>();
    var messages = new ArrayList<JavacMessage>();
    Path staging = null;
    try {
      validate(request);
      var tracker = new ResourceTracker(request.budget(), cancellation, System::nanoTime);
      tracker.checkpoint();
      for (SourceInput input : request.javelleSources()) {
        tracker.addBytes(input.utf8().length);
        SourceFile source =
            SourceFile.decode(
                SourceId.forContent(input.relativeUri(), input.utf8()),
                input.utf8(),
                request.budget());
        ParseResult parsed =
            new JavelleFrontend()
                .parse(
                    source,
                    new FrontendOptions(1, request.budget().maxDiagnostics()),
                    request.budget(),
                    cancellation);
        BindingResult binding = new EarlySemanticBinder().bind(parsed, source, "main");
        if (binding.unit().isEmpty()) {
          binding.diagnostics().forEach(d -> messages.add(frontendMessage(d, source)));
          return new CompileResult(false, -1, generated, messages, Optional.empty());
        }
        EmitResult emitted =
            new DeterministicJavaEmitter()
                .emit(
                    binding.unit().orElseThrow(),
                    new EmitOptions(1, request.release(), "0.1.0-SNAPSHOT", "    ", "\n"),
                    tracker,
                    cancellation);
        if (!emitted.diagnostics().isEmpty()) {
          emitted.diagnostics().forEach(d -> messages.add(frontendMessage(d, source)));
          return new CompileResult(false, -1, generated, messages, Optional.empty());
        }
        generated.addAll(emitted.files());
      }
      rejectDuplicateGenerated(generated);
      tracker.checkpoint();
      Path parent = request.classOutput().toAbsolutePath().normalize().getParent();
      if (parent == null) throw new IllegalArgumentException("class output requires parent");
      Files.createDirectories(parent);
      staging = Files.createTempDirectory(parent, ".javelle-compile-");
      Path generatedStage = staging.resolve("generated");
      Path classesStage = staging.resolve("classes");
      Files.createDirectories(generatedStage);
      Files.createDirectories(classesStage);
      for (GeneratedFile file : generated)
        writeOwned(
            generatedStage, file.relativeUri(), file.javaText().getBytes(StandardCharsets.UTF_8));

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler == null) {
        messages.add(toolMessage("JV-TOOL-0001", "system Java compiler is unavailable"));
        return new CompileResult(false, -1, generated, messages, Optional.empty());
      }
      var javacDiagnostics = new DiagnosticCollector<JavaFileObject>();
      int exit;
      try (StandardJavaFileManager manager =
          compiler.getStandardFileManager(javacDiagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
        var sources = new ArrayList<File>();
        for (GeneratedFile file : generated)
          sources.add(generatedStage.resolve(file.relativeUri()).toFile());
        for (Path java : request.javaSources())
          sources.add(java.toAbsolutePath().normalize().toFile());
        var options = options(request, classesStage);
        cancellation.throwIfCancelled();
        boolean ok =
            compiler
                .getTask(
                    null,
                    manager,
                    javacDiagnostics,
                    options,
                    null,
                    manager.getJavaFileObjectsFromFiles(sources))
                .call();
        exit = ok ? 0 : 1;
      }
      for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic :
          javacDiagnostics.getDiagnostics()) {
        if (messages.size() >= request.budget().maxDiagnostics()) break;
        messages.add(remap(diagnostic, generatedStage, generated));
      }
      if (exit != 0) return new CompileResult(false, exit, generated, messages, Optional.empty());
      cancellation.throwIfCancelled();
      tracker.checkpoint();
      try (var paths = Files.walk(classesStage)) {
        tracker.addBytes(
            paths
                .filter(Files::isRegularFile)
                .mapToLong(
                    path -> {
                      try {
                        return Files.size(path);
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                    })
                .sum());
      }
      PublishedOutputs outputs = publish(request, generatedStage, classesStage, generated);
      deleteTree(staging);
      staging = null;
      return new CompileResult(true, 0, generated, messages, Optional.of(outputs));
    } catch (CancellationException | ResourceLimitException e) {
      messages.add(toolMessage("JV-RESOURCE-0001", e.getMessage()));
      return new CompileResult(false, -1, generated, messages, Optional.empty());
    } catch (IOException | RuntimeException e) {
      messages.add(
          toolMessage(
              "JV-TOOL-0002",
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      return new CompileResult(false, -1, generated, messages, Optional.empty());
    } finally {
      if (staging != null) deleteTree(staging);
    }
  }

  private static void validate(CompileRequest request) throws IOException {
    int runtime = Runtime.version().feature();
    if ((request.release() != 21 && request.release() != 25) || request.release() > runtime)
      throw new IllegalArgumentException(
          "release must be 21 or 25 and not exceed toolchain " + runtime);
    if (request.enableProcessors() || !request.processorPath().isEmpty())
      throw new IllegalArgumentException("JV-DEV-0001(annotation-processing)");
    Path generated = request.generatedRoot().toAbsolutePath().normalize();
    Path classes = request.classOutput().toAbsolutePath().normalize();
    if (generated.equals(classes) || generated.startsWith(classes) || classes.startsWith(generated))
      throw new IllegalArgumentException("output roots must not alias or nest");
    if (Files.isSymbolicLink(generated) || Files.isSymbolicLink(classes))
      throw new IllegalArgumentException("symbolic output root rejected");
    new JavaPathResolver().resolveExisting(request.classpath());
    new JavaPathResolver().resolveExisting(request.modulePath());
    for (Path source : request.javaSources())
      if (!Files.isRegularFile(source))
        throw new IllegalArgumentException("missing Java source: " + source);
  }

  private static List<String> options(CompileRequest request, Path classes) {
    var result =
        new ArrayList<>(
            List.of(
                "--release",
                Integer.toString(request.release()),
                "-encoding",
                "UTF-8",
                "-d",
                classes.toString(),
                "-proc:none"));
    if (!request.classpath().isEmpty()) {
      result.add("--class-path");
      result.add(join(request.classpath()));
    }
    if (!request.modulePath().isEmpty()) {
      result.add("--module-path");
      result.add(join(request.modulePath()));
    }
    return result;
  }

  private static String join(List<Path> paths) {
    return String.join(
        File.pathSeparator,
        new JavaPathResolver().resolveExisting(paths).stream().map(Path::toString).toList());
  }

  private static void rejectDuplicateGenerated(List<GeneratedFile> files) {
    var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (GeneratedFile file : files)
      if (!names.add(file.relativeUri()))
        throw new IllegalArgumentException("generated path collision: " + file.relativeUri());
  }

  private static void writeOwned(Path root, String relative, byte[] bytes) throws IOException {
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root)) throw new IllegalArgumentException("output traversal");
    Files.createDirectories(target.getParent());
    Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
  }

  private static JavacMessage remap(
      javax.tools.Diagnostic<? extends JavaFileObject> d, Path stage, List<GeneratedFile> files) {
    Path diagnosticPath = d.getSource() == null ? null : Path.of(d.getSource().toUri());
    String uri =
        diagnosticPath == null
            ? ""
            : diagnosticPath.normalize().startsWith(stage.normalize())
                ? stage.relativize(diagnosticPath).toString().replace('\\', '/')
                : diagnosticPath.getFileName().toString();
    GeneratedFile file =
        files.stream().filter(f -> f.relativeUri().equals(uri)).findFirst().orElse(null);
    int start16 = (int) Math.max(0, d.getStartPosition());
    int end16 = (int) Math.max(start16, d.getEndPosition());
    int startCp = start16;
    int endCp = end16;
    List<SourceLocation> originals = List.of();
    if (file != null) {
      start16 = Math.min(start16, file.javaText().length());
      end16 = Math.min(end16, file.javaText().length());
      startCp = file.javaText().codePointCount(0, start16);
      endCp = file.javaText().codePointCount(0, end16);
      var hits =
          file.sourceMap()
              .generatedOverlapping(
                  new GeneratedRange(
                      uri,
                      new TextRange(
                          OffsetUnit.UNICODE_CODE_POINT, startCp, Math.max(startCp + 1, endCp))));
      originals = hits.stream().flatMap(s -> s.originals().stream()).distinct().toList();
    }
    return new JavacMessage(
        new DiagnosticCode("JV-JAVAC-" + severity(d.getKind()).name()),
        severity(d.getKind()),
        uri,
        new TextRange(OffsetUnit.UNICODE_CODE_POINT, startCp, endCp),
        originals,
        (d.getCode() == null ? "javac" : d.getCode()) + ": " + d.getMessage(Locale.ROOT));
  }

  private static Severity severity(javax.tools.Diagnostic.Kind kind) {
    return switch (kind) {
      case ERROR -> Severity.ERROR;
      case WARNING, MANDATORY_WARNING -> Severity.WARNING;
      case NOTE -> Severity.INFORMATION;
      default -> Severity.HINT;
    };
  }

  private static JavacMessage frontendMessage(
      org.javelle.compiler.core.diagnostic.Diagnostic d, SourceFile source) {
    int start =
        source.convertBoundary(
            d.range().startOffset(), d.range().unit(), OffsetUnit.UNICODE_CODE_POINT, Bias.START);
    int end =
        source.convertBoundary(
            d.range().endOffset(), d.range().unit(), OffsetUnit.UNICODE_CODE_POINT, Bias.END);
    var mapped = new TextRange(OffsetUnit.UNICODE_CODE_POINT, start, end);
    return new JavacMessage(
        d.code(),
        d.severity(),
        "",
        mapped,
        List.of(new SourceLocation(d.source(), mapped)),
        d.message());
  }

  private static JavacMessage toolMessage(String code, String message) {
    return new JavacMessage(
        new DiagnosticCode(code),
        Severity.ERROR,
        "",
        new TextRange(OffsetUnit.UNICODE_CODE_POINT, 0, 0),
        List.of(),
        message);
  }

  private PublishedOutputs publish(
      CompileRequest request, Path generatedStage, Path classesStage, List<GeneratedFile> generated)
      throws IOException {
    Path generatedRoot = request.generatedRoot().toAbsolutePath().normalize();
    Path classRoot = request.classOutput().toAbsolutePath().normalize();
    Files.createDirectories(generatedRoot);
    Files.createDirectories(classRoot);
    var entries = new ArrayList<Entry>();
    for (GeneratedFile file : generated)
      entries.add(new Entry("JAVA", file.relativeUri(), file.contentSha256()));
    try (var paths = Files.walk(classesStage)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        String relative = classesStage.relativize(path).toString().replace('\\', '/');
        entries.add(new Entry("CLASS", relative, sha(Files.readAllBytes(path))));
      }
    }
    Path manifest = classRoot.resolve(MANIFEST);
    List<Entry> previous =
        Files.exists(manifest)
            ? parseManifest(Files.readString(manifest, StandardCharsets.UTF_8))
            : List.of();
    rejectForeignCollisions(previous, generatedRoot, classRoot, entries);
    Path backup = generatedStage.getParent().resolve("rollback");
    backupPublished(previous, manifest, generatedRoot, classRoot, backup);
    String inputHash =
        sha(
            request.javelleSources().stream()
                .flatMapToInt(
                    s ->
                        java.util.stream.IntStream.range(0, s.utf8().length)
                            .map(i -> s.utf8()[i] & 255))
                .collect(StringBuilder::new, (b, n) -> b.append((char) n), StringBuilder::append)
                .toString()
                .getBytes(StandardCharsets.ISO_8859_1));
    try {
      cleanupPrevious(manifest, generatedRoot, classRoot, entries);
      publishFault.accept("after-stale-cleanup");
      moveFiles(generatedStage, generatedRoot);
      publishFault.accept("after-generated-move");
      moveFiles(classesStage, classRoot);
      publishFault.accept("after-classes-move");
      publishFault.accept("before-manifest");
      Files.createDirectories(manifest.getParent());
      Files.writeString(
          manifest,
          manifestJson(inputHash, entries),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      publishFault.accept("after-manifest");
    } catch (IOException | RuntimeException failure) {
      rollbackPublished(previous, entries, manifest, generatedRoot, classRoot, backup);
      throw failure;
    }
    return new PublishedOutputs(generatedRoot, classRoot, manifest);
  }

  private static void backupPublished(
      List<Entry> previous, Path manifest, Path generated, Path classes, Path backup)
      throws IOException {
    Files.createDirectories(backup);
    for (Entry entry : previous) {
      Path root = entry.kind.equals("JAVA") ? generated : classes;
      Path source = safeTarget(root, entry.path);
      if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        copyToBackup(source, backup.resolve(entry.kind).resolve(entry.path));
    }
    if (Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
      copyToBackup(manifest, backup.resolve("manifest.json"));
  }

  private static void rollbackPublished(
      List<Entry> previous,
      List<Entry> next,
      Path manifest,
      Path generated,
      Path classes,
      Path backup)
      throws IOException {
    var affected = new LinkedHashMap<String, Entry>();
    previous.forEach(entry -> affected.put(entry.kind + ":" + entry.path, entry));
    next.forEach(entry -> affected.put(entry.kind + ":" + entry.path, entry));
    for (Entry entry : affected.values()) {
      Path root = entry.kind.equals("JAVA") ? generated : classes;
      Files.deleteIfExists(safeTarget(root, entry.path));
    }
    for (Entry entry : previous) {
      Path saved = backup.resolve(entry.kind).resolve(entry.path);
      if (Files.isRegularFile(saved, LinkOption.NOFOLLOW_LINKS))
        copyToBackup(
            saved, safeTarget(entry.kind.equals("JAVA") ? generated : classes, entry.path));
    }
    Files.deleteIfExists(manifest);
    Path savedManifest = backup.resolve("manifest.json");
    if (Files.isRegularFile(savedManifest, LinkOption.NOFOLLOW_LINKS))
      copyToBackup(savedManifest, manifest);
  }

  private static void copyToBackup(Path source, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    Files.copy(
        source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
  }

  private static void rejectForeignCollisions(
      List<Entry> previous, Path generated, Path classes, List<Entry> next) throws IOException {
    Set<String> owned = new HashSet<>();
    Map<String, Entry> oldEntries = new HashMap<>();
    previous.forEach(
        e -> {
          owned.add(e.kind + ":" + e.path);
          oldEntries.put(e.kind + ":" + e.path, e);
        });
    for (Entry entry : next) {
      Path root = entry.kind.equals("JAVA") ? generated : classes;
      Path target = safeTarget(root, entry.path);
      String key = entry.kind + ":" + entry.path;
      if (Files.exists(target)) {
        if (!owned.contains(key))
          throw new IOException("refusing to overwrite foreign output: " + entry.path);
        if (!sha(Files.readAllBytes(target)).equals(oldEntries.get(key).hash))
          throw new IOException("refusing to overwrite changed owned output: " + entry.path);
      }
    }
  }

  private static Path safeTarget(Path root, String relative) throws IOException {
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root)) throw new IOException("output traversal: " + relative);
    Path cursor = root;
    Path rel = root.relativize(target);
    for (Path part : rel) {
      cursor = cursor.resolve(part);
      if (Files.isSymbolicLink(cursor))
        throw new IOException("descendant symlink rejected: " + relative);
    }
    return target;
  }

  private static void moveFiles(Path from, Path to) throws IOException {
    try (var paths = Files.walk(from)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        Path target = to.resolve(from.relativize(path)).normalize();
        if (!target.startsWith(to)) throw new IllegalArgumentException("publish traversal");
        Files.createDirectories(target.getParent());
        Files.move(
            path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }
    }
  }

  private static void cleanupPrevious(Path manifest, Path generated, Path classes, List<Entry> next)
      throws IOException {
    if (!Files.exists(manifest)) return;
    Set<String> retained = new HashSet<>();
    next.forEach(e -> retained.add(e.kind + ":" + e.path));
    for (Entry old : parseManifest(Files.readString(manifest, StandardCharsets.UTF_8))) {
      if (retained.contains(old.kind + ":" + old.path)) continue;
      Path root = old.kind.equals("JAVA") ? generated : classes;
      Path target = safeTarget(root, old.path);
      if (!target.startsWith(root)
          || !Files.isRegularFile(target)
          || !sha(Files.readAllBytes(target)).equals(old.hash))
        throw new IOException("refusing cleanup of foreign or changed owned output: " + old.path);
      Files.delete(target);
    }
  }

  private static List<Entry> parseManifest(String json) throws IOException {
    if (!json.startsWith("{\"schemaVersion\":1,"))
      throw new IOException("invalid ownership manifest");
    var result = new ArrayList<Entry>();
    int at = 0;
    while ((at = json.indexOf("{\"relativePath\":\"", at)) >= 0) {
      int p = at + 17, pe = json.indexOf('"', p);
      int h = json.indexOf("\"sha256\":\"", pe) + 10, he = json.indexOf('"', h);
      int k = json.indexOf("\"kind\":\"", he) + 8, ke = json.indexOf('"', k);
      if (pe < p || h < 10 || he < h || k < 8 || ke < k)
        throw new IOException("invalid ownership manifest entry");
      result.add(new Entry(json.substring(k, ke), json.substring(p, pe), json.substring(h, he)));
      at = ke;
    }
    return result;
  }

  private static String manifestJson(String inputHash, List<Entry> entries) {
    var out =
        new StringBuilder(
                "{\"schemaVersion\":1,\"generatorVersion\":\"0.1.0-SNAPSHOT\",\"inputFingerprint\":\"")
            .append(inputHash)
            .append("\",\"files\":[");
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) out.append(',');
      Entry e = entries.get(i);
      out.append("{\"relativePath\":\"")
          .append(e.path)
          .append("\",\"sha256\":\"")
          .append(e.hash)
          .append("\",\"kind\":\"")
          .append(e.kind)
          .append("\"}");
    }
    return out.append("]}\n").toString();
  }

  private static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void deleteTree(Path root) {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup; the unique staging path is never published.
    }
  }

  private record Entry(String kind, String path, String hash) {}
}
