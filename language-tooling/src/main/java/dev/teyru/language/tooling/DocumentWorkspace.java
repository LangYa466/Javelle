/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.language.tooling;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import dev.teyru.compiler.driver.*;
import dev.teyru.workspace.model.WorkspaceModel;

/** Thread-safe immutable document snapshots backed by the compiler-driver facade. */
public final class DocumentWorkspace implements AutoCloseable {
  private final ConcurrentMap<String, Snapshot> documents = new ConcurrentHashMap<>();
  private final Runnable analysisCheckpoint;
  private volatile Optional<WorkspaceModel> workspace = Optional.empty();
  private final ConcurrentMap<String, SemanticIndex> indexes = new ConcurrentHashMap<>();

  private record SemanticIndex(Map<String, String> hover, List<String> completion) {}

  public DocumentWorkspace() {
    this(testAnalysisDelayFromEnvironment());
  }

  DocumentWorkspace(Runnable analysisCheckpoint) {
    this.analysisCheckpoint = Objects.requireNonNull(analysisCheckpoint);
  }

  static Runnable testAnalysisDelayFromEnvironment() {
    String marker = System.getenv("TEYRU_LSP_TEST_ANALYSIS_MARKER");
    String target = System.getenv("TEYRU_LSP_TEST_ANALYSIS_CHECKPOINT");
    if (marker == null || target == null) return () -> {};
    int targetCall = Integer.parseInt(target);
    var calls = new AtomicInteger();
    return () -> {
      if (calls.incrementAndGet() != targetCall) return;
      try {
        Files.writeString(Path.of(marker), "running\n", StandardOpenOption.CREATE_NEW);
        while (!Thread.currentThread().isInterrupted()) Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  public record Position(int line, int character) {}

  public record Range(Position start, Position end) {}

  public record Change(Optional<Range> range, String text) {
    public Change {
      range = Objects.requireNonNull(range);
      Objects.requireNonNull(text);
    }
  }

  public record Snapshot(
      String uri,
      int version,
      String text,
      String fingerprint,
      String workspaceFingerprint,
      String overlayFingerprint) {}

  public record Message(String code, String severity, Range range, String message) {}

  public void configure(WorkspaceModel model) {
    workspace = Optional.of(Objects.requireNonNull(model));
    documents.clear();
  }

  public Snapshot open(String uri, int version, String text) {
    String normalized = normalizeUri(uri);
    var next = snapshot(normalized, version, text);
    documents.compute(
        normalized,
        (key, old) -> {
          if (old != null) throw new IllegalStateException("already open");
          return next;
        });
    return next;
  }

  public Optional<Snapshot> change(String uri, int version, List<Change> changes) {
    String normalized = normalizeUri(uri);
    var result = new AtomicReference<Snapshot>();
    documents.compute(
        normalized,
        (key, old) -> {
          if (old == null) throw new IllegalStateException("document not open");
          if (version <= old.version()) return old;
          String text = old.text();
          for (Change change : changes) text = apply(text, change);
          var next = snapshot(normalized, version, text);
          result.set(next);
          return next;
        });
    return Optional.ofNullable(result.get());
  }

  public Optional<Snapshot> close(String uri) {
    return Optional.ofNullable(documents.remove(normalizeUri(uri)));
  }

  public Optional<Snapshot> current(String uri) {
    return Optional.ofNullable(documents.get(normalizeUri(uri)));
  }

  public boolean isCurrent(Snapshot snapshot) {
    return snapshot.equals(documents.get(snapshot.uri()));
  }

  public List<Message> analyze(Snapshot snapshot) {
    if (!isCurrent(snapshot)) return List.of();
    analysisCheckpoint.run();
    cancelled();
    Path temp = null;
    try {
      temp = Files.createTempDirectory("teyru-lsp-analysis-");
      var teyruSources = new ArrayList<SourceInput>();
      teyruSources.add(
          new SourceInput(
              relative(snapshot.uri()), snapshot.text().getBytes(StandardCharsets.UTF_8)));
      for (Path source : workspacePaths(workspace, true, ".teyru"))
        if (!source.toUri().toASCIIString().equals(snapshot.uri()))
          teyruSources.add(
              new SourceInput(source.getFileName().toString(), Files.readAllBytes(source)));
      var result =
          new CliCompilerFacade()
              .compile(
                  teyruSources,
                  temp.resolve("java"),
                  temp.resolve("classes"),
                  workspaceRelease(workspace));
      cancelled();
      analysisCheckpoint.run();
      cancelled();
      if (!isCurrent(snapshot)) return List.of();
      indexes.put(
          snapshot.fingerprint(),
          semanticIndex(
              result.generatedFiles(), workspacePaths(workspace, false, ".java"), workspace));
      var messages = new ArrayList<Message>();
      for (var d : result.diagnostics())
        messages.add(
            new Message(
                d.code(),
                d.severity(),
                range(snapshot.text(), d.startRawUtf16(), d.endRawUtf16()),
                d.message()));
      return List.copyOf(messages);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (temp != null) delete(temp);
    }
  }

  public List<String> completion(String uri, Position position) {
    Snapshot s = documents.get(uri);
    if (s == null) return List.of();
    offset(s.text(), position);
    if (!indexes.containsKey(s.fingerprint())) analyze(s);
    return Optional.ofNullable(indexes.get(s.fingerprint()))
        .map(SemanticIndex::completion)
        .orElse(List.of());
  }

  public Optional<String> hover(String uri, Position position) {
    Snapshot s = documents.get(uri);
    if (s == null) return Optional.empty();
    if (!indexes.containsKey(s.fingerprint())) analyze(s);
    int at = offset(s.text(), position), a = at, b = at;
    while (a > 0 && Character.isJavaIdentifierPart(s.text().charAt(a - 1))) a--;
    while (b < s.text().length() && Character.isJavaIdentifierPart(s.text().charAt(b))) b++;
    if (a == b) return Optional.empty();
    String word = s.text().substring(a, b);
    return Optional.ofNullable(indexes.get(s.fingerprint()))
        .map(SemanticIndex::hover)
        .map(x -> x.get(word));
  }

  private static SemanticIndex semanticIndex(
      List<CliCompilerFacade.JavaFile> files,
      List<Path> javaSources,
      Optional<WorkspaceModel> workspace) {
    var hover = new TreeMap<String, String>();
    var completion = new TreeSet<String>();
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) return new SemanticIndex(Map.of(), List.of());
    try {
      var sources = new ArrayList<JavaFileObject>();
      for (var file : files) {
        var source =
            new SimpleJavaFileObject(
                URI.create("string:///" + file.relativeUri()), JavaFileObject.Kind.SOURCE) {
              @Override
              public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return file.javaText();
              }
            };
        sources.add(source);
      }
      if (!sources.isEmpty())
        try (var manager =
            compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
          manager.setLocationFromPaths(StandardLocation.CLASS_PATH, workspaceClasspath(workspace));
          manager.setLocationFromPaths(
              StandardLocation.SOURCE_PATH, workspaceRoots(workspace, false));
          manager.getJavaFileObjectsFromPaths(javaSources).forEach(sources::add);
          var options =
              List.of("-proc:none", "--release", Integer.toString(workspaceRelease(workspace)));
          var task = (JavacTask) compiler.getTask(null, manager, null, options, null, sources);
          var units = new ArrayList<CompilationUnitTree>();
          task.parse().forEach(units::add);
          task.analyze();
          var trees = Trees.instance(task);
          indexLocation(manager, task, StandardLocation.CLASS_PATH, "", true, hover, completion);
          indexLocation(
              manager,
              task,
              StandardLocation.PLATFORM_CLASS_PATH,
              "java.lang",
              false,
              hover,
              completion);
          for (CompilationUnitTree unit : units)
            new TreePathScanner<Void, Void>() {
              @Override
              public Void visitClass(ClassTree node, Void unused) {
                add(node.getSimpleName().toString(), "type");
                return super.visitClass(node, unused);
              }

              @Override
              public Void visitMethod(MethodTree node, Void unused) {
                add(node.getName().toString(), "method");
                return super.visitMethod(node, unused);
              }

              @Override
              public Void visitVariable(VariableTree node, Void unused) {
                add(node.getName().toString(), node.getType() + " variable");
                return super.visitVariable(node, unused);
              }

              @Override
              public Void visitIdentifier(IdentifierTree node, Void unused) {
                var element = trees.getElement(getCurrentPath());
                if (element != null && element.asType().getKind() != TypeKind.ERROR) {
                  String name = node.getName().toString();
                  hover.putIfAbsent(name, element.asType() + " `" + element + "`");
                  completion.add(name);
                  element
                      .getEnclosedElements()
                      .forEach(x -> completion.add(x.getSimpleName().toString()));
                }
                return super.visitIdentifier(node, unused);
              }

              private void add(String name, String kind) {
                if (!name.isBlank() && !name.equals("<init>")) {
                  hover.putIfAbsent(name, kind + " `" + name + "`");
                  completion.add(name);
                }
              }
            }.scan(unit, null);
        }
    } catch (IOException ignored) {
    }
    workspace.ifPresent(
        model -> {
          for (var module : model.modules())
            for (var property : module.propertyMetadata()) {
              hover.put(
                  property.propertyName(),
                  property.typeDescriptor()
                      + " property `"
                      + property.ownerBinaryName()
                      + "."
                      + property.propertyName()
                      + "`");
              completion.add(property.propertyName());
            }
        });
    return new SemanticIndex(Collections.unmodifiableMap(hover), List.copyOf(completion));
  }

  private static void indexLocation(
      StandardJavaFileManager manager,
      JavacTask task,
      JavaFileManager.Location location,
      String packageName,
      boolean recursive,
      Map<String, String> hover,
      Set<String> completion)
      throws IOException {
    int count = 0;
    for (JavaFileObject file :
        manager.list(location, packageName, Set.of(JavaFileObject.Kind.CLASS), recursive)) {
      if (++count > 10_000) break;
      String binaryName = manager.inferBinaryName(location, file);
      var type = task.getElements().getTypeElement(binaryName);
      if (type == null) continue;
      String simple = type.getSimpleName().toString();
      completion.add(simple);
      hover.putIfAbsent(simple, type.asType() + " `" + type.getQualifiedName() + "`");
      type.getEnclosedElements().forEach(x -> completion.add(x.getSimpleName().toString()));
    }
  }

  private static int workspaceRelease(Optional<WorkspaceModel> workspace) {
    int release =
        workspace
            .flatMap(x -> x.modules().stream().flatMap(m -> m.sourceSets().stream()).findFirst())
            .map(x -> x.releases().release())
            .orElse(Math.min(25, Runtime.version().feature()));
    int toolchain =
        workspace
            .flatMap(x -> x.modules().stream().findFirst())
            .map(x -> Integer.parseInt(x.toolchain().javaVersion().split("\\.")[0]))
            .orElse(Runtime.version().feature());
    if (release > toolchain || release > Runtime.version().feature())
      throw new IllegalArgumentException("workspace toolchain cannot provide requested release");
    return release;
  }

  private static List<Path> workspaceClasspath(Optional<WorkspaceModel> workspace) {
    return workspace
        .map(
            model ->
                model.modules().stream()
                    .flatMap(x -> x.sourceSets().stream())
                    .flatMap(x -> x.paths().compileClasspath().stream())
                    .flatMap(x -> safeResolve(model, x))
                    .filter(Files::exists)
                    .toList())
        .orElse(List.of());
  }

  private static List<Path> workspaceRoots(Optional<WorkspaceModel> workspace, boolean teyru) {
    return workspace
        .map(
            model ->
                model.modules().stream()
                    .flatMap(x -> x.sourceSets().stream())
                    .flatMap(x -> (teyru ? x.teyruRoots() : x.javaRoots()).stream())
                    .flatMap(x -> safeResolve(model, x))
                    .filter(Files::isDirectory)
                    .toList())
        .orElse(List.of());
  }

  private static List<Path> workspacePaths(
      Optional<WorkspaceModel> workspace, boolean teyru, String suffix) throws IOException {
    var found = new ArrayList<Path>();
    for (Path root : workspaceRoots(workspace, teyru))
      try (var paths = Files.walk(root, 32)) {
        paths
            .filter(Files::isRegularFile)
            .filter(x -> x.getFileName().toString().endsWith(suffix))
            .limit(10_000)
            .forEach(found::add);
      }
    return List.copyOf(found);
  }

  private static Path resolve(WorkspaceModel model, WorkspaceModel.PathRef ref) {
    var uri = ref.resolvedAbsoluteUri().or(() -> ref.fileUri());
    if (uri.isPresent()) return Path.of(URI.create(uri.orElseThrow())).normalize();
    Path base =
        model
            .workspace()
            .resolvedRoot()
            .flatMap(WorkspaceModel.PathRef::resolvedAbsoluteUri)
            .map(x -> Path.of(URI.create(x)))
            .orElseThrow(() -> new IllegalArgumentException("portable workspace is unresolved"));
    Path resolved = base.resolve(ref.logicalPath()).normalize();
    if (!resolved.startsWith(base.normalize())) throw new IllegalArgumentException("root escape");
    return resolved;
  }

  private static Stream<Path> safeResolve(WorkspaceModel model, WorkspaceModel.PathRef ref) {
    try {
      return Stream.of(resolve(model, ref));
    } catch (IllegalArgumentException | FileSystemNotFoundException e) {
      return Stream.empty();
    }
  }

  private static void cancelled() {
    if (Thread.currentThread().isInterrupted())
      throw new CancellationException("analysis cancelled");
  }

  private static String apply(String text, Change change) {
    if (change.range().isEmpty()) return change.text();
    int start = offset(text, change.range().orElseThrow().start());
    int end = offset(text, change.range().orElseThrow().end());
    if (end < start) throw new IllegalArgumentException("invalid edit range");
    return text.substring(0, start) + change.text() + text.substring(end);
  }

  public static int offset(String text, Position p) {
    if (p.line() < 0 || p.character() < 0) throw new IllegalArgumentException("negative position");
    int line = 0, at = 0;
    while (line < p.line() && at < text.length()) {
      char c = text.charAt(at++);
      if (c == '\n') line++;
    }
    if (line != p.line()) throw new IllegalArgumentException("line outside document");
    int end = at;
    while (end < text.length() && text.charAt(end) != '\r' && text.charAt(end) != '\n') end++;
    int result = at + p.character();
    if (result > end
        || result > at
            && result < end
            && Character.isHighSurrogate(text.charAt(result - 1))
            && Character.isLowSurrogate(text.charAt(result)))
      throw new IllegalArgumentException("UTF-16 position outside boundary");
    return result;
  }

  public static Range range(String text, int start, int end) {
    return new Range(position(text, start), position(text, end));
  }

  private static Position position(String text, int offset) {
    int line = 0, lineStart = 0;
    for (int i = 0; i < offset && i < text.length(); i++)
      if (text.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    return new Position(line, Math.max(0, Math.min(offset, text.length()) - lineStart));
  }

  private Snapshot snapshot(String uri, int version, String text) {
    if (version < 0) throw new IllegalArgumentException("negative version");
    String workspaceFingerprint =
        workspace.map(x -> x.fingerprints().model()).orElse("0".repeat(64));
    String overlayFingerprint =
        sha(uri + "\0" + version + "\0" + sha(text) + "\0" + workspaceFingerprint);
    return new Snapshot(uri, version, text, sha(text), workspaceFingerprint, overlayFingerprint);
  }

  private static String normalizeUri(String value) {
    var uri = URI.create(value).normalize();
    if (!uri.isAbsolute() || uri.getQuery() != null || uri.getFragment() != null)
      throw new IllegalArgumentException("invalid document URI");
    return uri.toASCIIString();
  }

  private static String relative(String uri) {
    try {
      String p = URI.create(uri).getPath();
      return Path.of(p).getFileName().toString();
    } catch (RuntimeException e) {
      return "buffer.teyru";
    }
  }

  private static String sha(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void delete(Path root) {
    try (var paths = Files.walk(root)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
  }

  @Override
  public void close() {
    documents.clear();
  }
}
